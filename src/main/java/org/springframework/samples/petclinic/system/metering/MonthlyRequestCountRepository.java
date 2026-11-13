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

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data JPA repository for {@link MonthlyRequestCount}. The atomic increment
 * (insert-or-update-by-unique-key) is dispatched from {@link MonthlyRequestCounter} via
 * {@code JdbcTemplate} so it can pick dialect-specific SQL for H2/MySQL/PostgreSQL.
 */
public interface MonthlyRequestCountRepository extends JpaRepository<MonthlyRequestCount, Long> {

	Optional<MonthlyRequestCount> findByYearBucketAndMonthBucket(int yearBucket, int monthBucket);

	/**
	 * Returns every row whose bucket is strictly before the given (year, month) — i.e. a
	 * closed calendar month.
	 */
	@Query("""
			SELECT m FROM MonthlyRequestCount m
			WHERE m.yearBucket < :yearBucket
			   OR (m.yearBucket = :yearBucket AND m.monthBucket < :monthBucket)
			""")
	List<MonthlyRequestCount> findClosedBefore(int yearBucket, int monthBucket);

}
