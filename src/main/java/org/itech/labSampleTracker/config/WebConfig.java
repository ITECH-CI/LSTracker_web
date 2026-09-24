package org.itech.labSampleTracker.config;

import java.util.List;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@EnableWebMvc
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Bean
	public BCryptPasswordEncoder bCryptPasswordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Expose la requête courante (RequestContextHolder) dès l'entrée, avant la
	 * chaîne de sécurité, comme le fait Spring Boot par défaut — filtre perdu
	 * avec {@code @EnableWebMvc}, qui désactive l'auto-configuration MVC. Sans
	 * lui, les événements de connexion web (formulaire /login, traité par un
	 * filtre) n'ont pas accès à la requête (journal des connexions).
	 */
	@Bean
	public org.springframework.boot.web.servlet.filter.OrderedRequestContextFilter springRequestContextFilter() {
		return new org.springframework.boot.web.servlet.filter.OrderedRequestContextFilter();
	}

	@Bean
	public LocaleChangeInterceptor localeInterceptor() {
		LocaleChangeInterceptor localeInterceptor = new LocaleChangeInterceptor();
		localeInterceptor.setParamName("lang");
		return localeInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(localeInterceptor());
		// registry.addInterceptor(new
		// AuthorizationInterceptor()).addPathPatterns("/api/**");
	}
//
//	@Bean
//	public ResourceBundleMessageSource messageSource() {
//		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
//		messageSource.setBasename("messages");
//		return messageSource;
//	}
//	

	@Bean
	public MessageSource messageSource() {
		ReloadableResourceBundleMessageSource m = new ReloadableResourceBundleMessageSource();
		m.setBasename("classpath:messages");
		m.setDefaultEncoding("UTF-8");
		m.setFallbackToSystemLocale(false);
		return m;
	}

	@Bean
	public InternalResourceViewResolver defaultViewResolver() {
		return new InternalResourceViewResolver();
	}

	@Override
	public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		converters.removeIf(c -> c instanceof MappingJackson2XmlHttpMessageConverter);
	}

}
