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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collector for the HS_PET_CLINIC_MONTLY_USER_COUNT measure.
 *
 * <p>
 * {@link #increment()} determines the current (year, month) bucket from the configured
 * time zone and performs an atomic, dialect-specific upsert-and-increment on the
 * {@code metering_monthly_request_count} table so concurrent inbound requests never
 * lose counts and never fail the business request.
 * </p>
 */
@Component
public class MonthlyRequestCounter {

	private static final Logger logger = LoggerFactory.getLogger(MonthlyRequestCounter.class);

	private final JdbcTemplate jdbcTemplate;

	private final MeteringProperties properties;

	private final Clock clock;

	private final Dialect dialect;

	public MonthlyRequestCounter(DataSource dataSource, MeteringProperties properties) {
		this(new JdbcTemplate(dataSource), properties, Clock.systemUTC(), detectDialect(dataSource));
	}

	MonthlyRequestCounter(JdbcTemplate jdbcTemplate, MeteringProperties properties, Clock clock, Dialect dialect) {
		this.jdbcTemplate = jdbcTemplate;
		this.properties = properties;
		this.clock = clock;
		this.dialect = dialect;
	}

	/**
	 * Atomically record one inbound HTTP request in the current calendar-month bucket.
	 * Runs in its own transaction so a failing business transaction never reverts the
	 * count and a failing count never breaks the business request.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void increment() {
		ZonedDateTime now = ZonedDateTime.now(this.clock.withZone(zoneId()));
		int year = now.getYear();
		int month = now.getMonthValue();
		try {
			int rows = this.jdbcTemplate.update(this.dialect.upsertSql(), year, month);
			if (rows == 0) {
				logger.debug("Metering upsert reported 0 affected rows for {}-{}", year, month);
			}
		}
		catch (RuntimeException ex) {
			logger.warn("Failed to record inbound HTTP request for metering ({}-{}): {}", year, month, ex.toString());
		}
	}

	/**
	 * Ensure a row exists for the given month so a zero-count month can still be
	 * reported downstream. Idempotent under the unique constraint.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void ensureRow(int yearBucket, int monthBucket) {
		try {
			this.jdbcTemplate.update(this.dialect.ensureZeroSql(), yearBucket, monthBucket);
		}
		catch (RuntimeException ex) {
			logger.warn("Failed to ensure metering row for {}-{}: {}", yearBucket, monthBucket, ex.toString());
		}
	}

	private ZoneId zoneId() {
		try {
			return ZoneId.of(this.properties.getTimeZone());
		}
		catch (RuntimeException ex) {
			return ZoneId.of("UTC");
		}
	}

	static Dialect detectDialect(DataSource dataSource) {
		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData metaData = connection.getMetaData();
			String product = (metaData.getDatabaseProductName() != null)
					? metaData.getDatabaseProductName().toLowerCase(Locale.ROOT) : "";
			if (product.contains("mysql") || product.contains("maria")) {
				return Dialect.MYSQL;
			}
			if (product.contains("postgres")) {
				return Dialect.POSTGRES;
			}
			return Dialect.H2;
		}
		catch (SQLException ex) {
			LoggerFactory.getLogger(MonthlyRequestCounter.class)
				.warn("Unable to detect database dialect for metering; defaulting to H2: {}", ex.toString());
			return Dialect.H2;
		}
	}

	/**
	 * Dialect-specific atomic SQL for the metering counter.
	 */
	public enum Dialect {

		H2("MERGE INTO metering_monthly_request_count AS t"
				+ " USING (VALUES (CAST(? AS INT), CAST(? AS INT))) AS s(year_bucket, month_bucket)"
				+ " ON t.year_bucket = s.year_bucket AND t.month_bucket = s.month_bucket"
				+ " WHEN MATCHED THEN UPDATE SET count = t.count + 1"
				+ " WHEN NOT MATCHED THEN INSERT (year_bucket, month_bucket, count) VALUES (s.year_bucket, s.month_bucket, 1)",
				"MERGE INTO metering_monthly_request_count AS t"
						+ " USING (VALUES (CAST(? AS INT), CAST(? AS INT))) AS s(year_bucket, month_bucket)"
						+ " ON t.year_bucket = s.year_bucket AND t.month_bucket = s.month_bucket"
						+ " WHEN NOT MATCHED THEN INSERT (year_bucket, month_bucket, count) VALUES (s.year_bucket, s.month_bucket, 0)"),

		MYSQL("INSERT INTO metering_monthly_request_count (year_bucket, month_bucket, count) VALUES (?, ?, 1)"
				+ " ON DUPLICATE KEY UPDATE count = count + 1",
				"INSERT IGNORE INTO metering_monthly_request_count (year_bucket, month_bucket, count) VALUES (?, ?, 0)"),

		POSTGRES("INSERT INTO metering_monthly_request_count (year_bucket, month_bucket, count) VALUES (?, ?, 1)"
				+ " ON CONFLICT (year_bucket, month_bucket)"
				+ " DO UPDATE SET count = metering_monthly_request_count.count + 1",
				"INSERT INTO metering_monthly_request_count (year_bucket, month_bucket, count) VALUES (?, ?, 0)"
						+ " ON CONFLICT (year_bucket, month_bucket) DO NOTHING");

		private final String upsertSql;

		private final String ensureZeroSql;

		Dialect(String upsertSql, String ensureZeroSql) {
			this.upsertSql = upsertSql;
			this.ensureZeroSql = ensureZeroSql;
		}

		public String upsertSql() {
			return this.upsertSql;
		}

		public String ensureZeroSql() {
			return this.ensureZeroSql;
		}

	}

}
