package org.springframework.samples.petclinic.system;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;
import org.springframework.samples.petclinic.metering.MeteringInterceptor;

@Configuration
@SuppressWarnings("unused")
public class WebConfiguration implements WebMvcConfigurer {

	private final MeteringInterceptor meteringInterceptor;

	public WebConfiguration(MeteringInterceptor meteringInterceptor) {
		this.meteringInterceptor = meteringInterceptor;
	}

	/**
	 * Uses session storage to remember the user’s language setting across requests.
	 * Defaults to English if nothing is specified.
	 * @return session-based {@link LocaleResolver}
	 */
	@Bean
	public LocaleResolver localeResolver() {
		SessionLocaleResolver resolver = new SessionLocaleResolver();
		resolver.setDefaultLocale(Locale.ENGLISH);
		return resolver;
	}

	/**
	 * Allows the app to switch languages using a URL parameter like
	 * <code>?lang=es</code>.
	 * @return a {@link LocaleChangeInterceptor} that handles the change
	 */
	@Bean
	public LocaleChangeInterceptor localeChangeInterceptor() {
		LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
		interceptor.setParamName("lang");
		return interceptor;
	}

	/**
	 * Registers the locale change interceptor and the Unified Metering interceptor so
	 * they run on each inbound DispatcherServlet request. Outbound {@code RestClient}
	 * invocations do not pass through here.
	 * @param registry where interceptors are added
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(localeChangeInterceptor());
		registry.addInterceptor(this.meteringInterceptor)
			.addPathPatterns("/**")
			.excludePathPatterns("/actuator/**", "/webjars/**", "/resources/**", "/css/**", "/js/**", "/images/**",
					"/fonts/**", "/oups");
	}

}
