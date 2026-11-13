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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the SAP Unified Metering integration.
 *
 * <p>
 * The {@code metering-service} binding (VCAP_SERVICES) is read at runtime; when it is
 * absent (local development, tests) submission is silently disabled and the empty
 * defaults below are safe. Only non-personal consumer/product identification is kept
 * here — never user data.
 * </p>
 */
@ConfigurationProperties(prefix = "metering")
public class MeteringProperties {

	/**
	 * Time zone used to assign each inbound request to a calendar month bucket and to
	 * run the scheduled flush. Defaults to UTC.
	 */
	private String timeZone = "UTC";

	/**
	 * Cron expression for the daily monthly-flush job (zone = {@link #timeZone}).
	 */
	private String flushCron = "0 5 0 * * *";

	/**
	 * Service binding name looked up in VCAP_SERVICES.
	 */
	private String bindingName = "metering-service";

	private final Product product = new Product();

	private final Consumer consumer = new Consumer();

	public String getTimeZone() {
		return this.timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public String getFlushCron() {
		return this.flushCron;
	}

	public void setFlushCron(String flushCron) {
		this.flushCron = flushCron;
	}

	public String getBindingName() {
		return this.bindingName;
	}

	public void setBindingName(String bindingName) {
		this.bindingName = bindingName;
	}

	public Product getProduct() {
		return this.product;
	}

	public Consumer getConsumer() {
		return this.consumer;
	}

	/**
	 * Product identification sent in every event.
	 */
	public static class Product {

		private final Service service = new Service();

		public Service getService() {
			return this.service;
		}

		public static class Service {

			private String id = "petclinic";

			public String getId() {
				return this.id;
			}

			public void setId(String id) {
				this.id = id;
			}

		}

	}

	/**
	 * Non-personal consumer identification sent in every event.
	 */
	public static class Consumer {

		private String region = "eu10";

		private final Btp btp = new Btp();

		public String getRegion() {
			return this.region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

		public Btp getBtp() {
			return this.btp;
		}

		public static class Btp {

			private String environment = "cloudfoundry";

			private String subAccount = "";

			public String getEnvironment() {
				return this.environment;
			}

			public void setEnvironment(String environment) {
				this.environment = environment;
			}

			public String getSubAccount() {
				return this.subAccount;
			}

			public void setSubAccount(String subAccount) {
				this.subAccount = subAccount;
			}

		}

	}

}
