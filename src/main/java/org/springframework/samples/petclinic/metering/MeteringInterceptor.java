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

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC {@link HandlerInterceptor} that records distinct monthly visitors for the
 * Unified Metering measure {@code HS_PET_CLINIC_MONTLY_USER_COUNT}.
 *
 * <p>
 * Because it is registered on the {@code InterceptorRegistry} of the
 * {@link org.springframework.web.servlet.DispatcherServlet}, it only fires for inbound
 * HTTP requests handled by the application. Outbound {@code RestClient} invocations
 * use {@code ClientHttpRequestInterceptor} and never pass through here (scenario s4).
 *
 * <p>
 * The handler is a strict no-op whenever the production gate is disabled (scenario
 * s5) or when the request matches a known infrastructure/probe path or user agent.
 * The recorder's call is always wrapped in try/catch so a metering failure cannot
 * fail or delay the business request.
 */
@Component
public class MeteringInterceptor implements HandlerInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(MeteringInterceptor.class);

	private static final List<String> PROBE_USER_AGENT_TOKENS = Arrays.asList("kube-probe", "HealthCheck",
			"ELB-HealthChecker", "GoogleHC", "Blackbox");

	private static final List<String> EXCLUDED_PATH_PREFIXES = Arrays.asList("/actuator", "/webjars", "/resources",
			"/css", "/js", "/images", "/fonts");

	private final MeteringProperties properties;

	private final VisitorIdentityResolver identityResolver;

	private final MeteredVisitorRecorder recorder;

	public MeteringInterceptor(MeteringProperties properties, VisitorIdentityResolver identityResolver,
			MeteredVisitorRecorder recorder) {
		this.properties = properties;
		this.identityResolver = identityResolver;
		this.recorder = recorder;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!this.properties.isProduction()) {
			return true;
		}
		if (isInfrastructureRequest(request)) {
			return true;
		}
		try {
			String visitorId = this.identityResolver.resolve(request, response);
			String month = YearMonth.now(ZoneId.of(this.properties.getTimezone())).toString();
			this.recorder.recordDistinct(month, visitorId);
		}
		catch (RuntimeException ex) {
			logger.warn("metering: interceptor failure ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
		}
		return true;
	}

	private boolean isInfrastructureRequest(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri == null) {
			return false;
		}
		if ("/oups".equals(uri)) {
			return true;
		}
		for (String prefix : EXCLUDED_PATH_PREFIXES) {
			if (uri.startsWith(prefix)) {
				return true;
			}
		}
		String userAgent = request.getHeader("User-Agent");
		if (userAgent != null) {
			for (String token : PROBE_USER_AGENT_TOKENS) {
				if (userAgent.contains(token)) {
					return true;
				}
			}
		}
		return false;
	}

}
