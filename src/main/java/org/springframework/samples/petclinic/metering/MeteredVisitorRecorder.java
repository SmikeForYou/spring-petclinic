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

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic, idempotent writer into the {@code metered_visitor} deduplication table.
 *
 * <p>
 * Uses a profile-dispatched native upsert so that concurrent requests from the same
 * visitor never fail: H2 uses {@code MERGE}, MySQL uses {@code INSERT IGNORE},
 * Postgres uses {@code ON CONFLICT DO NOTHING}. Each call runs in a separate
 * transaction ({@link Propagation#REQUIRES_NEW}) so a failure here is isolated from
 * the business request's transaction. Any remaining exception is swallowed with a
 * WARN log — metering must never fail a business request.
 */
@Component
public class MeteredVisitorRecorder {

	private static final Logger logger = LoggerFactory.getLogger(MeteredVisitorRecorder.class);

	private static final List<String> MYSQL_PROFILES = Arrays.asList("mysql");

	private static final List<String> POSTGRES_PROFILES = Arrays.asList("postgres", "postgresql");

	@PersistenceContext
	private EntityManager entityManager;

	private final Environment environment;

	public MeteredVisitorRecorder(Environment environment) {
		this.environment = environment;
	}

	/**
	 * Idempotently record that {@code visitorId} was active during {@code month}.
	 * @param month the calendar month in {@code YYYY-MM} form
	 * @param visitorId the stable pseudonymous visitor identity
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordDistinct(String month, String visitorId) {
		try {
			String sql = chooseSql();
			this.entityManager.createNativeQuery(sql)
				.setParameter("m", month)
				.setParameter("v", visitorId)
				.executeUpdate();
		}
		catch (RuntimeException ex) {
			logger.warn("metering: failed to record visitor for month={} ({}): {}", month,
					ex.getClass().getSimpleName(), ex.getMessage());
		}
	}

	private String chooseSql() {
		String[] active = this.environment.getActiveProfiles();
		for (String profile : active) {
			if (MYSQL_PROFILES.contains(profile)) {
				return "INSERT IGNORE INTO metered_visitor (month, visitor_id) VALUES (:m, :v)";
			}
			if (POSTGRES_PROFILES.contains(profile)) {
				return "INSERT INTO metered_visitor (month, visitor_id) VALUES (:m, :v) ON CONFLICT DO NOTHING";
			}
		}
		return "MERGE INTO metered_visitor (month, visitor_id) KEY(month, visitor_id) VALUES (:m, :v)";
	}

}
