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

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

/**
 * Resolves a stable pseudonymous visitor identity for an inbound HTTP request.
 *
 * <p>
 * The identity is stored in a long-lived HttpOnly cookie (see
 * {@link MeteringProperties#getCookieName()}). When the cookie is absent or malformed a
 * fresh UUID is minted and set on the response, so the same browser produces the same
 * identifier for subsequent requests within the calendar month. No personal data is
 * ever included in the payload sent to Unified Metering; the pseudonymous id is only
 * used for in-store deduplication.
 *
 * <p>
 * Browser/device boundary limitation: a human visiting the app from two different
 * browsers or devices without authentication is counted as two distinct visitors. This
 * is an accepted limitation of pseudonymous identity recorded in the measurement
 * contract assumptions.
 */
@Component
public class VisitorIdentityResolver {

	private static final Pattern UUID_PATTERN = Pattern
		.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	private final MeteringProperties properties;

	public VisitorIdentityResolver(MeteringProperties properties) {
		this.properties = properties;
	}

	/**
	 * Return a stable pseudonymous identifier for the caller, minting and setting a
	 * new cookie when none is present.
	 */
	public String resolve(HttpServletRequest request, HttpServletResponse response) {
		String cookieName = this.properties.getCookieName();
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookieName.equals(cookie.getName())) {
					String value = cookie.getValue();
					if (value != null && UUID_PATTERN.matcher(value).matches()) {
						return value;
					}
				}
			}
		}
		String fresh = UUID.randomUUID().toString();
		writeCookie(request, response, cookieName, fresh);
		return fresh;
	}

	private void writeCookie(HttpServletRequest request, HttpServletResponse response, String name, String value) {
		int maxAgeSeconds = Math.max(1, this.properties.getCookieMaxAgeDays()) * 24 * 60 * 60;
		StringBuilder sb = new StringBuilder();
		sb.append(name).append('=').append(value);
		sb.append("; Path=/");
		sb.append("; Max-Age=").append(maxAgeSeconds);
		sb.append("; HttpOnly");
		sb.append("; SameSite=Lax");
		if (request.isSecure()) {
			sb.append("; Secure");
		}
		response.addHeader("Set-Cookie", sb.toString());
	}

}
