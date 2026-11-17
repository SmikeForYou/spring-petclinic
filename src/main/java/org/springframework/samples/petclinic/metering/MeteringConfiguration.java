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

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates the SAP Unified Metering collection for PetClinic.
 *
 * <p>
 * Discovered by the {@code @SpringBootApplication} component scan on
 * {@link org.springframework.samples.petclinic.PetClinicApplication}, this class
 * enables {@link MeteringProperties} binding and the scheduled monthly flush. The
 * individual components ({@link MeteringInterceptor},
 * {@link VisitorIdentityResolver}, {@link MeteredVisitorRecorder},
 * {@link MeteringServiceBinding}, {@link MeteringSender} and
 * {@link MeteringFlushJob}) are discovered through the same component scan, exactly
 * like the existing {@code PetTypeFormatter} and controllers.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MeteringProperties.class)
@EnableScheduling
public class MeteringConfiguration {

}
