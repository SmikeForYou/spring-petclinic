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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for SAP Unified Metering collection and delivery.
 *
 * <p>
 * All values default to a non-production, no-op configuration so that collection is
 * disabled unless the deployment explicitly turns it on by setting
 * {@code metering.production=true} and supplying the consumer identifiers required by
 * the Unified Metering datastream.
 */
@ConfigurationProperties(prefix = "metering")
public class MeteringProperties {

	/** Production gate: when {@code false} the interceptor and flush job skip. */
	private boolean production = false;

	/** BTP region of the consumer tenant (e.g. {@code eu10}). */
	private String region = "";

	/** BTP environment of the consumer tenant (e.g. {@code cloudfoundry}). */
	private String environment = "";

	/** BTP sub-account id of the consumer; either this or globalAccount must be set. */
	private String subAccount = "";

	/** BTP global account id of the consumer; alternative to subAccount. */
	private String globalAccount = "";

	/** Product/service id as registered with Unified Metering. */
	private String serviceId = "pet-clinic";

	/** Optional consumer identifier (used for diagnostic logging only). */
	private String consumerId = "";

	/** Measure id submitted verbatim as {@code measure.id} in every event. */
	private String measureId = "HS_PET_CLINIC_MONTLY_USER_COUNT";

	/** Cron for the monthly flush job; default = 5 minutes past midnight on the 1st. */
	private String flushCron = "0 5 0 1 * *";

	/** Time zone used for calendar-month windowing and the flush scheduler. */
	private String timezone = "UTC";

	/** Name of the pseudonymous visitor cookie. */
	private String cookieName = "PC_VID";

	/** Max age in days of the visitor cookie (> one calendar month). */
	private int cookieMaxAgeDays = 400;

	public boolean isProduction() {
		return this.production;
	}

	public void setProduction(boolean production) {
		this.production = production;
	}

	public String getRegion() {
		return this.region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

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

	public String getGlobalAccount() {
		return this.globalAccount;
	}

	public void setGlobalAccount(String globalAccount) {
		this.globalAccount = globalAccount;
	}

	public String getServiceId() {
		return this.serviceId;
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	public String getConsumerId() {
		return this.consumerId;
	}

	public void setConsumerId(String consumerId) {
		this.consumerId = consumerId;
	}

	public String getMeasureId() {
		return this.measureId;
	}

	public void setMeasureId(String measureId) {
		this.measureId = measureId;
	}

	public String getFlushCron() {
		return this.flushCron;
	}

	public void setFlushCron(String flushCron) {
		this.flushCron = flushCron;
	}

	public String getTimezone() {
		return this.timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public String getCookieName() {
		return this.cookieName;
	}

	public void setCookieName(String cookieName) {
		this.cookieName = cookieName;
	}

	public int getCookieMaxAgeDays() {
		return this.cookieMaxAgeDays;
	}

	public void setCookieMaxAgeDays(int cookieMaxAgeDays) {
		this.cookieMaxAgeDays = cookieMaxAgeDays;
	}

}
