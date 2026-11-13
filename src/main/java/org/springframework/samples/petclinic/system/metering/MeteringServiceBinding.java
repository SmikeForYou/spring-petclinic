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

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Parses the {@code metering-service} entry from the {@code VCAP_SERVICES} environment
 * variable so the sender can submit usage events with mutual TLS.
 *
 * <p>
 * Reads the binding at construction time; when the variable or entry is missing the
 * binding is marked absent and the sender logs a single startup warning and skips
 * submission. No personal data is stored or logged — only the usage ingestion URL and
 * whether a binding is present.
 * </p>
 */
@Component
public class MeteringServiceBinding {

	private static final Logger logger = LoggerFactory.getLogger(MeteringServiceBinding.class);

	private final boolean present;

	private final String certificatePem;

	private final String keyPem;

	private final String usageIngestionUrl;

	public MeteringServiceBinding(MeteringProperties properties) {
		this(properties, System.getenv("VCAP_SERVICES"));
	}

	MeteringServiceBinding(MeteringProperties properties, String vcapServices) {
		String cert = null;
		String key = null;
		String url = null;
		boolean found = false;
		if (vcapServices != null && !vcapServices.isBlank()) {
			try {
				JsonNode root = new ObjectMapper().readTree(vcapServices);
				Iterator<Map.Entry<String, JsonNode>> labels = root.fields();
				while (labels.hasNext() && !found) {
					JsonNode instances = labels.next().getValue();
					if (instances == null || !instances.isArray()) {
						continue;
					}
					for (JsonNode instance : instances) {
						if (matches(instance, properties.getBindingName())) {
							JsonNode credentials = instance.path("credentials");
							cert = unescape(credentials.path("certificate").asText(null));
							key = unescape(credentials.path("key").asText(null));
							JsonNode endpoints = credentials.path("endpoints");
							url = endpoints.path("usageIngestion").asText(null);
							found = cert != null && key != null && url != null;
							break;
						}
					}
				}
			}
			catch (RuntimeException | java.io.IOException ex) {
				logger.warn("Failed to parse VCAP_SERVICES for metering binding '{}': {}", properties.getBindingName(),
						ex.toString());
				found = false;
			}
		}
		this.present = found;
		this.certificatePem = cert;
		this.keyPem = key;
		this.usageIngestionUrl = url;
		if (this.present) {
			logger.info("SAP Unified Metering binding '{}' detected; usage ingestion URL configured.",
					properties.getBindingName());
		}
		else {
			logger.warn("metering-service binding '{}' not present; usage submission disabled.",
					properties.getBindingName());
		}
	}

	private static boolean matches(JsonNode instance, String bindingName) {
		if (instance == null || !instance.isObject()) {
			return false;
		}
		if (bindingName.equals(instance.path("name").asText(null))
				|| bindingName.equals(instance.path("label").asText(null))) {
			return true;
		}
		JsonNode tags = instance.path("tags");
		if (tags.isArray()) {
			for (JsonNode tag : tags) {
				if (bindingName.equals(tag.asText(null))) {
					return true;
				}
			}
		}
		return false;
	}

	private static String unescape(String pem) {
		if (pem == null) {
			return null;
		}
		return pem.replace("\\n", "\n");
	}

	public boolean isPresent() {
		return this.present;
	}

	public String getCertificatePem() {
		return this.certificatePem;
	}

	public String getKeyPem() {
		return this.keyPem;
	}

	public String getUsageIngestionUrl() {
		return this.usageIngestionUrl;
	}

}
