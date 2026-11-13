/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.system.metering;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled flush job for the HS_PET_CLINIC_MONTLY_USER_COUNT measure.
 *
 * <p>
 * Runs daily according to {@code metering.flush.cron} in the configured time zone.
 * Submits one event per calendar-month bucket present in the store (plus a zero-value
 * event for the just-closed month when no inbound requests were received, satisfying
 * acceptance scenario s7). Event ids are derived deterministically from the measure key
 * and the (year, month) so retries never double count on the ingestion side.
 * </p>
 */
@Component
public class MonthlyFlushJob {

	static final String MEASURE_KEY = "HS_PET_CLINIC_MONTLY_USER_COUNT";

	private static final Logger logger = LoggerFactory.getLogger(MonthlyFlushJob.class);

	private final MonthlyRequestCountRepository repository;

	private final MonthlyRequestCounter counter;

	private final UnifiedMeteringSender sender;

	private final MeteringProperties properties;

	public MonthlyFlushJob(MonthlyRequestCountRepository repository, MonthlyRequestCounter counter,
			UnifiedMeteringSender sender, MeteringProperties properties) {
		this.repository = repository;
		this.counter = counter;
		this.sender = sender;
		this.properties = properties;
	}

	@Scheduled(cron = "${metering.flush.cron:0 5 0 * * *}", zone = "${metering.time-zone:UTC}")
	public void flush() {
		try {
			ZoneId zone = zoneId();
			ZonedDateTime now = ZonedDateTime.now(zone);
			int currentYear = now.getYear();
			int currentMonth = now.getMonthValue();
			ensurePreviousMonthRow(currentYear, currentMonth);
			List<MonthlyRequestCount> closed = this.repository.findClosedBefore(currentYear, currentMonth);
			for (MonthlyRequestCount row : closed) {
				submit(row, zone, true);
				row.setLastFlushedAt(Instant.now());
				try {
					this.repository.save(row);
				}
				catch (RuntimeException ex) {
					logger.warn("Failed to persist last flush timestamp for {}-{}: {}", row.getYearBucket(),
							row.getMonthBucket(), ex.toString());
				}
			}
			this.repository.findByYearBucketAndMonthBucket(currentYear, currentMonth)
				.ifPresent((row) -> submit(row, zone, false));
		}
		catch (RuntimeException ex) {
			logger.warn("Monthly metering flush failed: {}", ex.toString());
		}
	}

	private void ensurePreviousMonthRow(int currentYear, int currentMonth) {
		int prevYear = (currentMonth == 1) ? currentYear - 1 : currentYear;
		int prevMonth = (currentMonth == 1) ? 12 : currentMonth - 1;
		this.counter.ensureRow(prevYear, prevMonth);
	}

	private void submit(MonthlyRequestCount row, ZoneId zone, boolean closed) {
		UUID id = UUID
			.nameUUIDFromBytes((MEASURE_KEY + "|" + row.getYearBucket() + "-" + row.getMonthBucket())
				.getBytes(StandardCharsets.UTF_8));
		Instant timestamp = closed ? lastInstantOfMonth(row.getYearBucket(), row.getMonthBucket(), zone)
				: Instant.now();
		UnifiedMeteringSender.UsageEvent event = new UnifiedMeteringSender.UsageEvent(id, timestamp, MEASURE_KEY,
				row.getCount());
		this.sender.submit(event);
	}

	private static Instant lastInstantOfMonth(int year, int month, ZoneId zone) {
		LocalDate firstOfMonth = LocalDate.of(year, Month.of(month), 1);
		LocalDate lastOfMonth = firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth());
		LocalDateTime endOfDay = lastOfMonth.atTime(23, 59, 59);
		return endOfDay.atZone(zone).toInstant();
	}

	private ZoneId zoneId() {
		try {
			return ZoneId.of(this.properties.getTimeZone());
		}
		catch (RuntimeException ex) {
			return ZoneId.of("UTC");
		}
	}

}
