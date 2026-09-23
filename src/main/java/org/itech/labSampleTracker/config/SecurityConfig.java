package org.itech.labSampleTracker.config;

import java.util.List;

import org.itech.labSampleTracker.security.JwtAuthFilter;
import org.itech.labSampleTracker.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter.Directive;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Configuration
public class SecurityConfig {

	@Autowired
	private UserDetailsService userDetailsService;

	@Bean
	public DaoAuthenticationProvider authProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(userDetailsService);
		authProvider.setPasswordEncoder(passwordEncoder());
		return authProvider;
	}

	@Bean
	public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
		return http.getSharedObject(AuthenticationManagerBuilder.class).authenticationProvider(authProvider()).build();
	}

	@Bean
	public static PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		var cfg = new CorsConfiguration();
		cfg.setAllowedOriginPatterns(List.of("*"));
		cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
		cfg.setExposedHeaders(List.of("Authorization"));
		cfg.setAllowCredentials(false);
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cfg);
		return source;
	}

	// ====== API (JWT) ======
	@Bean
	@Order(1)
	public SecurityFilterChain apiFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
		return http.securityMatcher("/api_v2/**").cors(Customizer.withDefaults()).csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(new RestAuthenticationEntryPoint())
						.accessDeniedHandler(new RestAccessDeniedHandler()))
				.authorizeHttpRequests(req -> req.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/api_v2/auth/**").permitAll().requestMatchers("/api_v2/meta/**")
						.authenticated().requestMatchers("/api_v2/sync/samples/**").authenticated()
						.requestMatchers("/api_v2/admin/**").hasAuthority("ADMIN").anyRequest().authenticated())
				.addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
				.httpBasic(Customizer.withDefaults()).formLogin(AbstractHttpConfigurer::disable).build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(
				request -> request.requestMatchers("/admin/**").hasAnyAuthority("ADMIN").anyRequest().authenticated()

		).formLogin(form -> form.loginPage("/login").successForwardUrl("/").permitAll().defaultSuccessUrl("/", false))
				.logout((logout) -> logout
						.addLogoutHandler(new HeaderWriterLogoutHandler(new ClearSiteDataHeaderWriter(Directive.CACHE,
								Directive.COOKIES, Directive.EXECUTION_CONTEXTS, Directive.STORAGE)))
						.logoutUrl("/logout").logoutSuccessUrl("/"))
				// Track active sessions in SessionRegistry so we can revoke them
				// programmatically (e.g. when an admin deactivates or locks a user).
				.sessionManagement(session -> session
						.maximumSessions(-1)
						.sessionRegistry(sessionRegistry())
						.expiredUrl("/login?expired"));
		http.httpBasic(Customizer.withDefaults());
		// CSRF protection for the web (session-based) chain. The token is
		// exposed via a cookie (XSRF-TOKEN) so jQuery $.ajax can read it, and
		// expected back in the X-XSRF-TOKEN header on every state-changing
		// request. Thymeleaf forms get the token automatically via Spring
		// Security's RequestDataValueProcessor (hidden input _csrf).
		//
		// IMPORTANT: this only applies to the WEB chain (Order 2). The mobile
		// API chain (Order 1, /api_v2/**) is stateless JWT and keeps CSRF
		// disabled — see apiFilterChain above. So the mobile app is NOT
		// impacted by this change.
		//
		// We use CsrfTokenRequestAttributeHandler with
		// {@code setCsrfRequestAttributeName(null)} so the token is eagerly
		// materialized on every request (otherwise the cookie is not set on
		// the first GET, and the subsequent POST fails with 403).
		org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler csrfHandler =
				new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler();
		csrfHandler.setCsrfRequestAttributeName(null);

		http.csrf(csrf -> csrf
				.csrfTokenRepository(org.springframework.security.web.csrf
						.CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(csrfHandler));
		return http.build();
	}

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.ignoring()
				.requestMatchers("/webjars/**", "/content/**", "/csrf", "/css/**", "/js/**", "/img/**",
						"/DataTables/**", "/legal/**")
				.requestMatchers("/error/**").requestMatchers("/error").requestMatchers("/resources/**")
				// Actuator : servi uniquement sur le port de gestion interne
				// (management.server.port), jamais publié ; le port public
				// renvoie 404 sur /actuator/*. Sans authentification pour le
				// healthcheck du conteneur et le serveur de supervision.
				.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus");
	}

	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

	/**
	 * Registry of active HTTP sessions, used by UserSessionManagementService
	 * to forcibly expire sessions when an admin deactivates or locks a user.
	 */
	@Bean
	public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
		return new org.springframework.security.core.session.SessionRegistryImpl();
	}

	/**
	 * Bridges servlet container session events to Spring Security's
	 * SessionRegistry so it stays in sync with active sessions.
	 */
	@Bean
	public org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher() {
		return new org.springframework.security.web.session.HttpSessionEventPublisher();
	}

}
