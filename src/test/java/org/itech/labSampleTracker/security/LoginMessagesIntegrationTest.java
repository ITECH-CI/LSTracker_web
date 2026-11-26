package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.HtmlUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Messages de connexion avant blocage, en HTTP réel, sur la page de
 * connexion et sur /api_v2/auth/login : mêmes messages pour un compte réel
 * et pour un identifiant inexistant ; seul le compte réel est verrouillé en
 * base. Indicateur password_reset de la réponse de connexion mobile.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"app.jwt.secret=bHN0cmFja2VyLWludGVncmF0aW9uLXRlc3RzLW9ubHktc2VjcmV0LWtleS1ub3QtZm9yLXByb2R1Y3Rpb24tdXNlLTAwMDE=",
		"spring.liquibase.default-schema=sample_tracker",
		"management.server.port=-1",
		"logging.file.name=",
		"lstracker.oedatarepo.enabled=false" })
class LoginMessagesIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:14")
			.withInitScript("testcontainers/create-schema.sql");

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=sample_tracker");
		registry.add("spring.datasource.username", PG::getUsername);
		registry.add("spring.datasource.password", PG::getPassword);
	}

	private static final String PASSWORD = "Agent@2026";
	private static final List<String> EXPECTED = List.of(
			"Identifiant ou mot de passe incorrect.",
			"Identifiant ou mot de passe incorrect.",
			"Identifiant ou mot de passe incorrect. Il vous reste 2 tentative(s) avant le blocage du compte.",
			"Identifiant ou mot de passe incorrect. Il vous reste 1 tentative(s) avant le blocage du compte.",
			"Compte bloqué après 5 tentatives. Contactez l'administration centrale.");

	@LocalServerPort
	private int port;
	@Autowired
	private NamedParameterJdbcTemplate jdbc;
	@Autowired
	private BCryptPasswordEncoder encoder;

	private static int phone = 1;

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private void account(String login, boolean passwordReset) {
		jdbc.update("INSERT INTO app_user (login, password, phone_contact, password_reset, is_active, is_locked, "
				+ "last_name, first_name, created_at, role, user_level, user_type) VALUES (:login, :pwd, :phone, "
				+ ":reset, true, false, 'Test', 'Test', now(), 'USER', 'REGION', 'AUTRE')",
				Map.of("login", login, "pwd", encoder.encode(PASSWORD), "phone", String.format("05%08d", phone++),
						"reset", passwordReset));
	}

	private Boolean locked(String login) {
		return jdbc.queryForObject("SELECT is_locked FROM app_user WHERE login = :l", Map.of("l", login),
				Boolean.class);
	}

	/** Une tentative par le formulaire de connexion, comme le navigateur ; renvoie le message affiché. */
	private String webAttempt(String login, String password) throws Exception {
		CookieManager cookies = new CookieManager();
		HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER)
				.build();
		http.send(HttpRequest.newBuilder(uri("/login")).GET().build(), HttpResponse.BodyHandlers.ofString());
		String csrf = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
				.map(HttpCookie::getValue).findFirst().orElseThrow();
		String body = "username=" + URLEncoder.encode(login, StandardCharsets.UTF_8) + "&password="
				+ URLEncoder.encode(password, StandardCharsets.UTF_8) + "&_csrf=" + csrf;
		HttpResponse<String> post = http.send(HttpRequest.newBuilder(uri("/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(302, post.statusCode());
		String location = post.headers().firstValue("Location").orElse("");
		assertTrue(location.endsWith("/login?error"), "connexion acceptée : " + location);
		HttpResponse<String> pageResponse = http.send(HttpRequest.newBuilder(uri("/login?error")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertEquals(200, pageResponse.statusCode(), "page de connexion après échec");
		String page = pageResponse.body();
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("alert-modern is-error\".*?<span[^>]*>(.*?)</span>", java.util.regex.Pattern.DOTALL)
				.matcher(page);
		int at = page.indexOf("alert-modern");
		assertTrue(m.find(), "message absent : " + (at < 0 ? page.substring(0, Math.min(300, page.length()))
				: page.substring(at, Math.min(page.length(), at + 300))));
		return HtmlUtils.htmlUnescape(m.group(1)).trim();
	}

	private HttpResponse<String> apiLogin(String login, String password) throws Exception {
		String json = "{\"username\":\"" + login + "\",\"password\":\"" + password + "\"}";
		return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api_v2/auth/login"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private String apiAttempt(String login, String password) throws Exception {
		HttpResponse<String> r = apiLogin(login, password);
		assertEquals(401, r.statusCode(), r.body());
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"message\":\"([^\"]*)\"").matcher(r.body());
		assertTrue(m.find(), r.body());
		return m.group(1);
	}

	@Test
	void pageDeConnexionMemesMessagesPourUnCompteReelEtUnIdentifiantInexistant() throws Exception {
		account("agentweb", false);
		List<String> real = new ArrayList<>(), unknown = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			real.add(webAttempt("agentweb", "mauvais"));
			unknown.add(webAttempt("personneweb", "mauvais"));
		}
		assertEquals(EXPECTED, real);
		assertEquals(EXPECTED, unknown);
		assertEquals(Boolean.TRUE, locked("agentweb"), "compte réel verrouillé en base");
		// Bloqué, y compris avec le bon mot de passe ; l'inexistant reste annoncé bloqué.
		assertEquals(EXPECTED.get(4), webAttempt("agentweb", PASSWORD));
		assertEquals(EXPECTED.get(4), webAttempt("personneweb", "mauvais"));
	}

	@Test
	void apiMobileMemesMessagesDansLaReponse() throws Exception {
		account("agentapi", false);
		List<String> real = new ArrayList<>(), unknown = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			real.add(apiAttempt("agentapi", "mauvais"));
			unknown.add(apiAttempt("personneapi", "mauvais"));
		}
		assertEquals(EXPECTED, real);
		assertEquals(EXPECTED, unknown);
		assertEquals(Boolean.TRUE, locked("agentapi"));
		assertEquals(EXPECTED.get(4), apiAttempt("agentapi", PASSWORD));
	}

	@Test
	void laConnexionMobileSignaleUnMotDePasseTemporaire() throws Exception {
		account("temporaire", true);
		account("definitif", false);
		HttpResponse<String> reset = apiLogin("temporaire", PASSWORD);
		assertEquals(200, reset.statusCode(), reset.body());
		assertTrue(reset.body().contains("\"password_reset\":true"), reset.body());
		HttpResponse<String> normal = apiLogin("definitif", PASSWORD);
		assertEquals(200, normal.statusCode(), normal.body());
		assertTrue(normal.body().contains("\"password_reset\":false"), normal.body());
	}
}
