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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Terminal sink that submits one Unified Metering event per calendar month over mTLS.
 *
 * <p>
 * The sender is strictly fire-and-forget: submission runs on a bounded single-thread
 * executor so it never blocks a business request; any transport failure is logged
 * with the HTTP status code and swallowed; and when the BTP binding is absent the
 * sender logs once and skips submission entirely.
 *
 * <p>
 * The event id is deterministic ({@code UUID.nameUUIDFromBytes(measure|month)}) so a
 * retry of the same month never produces a duplicate count at Unified Metering.
 */
@Component
public class MeteringSender {

	private static final Logger logger = LoggerFactory.getLogger(MeteringSender.class);

	private static final String INGESTION_PATH = "/namespaces/metering/datastreams/eventBatch";

	private final MeteringProperties properties;

	private final MeteringServiceBinding binding;

	private final ExecutorService executor;

	private volatile RestClient restClient;

	private volatile boolean clientInitialised;

	public MeteringSender(MeteringProperties properties, MeteringServiceBinding binding) {
		this.properties = properties;
		this.binding = binding;
		this.executor = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(16),
				runnable -> {
					Thread t = new Thread(runnable, "metering-sender");
					t.setDaemon(true);
					return t;
				}, new ThreadPoolExecutor.DiscardOldestPolicy());
	}

	/**
	 * Submit the aggregated distinct-user count for the given calendar month. Never
	 * throws; failures are logged with HTTP status only.
	 * @param month ISO {@code YYYY-MM}
	 * @param value distinct count (may be 0 for scenario s7)
	 */
	public void send(String month, long value) {
		this.executor.execute(() -> submitQuietly(month, value));
	}

	private void submitQuietly(String month, long value) {
		try {
			RestClient client = ensureClient();
			if (client == null) {
				logger.info("metering: binding absent — skipping submission for month={} value={}", month, value);
				return;
			}
			String url = this.binding.getCredentials().get().getUsageIngestion() + INGESTION_PATH;
			List<Map<String, Object>> payload = List.of(buildEvent(month, value));
			client.post()
				.uri(URI.create(url))
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.toBodilessEntity();
			logger.info("metering: submitted {} for month={} value={}", this.properties.getMeasureId(), month, value);
		}
		catch (RestClientException ex) {
			logger.warn("metering: HTTP submission failed for month={}: {}", month, ex.getMessage());
		}
		catch (RuntimeException ex) {
			logger.warn("metering: unexpected submission failure for month={} ({}): {}", month,
					ex.getClass().getSimpleName(), ex.getMessage());
		}
	}

	private Map<String, Object> buildEvent(String month, long value) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("id", deterministicEventId(month));
		event.put("timestamp", endOfMonthUtc(month).toString());

		Map<String, Object> btp = new LinkedHashMap<>();
		btp.put("environment", this.properties.getEnvironment());
		if (!this.properties.getSubAccount().isBlank()) {
			btp.put("subAccount", this.properties.getSubAccount());
		}
		else if (!this.properties.getGlobalAccount().isBlank()) {
			btp.put("globalAccount", this.properties.getGlobalAccount());
		}

		Map<String, Object> consumer = new LinkedHashMap<>();
		consumer.put("region", this.properties.getRegion());
		consumer.put("btp", btp);
		event.put("consumer", consumer);

		Map<String, Object> service = new LinkedHashMap<>();
		service.put("id", this.properties.getServiceId());
		Map<String, Object> product = new LinkedHashMap<>();
		product.put("service", service);
		event.put("product", product);

		Map<String, Object> measure = new LinkedHashMap<>();
		measure.put("id", this.properties.getMeasureId());
		measure.put("value", value);
		event.put("measure", measure);
		return event;
	}

	private String deterministicEventId(String month) {
		String seed = this.properties.getMeasureId() + "|" + month;
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
	}

	private Instant endOfMonthUtc(String month) {
		YearMonth ym = YearMonth.parse(month);
		return ym.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
	}

	private synchronized RestClient ensureClient() {
		if (this.clientInitialised) {
			return this.restClient;
		}
		this.clientInitialised = true;
		if (this.binding.getCredentials().isEmpty()) {
			return null;
		}
		try {
			SSLContext sslContext = buildSslContext(this.binding.getCredentials().get());
			HttpClient httpClient = HttpClient.newBuilder().sslContext(sslContext).build();
			JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
			this.restClient = RestClient.builder().requestFactory(factory).build();
			return this.restClient;
		}
		catch (RuntimeException ex) {
			logger.warn("metering: failed to build mTLS client ({}): {}", ex.getClass().getSimpleName(),
					ex.getMessage());
			return null;
		}
		catch (Exception ex) {
			logger.warn("metering: failed to build mTLS client ({}): {}", ex.getClass().getSimpleName(),
					ex.getMessage());
			return null;
		}
	}

	private static SSLContext buildSslContext(MeteringServiceBinding.Credentials creds) throws Exception {
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		List<Certificate> chain = new ArrayList<>();
		for (String pem : splitPemBlocks(creds.getCertificate(), "CERTIFICATE")) {
			byte[] der = Base64.getMimeDecoder().decode(pem);
			chain.add(cf.generateCertificate(new ByteArrayInputStream(der)));
		}
		if (chain.isEmpty()) {
			throw new IllegalStateException("metering: no certificates in binding");
		}

		byte[] keyDer = Base64.getMimeDecoder()
			.decode(splitPemBlocks(creds.getKey(), "PRIVATE KEY").get(0));
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyDer);
		PrivateKey privateKey = parsePrivateKey(keySpec);

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, new char[0]);
		keyStore.setKeyEntry("metering", privateKey, new char[0], chain.toArray(new Certificate[0]));

		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(keyStore, new char[0]);

		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init((KeyStore) null);

		SSLContext ctx = SSLContext.getInstance("TLS");
		ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		return ctx;
	}

	private static PrivateKey parsePrivateKey(PKCS8EncodedKeySpec spec) throws Exception {
		try {
			return KeyFactory.getInstance("RSA").generatePrivate(spec);
		}
		catch (Exception rsaFailure) {
			try {
				return KeyFactory.getInstance("EC").generatePrivate(spec);
			}
			catch (Exception ecFailure) {
				return KeyFactory.getInstance("DSA").generatePrivate(spec);
			}
		}
	}

	private static List<String> splitPemBlocks(String pem, String type) {
		List<String> blocks = new ArrayList<>();
		String begin = "-----BEGIN " + type + "-----";
		String end = "-----END " + type + "-----";
		int cursor = 0;
		while (true) {
			int b = pem.indexOf(begin, cursor);
			if (b < 0) {
				break;
			}
			int e = pem.indexOf(end, b);
			if (e < 0) {
				break;
			}
			blocks.add(pem.substring(b + begin.length(), e));
			cursor = e + end.length();
		}
		// Accept keys/certs stored without PEM markers too.
		if (blocks.isEmpty() && !pem.isBlank()) {
			blocks.add(pem);
		}
		return blocks;
	}

	@SuppressWarnings("unused")
	private static Certificate castToX509(Certificate certificate) {
		return (X509Certificate) certificate;
	}

	@PreDestroy
	void shutdown() {
		this.executor.shutdown();
		try {
			if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
				this.executor.shutdownNow();
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			this.executor.shutdownNow();
		}
	}

}
