package org.itech.labSampleTracker.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;
import org.springframework.web.servlet.resource.VersionResourceResolver;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
	private static final String[] CLASSPATH_RESOURCE_LOCATIONS = { "classpath:/META-INF/resources/",
			"classpath:/resources/", "classpath:/static/", "classpath:/public/", "classpath:/custom/" };

	/**
	 * Ressources statiques avec empreinte de contenu (cahier VII.1) : les URL
	 * générées par th:href / th:src portent un hachage du fichier
	 * (home-dashboard-3f2a....js). Le navigateur peut donc les garder en cache
	 * un an : l'URL change dès que le fichier change. Toute ressource doit être
	 * référencée via th:href / th:src.
	 *
	 * <p>Configuré ici et non par les propriétés spring.web.resources.* :
	 * {@code @EnableWebMvc} (WebConfig) désactive l'auto-configuration MVC de
	 * Spring Boot, qui ignorerait ces propriétés.
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**").addResourceLocations(CLASSPATH_RESOURCE_LOCATIONS)
				.setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
				.resourceChain(true)
				.addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"));
	}

	/** Réécrit les URL th:href / th:src avec l'empreinte de contenu. */
	@Bean
	public ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
		return new ResourceUrlEncodingFilter();
	}
}
