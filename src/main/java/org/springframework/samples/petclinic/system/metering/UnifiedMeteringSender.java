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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends usage events to SAP Unified Metering via the BTP datastream REST endpoint with
 * mutual TLS derived from the {@link MeteringServiceBinding}.
 *
 * <p>
 * Fire-and-forget: a failed or unconfigured submission is logged with the HTTP status
 * code (when available) and never rethrown, so scheduled flushes never fail. When the
 * binding is absent at startup, a single warning is emitted and {@link #submit(UsageEvent)}
 * becomes a no-op.
 * </p>
 */
@Component
public class UnifiedMeteringSender {

	private static final Logger logger = LoggerFactory.getLogger(UnifiedMeteringSender.class);

	private static final String EVENT_BATCH_PATH = "/namespaces/metering/datastreams/eventBatch";

	private final MeteringProperties properties;

	private final boolean enabled;

	private final String ingestionUrl;

	private final RestClient restClient;

	public UnifiedMeteringSender(MeteringServiceBinding binding, MeteringProperties properties) {
		this.properties = properties;
		if (!binding.isPresent()) {
			this.enabled = false;
			this.ingestionUrl = null;
			this.restClient = null;
			return;
		}
		RestClient client = null;
		String url = null;
		boolean built = false;
		try {
			SSLContext sslContext = buildSslContext(binding.getCertificatePem(), binding.getKeyPem());
			java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder().sslContext(sslContext).build();
			client = RestClient.builder()
				.requestFactory(new JdkClientHttpRequestFactory(httpClient))
				.baseUrl(binding.getUsageIngestionUrl())
				.build();
			url = binding.getUsageIngestionUrl();
			built = true;
		}
		catch (RuntimeException ex) {
			logger.warn("Failed to initialize SAP Unified Metering TLS client; submission disabled: {}", ex.toString());
		}
		catch (Exception ex) {
			logger.warn("Failed to initialize SAP Unified Metering TLS client; submission disabled: {}", ex.toString());
		}
		this.enabled = built;
		this.ingestionUrl = url;
		this.restClient = client;
	}

	/**
	 * Submit one usage event as a single-element JSON array. Never throws.
	 */
	public void submit(UsageEvent event) {
		if (!this.enabled || this.restClient == null) {
			return;
		}
		try {
			List<Map<String, Object>> payload = List.of(toPayload(event));
			this.restClient.post()
				.uri(EVENT_BATCH_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientResponseException ex) {
			HttpStatusCode status = ex.getStatusCode();
			logger.warn("SAP Unified Metering submission failed with HTTP {} for measure {}", status.value(),
					event.measureId());
		}
		catch (Throwable ex) {
			logger.warn("SAP Unified Metering submission failed for measure {}: {}", event.measureId(), ex.toString());
		}
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	String getIngestionUrl() {
		return this.ingestionUrl;
	}

	private Map<String, Object> toPayload(UsageEvent event) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", event.id().toString());
		body.put("timestamp", event.timestamp().toString());
		Map<String, Object> btp = new LinkedHashMap<>();
		btp.put("environment", this.properties.getConsumer().getBtp().getEnvironment());
		btp.put("subAccount", this.properties.getConsumer().getBtp().getSubAccount());
		Map<String, Object> consumer = new LinkedHashMap<>();
		consumer.put("region", this.properties.getConsumer().getRegion());
		consumer.put("btp", btp);
		body.put("consumer", consumer);
		Map<String, Object> service = new LinkedHashMap<>();
		service.put("id", this.properties.getProduct().getService().getId());
		Map<String, Object> product = new LinkedHashMap<>();
		product.put("service", service);
		body.put("product", product);
		Map<String, Object> measure = new LinkedHashMap<>();
		measure.put("id", event.measureId());
		measure.put("value", event.value());
		body.put("measure", measure);
		return body;
	}

	private static SSLContext buildSslContext(String certificatePem, String keyPem) throws Exception {
		CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
		List<Certificate> chain = new ArrayList<>();
		try (ByteArrayInputStream in = new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.UTF_8))) {
			for (Certificate certificate : certificateFactory.generateCertificates(in)) {
				chain.add(certificate);
			}
		}
		byte[] pkcs8 = decodePrivateKey(keyPem);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		java.security.PrivateKey privateKey;
		try {
			privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
		}
		catch (java.security.spec.InvalidKeySpecException ex) {
			privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
		}
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(null, new char[0]);
		keyStore.setKeyEntry("metering", privateKey, new char[0], chain.toArray(new Certificate[0]));
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, new char[0]);
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
		return sslContext;
	}

	private static byte[] decodePrivateKey(String keyPem) {
		String cleaned = keyPem.replaceAll("-----BEGIN [^-]+-----", "")
			.replaceAll("-----END [^-]+-----", "")
			.replaceAll("\\s+", "");
		return Base64.getDecoder().decode(cleaned);
	}

	/**
	 * Minimal usage-event representation carried from the collector/flush path to the
	 * sender. No personal data is included.
	 */
	public record UsageEvent(UUID id, Instant timestamp, String measureId, long value) {
	}

}
