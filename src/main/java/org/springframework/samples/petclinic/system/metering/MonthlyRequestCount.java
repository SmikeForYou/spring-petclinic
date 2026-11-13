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

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Durable aggregation state for the HS_PET_CLINIC_MONTLY_USER_COUNT measure.
 *
 * <p>
 * One row per calendar month ({@code year_bucket}, {@code month_bucket}); every inbound
 * HTTP request atomically increments {@link #count} in the row for its event-time month.
 * Rows survive restarts so a month's total covers the full calendar period, not process
 * uptime.
 * </p>
 */
@Entity
@Table(name = "metering_monthly_request_count",
		uniqueConstraints = @UniqueConstraint(name = "uk_metering_month",
				columnNames = { "year_bucket", "month_bucket" }))
public class MonthlyRequestCount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "year_bucket", nullable = false)
	private int yearBucket;

	@Column(name = "month_bucket", nullable = false)
	private int monthBucket;

	@Column(name = "count", nullable = false)
	private long count;

	@Column(name = "last_flushed_at")
	private Instant lastFlushedAt;

	public MonthlyRequestCount() {
	}

	public MonthlyRequestCount(int yearBucket, int monthBucket, long count) {
		this.yearBucket = yearBucket;
		this.monthBucket = monthBucket;
		this.count = count;
	}

	public Long getId() {
		return this.id;
	}

	public int getYearBucket() {
		return this.yearBucket;
	}

	public void setYearBucket(int yearBucket) {
		this.yearBucket = yearBucket;
	}

	public int getMonthBucket() {
		return this.monthBucket;
	}

	public void setMonthBucket(int monthBucket) {
		this.monthBucket = monthBucket;
	}

	public long getCount() {
		return this.count;
	}

	public void setCount(long count) {
		this.count = count;
	}

	public Instant getLastFlushedAt() {
		return this.lastFlushedAt;
	}

	public void setLastFlushedAt(Instant lastFlushedAt) {
		this.lastFlushedAt = lastFlushedAt;
	}

}
