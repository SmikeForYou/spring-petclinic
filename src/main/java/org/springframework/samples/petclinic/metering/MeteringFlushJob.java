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
package org.springframework.samples.petclinic.metering;

import java.time.YearMonth;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled flush that reads the deduplication store once per closed calendar month
 * and submits exactly one aggregated event to Unified Metering.
 *
 * <p>
 * The default cron ({@code 0 5 0 1 * *}) fires five minutes after midnight on the
 * first of each month; the window is deliberately the <em>previous</em>
 * {@link YearMonth} so the current month is never half-reported. A month with no
 * recorded visitors still emits a single event with value 0 (scenario s7). The
 * sender uses a deterministic event id per month, so an at-least-once delivery retry
 * never double-counts.
 */
@Component
public class MeteringFlushJob {

	private static final Logger logger = LoggerFactory.getLogger(MeteringFlushJob.class);

	private final MeteringProperties properties;

	private final MeteredVisitorRepository repository;

	private final MeteringSender sender;

	public MeteringFlushJob(MeteringProperties properties, MeteredVisitorRepository repository,
			MeteringSender sender) {
		this.properties = properties;
		this.repository = repository;
		this.sender = sender;
	}

	/**
	 * Flush the previous calendar month. Guarded by the production gate.
	 */
	@Scheduled(cron = "${metering.flush-cron:0 5 0 1 * *}", zone = "${metering.timezone:UTC}")
	public void flushPreviousMonth() {
		if (!this.properties.isProduction()) {
			logger.debug("metering: production gate disabled — skipping monthly flush");
			return;
		}
		YearMonth previous = YearMonth.now(ZoneId.of(this.properties.getTimezone())).minusMonths(1);
		flushMonth(previous);
	}

	/**
	 * Flush the given calendar month; exposed for operational/manual triggers.
	 */
	public void flushMonth(YearMonth month) {
		String key = month.toString();
		long count = 0L;
		try {
			count = this.repository.countByMonth(key);
		}
		catch (RuntimeException ex) {
			logger.warn("metering: failed to read monthly count for {} ({}): {}", key, ex.getClass().getSimpleName(),
					ex.getMessage());
		}
		logger.info("metering: flushing month={} distinctVisitors={}", key, count);
		this.sender.send(key, count);
	}

}
