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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.metering.MeteredVisitor.MeteredVisitorId;

/**
 * Spring Data repository for {@link MeteredVisitor}.
 *
 * <p>
 * The primary key is the composite (month, visitorId) pair, so
 * {@link #countByMonth(String)} returns the distinct-visitor count for that calendar
 * month directly. Discovered by the same Spring Data auto-configuration that already
 * activates {@code OwnerRepository}.
 */
public interface MeteredVisitorRepository extends JpaRepository<MeteredVisitor, MeteredVisitorId> {

	/**
	 * Distinct visitor count for the given calendar month ({@code YYYY-MM}).
	 * @param month the month in ISO {@code YYYY-MM} form
	 * @return the number of distinct visitor identities recorded in that month; 0 when
	 * no row has been recorded (scenario s7)
	 */
	@Query("SELECT COUNT(v) FROM MeteredVisitor v WHERE v.id.month = :month")
	long countByMonth(@Param("month") String month);

}
