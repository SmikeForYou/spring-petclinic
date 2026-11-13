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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC {@link HandlerInterceptor} that observes every inbound HTTP request handled
 * by the DispatcherServlet exactly once and increments the monthly request counter.
 *
 * <p>
 * Runs regardless of HTTP method, URL path, response status or authentication state and
 * never for outbound HTTP client calls (which do not traverse the DispatcherServlet).
 * Counter failures are logged and swallowed so metering never breaks a business request.
 * </p>
 */
@Component
public class MeteringRequestInterceptor implements HandlerInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(MeteringRequestInterceptor.class);

	private final MonthlyRequestCounter counter;

	public MeteringRequestInterceptor(MonthlyRequestCounter counter) {
		this.counter = counter;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		try {
			this.counter.increment();
		}
		catch (RuntimeException ex) {
			logger.warn("Failed to count inbound HTTP request for metering: {}", ex.toString());
		}
		return true;
	}

}
