package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Actions AJAX de l'administration en HTTP réel, comme le navigateur :
 * connexion par le formulaire, jeton lu dans le cookie XSRF-TOKEN et renvoyé
 * dans l'en-tête X-XSRF-TOKEN (ce que fait scripts.js), ou dans le champ
 * _csrf d'un formulaire (mesh-actions.js). MockMvc ne convient pas ici : il
 * remplace le dépôt de jetons par le sien.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"app.jwt.secret=bHN0cmFja2VyLWludGVncmF0aW9uLXRlc3RzLW9ubHktc2VjcmV0LWtleS1ub3QtZm9yLXByb2R1Y3Rpb24tdXNlLTAwMDE=",
		"spring.liquibase.default-schema=sample_tracker",
		"management.server.port=-1",
		"logging.file.name=",
		"lstracker.oedatarepo.enabled=false" })
class AdminAjaxCsrfIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:14")
			.withInitScript("testcontainers/create-schema.sql");

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=sample_tracker");
		registry.add("spring.datasource.username", PG::getUsername);
		registry.add("spring.datasource.password", PG::getPassword);
	}

	/** Compte administrateur créé par le changelog (init_db.xml). */
	private static final String ADMIN_PASSWORD = "Admin_24!";

	@LocalServerPort
	private int port;
	@Autowired
	private NamedParameterJdbcTemplate jdbc;

	private CookieManager cookies;
	private HttpClient http;

	@BeforeEach
	void loggedInAsAdmin() throws Exception {
		cookies = new CookieManager();
		http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
		get("/login");
		HttpResponse<String> login = form("/login",
				Map.of("username", "admin", "password", ADMIN_PASSWORD, "_csrf", xsrfCookie()));
		assertEquals(302, login.statusCode());
		assertFalse(login.headers().firstValue("Location").orElse("").contains("error"), "connexion refusée");
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> get(String path) throws Exception {
		return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private String xsrfCookie() {
		return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
				.map(HttpCookie::getValue).findFirst().orElseThrow(() -> new AssertionError("cookie XSRF-TOKEN absent"));
	}

	private HttpResponse<String> form(String path, Map<String, String> fields) throws Exception {
		StringBuilder body = new StringBuilder();
		fields.forEach((k, v) -> body.append(body.length() == 0 ? "" : "&").append(k).append('=')
				.append(URLEncoder.encode(v, StandardCharsets.UTF_8)));
		return http.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Comme $.post après scripts.js : jeton du cookie dans l'en-tête X-XSRF-TOKEN. */
	private HttpResponse<String> ajaxPost(String path, boolean withToken) throws Exception {
		get("/appuser"); // page de l'écran : le jeton courant est posé dans le cookie
		HttpRequest.Builder req = HttpRequest.newBuilder(uri(path)).header("X-Requested-With", "XMLHttpRequest")
				.POST(HttpRequest.BodyPublishers.noBody());
		if (withToken) {
			req.header("X-XSRF-TOKEN", xsrfCookie());
		}
		return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
	}

	private int newAccount(String login) {
		return jdbc.queryForObject("INSERT INTO app_user (login, password, phone_contact, password_reset, is_active, "
				+ "is_locked, last_name, first_name, created_at, role, user_level, user_type) VALUES ('" + login
				+ "', 'x', '07" + Math.abs(login.hashCode() % 100000000) + "', false, true, false, 'Test', 'Test', "
				+ "now(), 'USER', 'REGION', 'AUTRE') RETURNING id", Map.of(), Integer.class);
	}

	private Map<String, Object> account(int id) {
		return jdbc.queryForMap("SELECT is_active, is_locked, password_reset FROM app_user WHERE id = " + id,
				Map.of());
	}

	@Test
	void sansJetonLActionEstRefusee() throws Exception {
		int id = newAccount("sansjeton");
		assertEquals(403, ajaxPost("/appuser/" + id + "/toggle-active", false).statusCode());
		assertEquals(Boolean.TRUE, account(id).get("is_active"));
	}

	@Test
	void desactiverVerrouillerEtReinitialiserUnCompte() throws Exception {
		int id = newAccount("actionsajax");
		assertEquals(200, ajaxPost("/appuser/" + id + "/toggle-active", true).statusCode(), "désactiver");
		assertEquals(200, ajaxPost("/appuser/" + id + "/toggle-lock", true).statusCode(), "verrouiller");
		HttpResponse<String> reset = ajaxPost("/appuser/" + id + "/reset-password", true);
		assertEquals(200, reset.statusCode(), "réinitialiser le mot de passe");
		assertTrue(reset.body().contains("temporaryPassword"));
		Map<String, Object> row = account(id);
		assertEquals(Boolean.FALSE, row.get("is_active"));
		assertEquals(Boolean.TRUE, row.get("is_locked"));
		assertEquals(Boolean.TRUE, row.get("password_reset"));
	}

	@Test
	void desactiverUnElementDuMaillage() throws Exception {
		int district = jdbc.queryForObject("SELECT min(id) FROM district", Map.of(), Integer.class);
		int site = jdbc.queryForObject("INSERT INTO site (name, dhis_code, district_id) VALUES ('Site maillage', "
				+ "'SITE-MAIL', " + district + ") RETURNING id", Map.of(), Integer.class);
		get("/site");
		HttpResponse<String> r = form("/site/deactivate/" + site, Map.of("_csrf", xsrfCookie()));
		assertEquals(302, r.statusCode());
		assertEquals(Boolean.FALSE,
				jdbc.queryForObject("SELECT is_active FROM site WHERE id = " + site, Map.of(), Boolean.class));
	}
}
