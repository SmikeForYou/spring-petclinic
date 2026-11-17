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

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Durable deduplication row: one (month, visitorId) pair per distinct monthly visitor.
 *
 * <p>
 * The composite primary key enforces that the same visitor recorded multiple times in
 * the same calendar month collapses to a single row; counting rows per month therefore
 * yields the distinct-user count required by the Unified Metering measure
 * {@code HS_PET_CLINIC_MONTLY_USER_COUNT}.
 */
@Entity
@Table(name = "metered_visitor")
public class MeteredVisitor {

	@EmbeddedId
	private MeteredVisitorId id;

	public MeteredVisitor() {
	}

	public MeteredVisitor(String month, String visitorId) {
		this.id = new MeteredVisitorId(month, visitorId);
	}

	public MeteredVisitorId getId() {
		return this.id;
	}

	public void setId(MeteredVisitorId id) {
		this.id = id;
	}

	/**
	 * Composite primary key: calendar month ({@code YYYY-MM}) and the pseudonymous
	 * visitor identity.
	 */
	@Embeddable
	public static class MeteredVisitorId implements Serializable {

		private static final long serialVersionUID = 1L;

		@Column(name = "month", length = 7, nullable = false)
		private String month;

		@Column(name = "visitor_id", length = 64, nullable = false)
		private String visitorId;

		public MeteredVisitorId() {
		}

		public MeteredVisitorId(String month, String visitorId) {
			this.month = month;
			this.visitorId = visitorId;
		}

		public String getMonth() {
			return this.month;
		}

		public void setMonth(String month) {
			this.month = month;
		}

		public String getVisitorId() {
			return this.visitorId;
		}

		public void setVisitorId(String visitorId) {
			this.visitorId = visitorId;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof MeteredVisitorId that)) {
				return false;
			}
			return Objects.equals(this.month, that.month) && Objects.equals(this.visitorId, that.visitorId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.month, this.visitorId);
		}

	}

}
