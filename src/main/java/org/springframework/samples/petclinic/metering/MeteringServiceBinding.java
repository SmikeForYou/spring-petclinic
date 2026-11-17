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

import java.util.Iterator;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Reads the SAP BTP {@code metering-service} binding from the {@code VCAP_SERVICES}
 * environment variable at construction time.
 *
 * <p>
 * On a developer workstation or in any environment without the binding, the
 * credentials are simply absent: the sender then logs a single warning at startup and
 * skips submission. This is explicitly supported by the measurement contract.
 */
@Component
public class MeteringServiceBinding {

	private static final Logger logger = LoggerFactory.getLogger(MeteringServiceBinding.class);

	private static final String SERVICE_LABEL = "metering-service";

	private final Credentials credentials;

	public MeteringServiceBinding() {
		this(System.getenv("VCAP_SERVICES"));
	}

	MeteringServiceBinding(String vcapServices) {
		this.credentials = parse(vcapServices).orElse(null);
		if (this.credentials == null) {
			logger.warn("metering-service binding absent; Unified Metering submission disabled");
		}
	}

	public Optional<Credentials> getCredentials() {
		return Optional.ofNullable(this.credentials);
	}

	private static Optional<Credentials> parse(String vcapServices) {
		if (vcapServices == null || vcapServices.isBlank()) {
			return Optional.empty();
		}
		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(vcapServices);
			JsonNode services = root.get(SERVICE_LABEL);
			if (services == null || !services.isArray() || services.isEmpty()) {
				return Optional.empty();
			}
			JsonNode binding = services.get(0);
			JsonNode creds = binding.get("credentials");
			if (creds == null) {
				return Optional.empty();
			}
			String usageIngestion = findIngestionEndpoint(creds);
			String certificate = textOrNull(creds.get("certificate"));
			String key = textOrNull(creds.get("key"));
			if (usageIngestion == null || certificate == null || key == null) {
				return Optional.empty();
			}
			return Optional.of(new Credentials(usageIngestion, unescape(certificate), unescape(key)));
		}
		catch (RuntimeException | java.io.IOException ex) {
			logger.warn("metering: cannot parse VCAP_SERVICES ({}): {}", ex.getClass().getSimpleName(),
					ex.getMessage());
			return Optional.empty();
		}
	}

	private static String findIngestionEndpoint(JsonNode creds) {
		JsonNode endpoints = creds.get("endpoints");
		if (endpoints != null) {
			JsonNode ingestion = endpoints.get("usageIngestion");
			if (ingestion != null && ingestion.isTextual()) {
				return ingestion.asText();
			}
			// Fallback: scan for any child with a usageIngestion property.
			Iterator<JsonNode> children = endpoints.elements();
			while (children.hasNext()) {
				JsonNode child = children.next();
				JsonNode nested = child.get("usageIngestion");
				if (nested != null && nested.isTextual()) {
					return nested.asText();
				}
			}
		}
		return textOrNull(creds.get("usageIngestion"));
	}

	private static String textOrNull(JsonNode node) {
		return (node != null && node.isTextual() && !node.asText().isBlank()) ? node.asText() : null;
	}

	private static String unescape(String pem) {
		return pem.replace("\\n", "\n").replace("\\r", "");
	}

	/** Parsed PEM credentials and the ingestion URL. */
	public static final class Credentials {

		private final String usageIngestion;

		private final String certificate;

		private final String key;

		public Credentials(String usageIngestion, String certificate, String key) {
			this.usageIngestion = usageIngestion;
			this.certificate = certificate;
			this.key = key;
		}

		public String getUsageIngestion() {
			return this.usageIngestion;
		}

		public String getCertificate() {
			return this.certificate;
		}

		public String getKey() {
			return this.key;
		}

	}

}
