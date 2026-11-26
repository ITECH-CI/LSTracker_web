package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.itech.labSampleTracker.service_impl.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Périmètre des profils de supervision et actions de l'administration, de
 * bout en bout : application complète (sécurité, contrôleurs, vues,
 * requêtes) sur PostgreSQL, schéma créé par le changelog Liquibase. Les
 * comptes sont créés par le formulaire d'administration, comme dans l'écran.
 *
 * <p>Région A : un site et un labo, l'échantillon PAT-ZONE-A. Région B : un
 * site, l'échantillon PAT-ZONE-B, et PAT-B-VERS-A collecté en B et déposé au
 * labo de A. Région C : des districts mais aucun site. Un circuit par
 * région (CIRC-ZONE-A, CIRC-ZONE-B) et un échantillon sans zone, ancien
 * (hors de la période par défaut), antérieur à la contrainte de zone.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
		"app.jwt.secret=bHN0cmFja2VyLWludGVncmF0aW9uLXRlc3RzLW9ubHktc2VjcmV0LWtleS1ub3QtZm9yLXByb2R1Y3Rpb24tdXNlLTAwMDE=",
		"spring.liquibase.default-schema=sample_tracker",
		"management.server.port=-1",
		"logging.file.name=",
		"lstracker.oedatarepo.enabled=false" })
@AutoConfigureMockMvc
class ZoneSupervisorAccessIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:14")
			.withInitScript("testcontainers/create-schema.sql");

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=sample_tracker");
		registry.add("spring.datasource.username", PG::getUsername);
		registry.add("spring.datasource.password", PG::getPassword);
	}

	private static final String PASSWORD = "Test@2026x";
	private static final String A = "PAT-ZONE-A", B = "PAT-ZONE-B", B_TO_LAB_A = "PAT-B-VERS-A";
	private static final String NO_ZONE = "PAT-SANS-ZONE";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private NamedParameterJdbcTemplate jdbc;
	@Autowired
	private UserDetailsServiceImpl userDetails;
	@Autowired
	private JwtService jwtService;

	/** Jeu de données commun, créé au premier test (le contexte Spring est partagé). */
	private static boolean ready;
	private static int regionA, regionB, regionC, districtA, districtB, siteA, siteB, labA, labB;
	private static int sampleA, sampleB, sampleBToLabA;
	private static int phone = 1;

	@BeforeEach
	void referentialSamplesAndAccounts() throws Exception {
		if (ready) {
			return;
		}
		// Trois régions du référentiel chargé par Liquibase ; seules A et B reçoivent un site.
		List<Map<String, Object>> districts = jdbc.queryForList(
				"SELECT DISTINCT ON (region_id) id, region_id FROM district ORDER BY region_id, id LIMIT 3", Map.of());
		districtA = num(districts.get(0).get("id"));
		regionA = num(districts.get(0).get("region_id"));
		districtB = num(districts.get(1).get("id"));
		regionB = num(districts.get(1).get("region_id"));
		regionC = num(districts.get(2).get("region_id"));

		siteA = insertId("INSERT INTO site (name, dhis_code, district_id) VALUES ('Site A', 'SITE-A', " + districtA
				+ ") RETURNING id");
		siteB = insertId("INSERT INTO site (name, dhis_code, district_id) VALUES ('Site B', 'SITE-B', " + districtB
				+ ") RETURNING id");
		labA = insertId("INSERT INTO lab (lab_name, lab_type, district_id, created_at) VALUES ('Labo A', 'DISTRICT', "
				+ districtA + ", now()) RETURNING id");
		labB = insertId("INSERT INTO lab (lab_name, lab_type, district_id, created_at) VALUES ('Labo B', 'DISTRICT', "
				+ districtB + ", now()) RETURNING id");
		int admin = insertId("SELECT id FROM app_user WHERE login = 'admin'");
		int retrievingA = retrieving(admin, siteA);
		int retrievingB = retrieving(admin, siteB);
		sampleA = sample(A, retrievingA, String.valueOf(labA));
		sampleB = sample(B, retrievingB, "NULL");
		sampleBToLabA = sample(B_TO_LAB_A, retrievingB, String.valueOf(labA));
		for (int[] c : new int[][] { { siteA, 'A' }, { siteB, 'B' } }) {
			int circuit = insertId("INSERT INTO circuit (circuit_number) VALUES ('CIRC-ZONE-" + (char) c[1]
					+ "') RETURNING id");
			jdbc.update("INSERT INTO circuit_site (circuit_id, site_id, is_active) VALUES (" + circuit + ", " + c[0]
					+ ", true)", Map.of());
		}
		// Échantillon sans collecte ni site demandeur, antérieur à la contrainte
		// ck_sample_collection_or_requester : on la retire le temps de l'insérer.
		String check = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
				+ "WHERE conname = 'ck_sample_collection_or_requester'", Map.of(), String.class);
		jdbc.update("ALTER TABLE sample DROP CONSTRAINT ck_sample_collection_or_requester", Map.of());
		jdbc.update("INSERT INTO sample (patient_identifier, collection_date, created_at, lastupdated_at, "
				+ "sample_status_id, sample_type_id) VALUES ('" + NO_ZONE + "', CURRENT_DATE - 1000, now(), now(), "
				+ "(SELECT id FROM sample_status WHERE status = 'ON_TRANSIT'), "
				+ "(SELECT id FROM sample_type WHERE name = 'CV'))", Map.of());
		jdbc.update("ALTER TABLE sample ADD CONSTRAINT ck_sample_collection_or_requester " + check, Map.of());

		createAccount("supregion", "SUPERVISEUR_REGION", null, null, List.of(regionA), List.of());
		createAccount("supdistrict", "SUPERVISEUR_DISTRICT", null, null, List.of(), List.of(districtA));
		createAccount("supvide", "SUPERVISEUR_REGION", null, null, List.of(regionC), List.of());
		createAccount("sansaffect", "USER", "REGION_LEVEL", "OTHER", List.of(), List.of());
		ready = true;
	}

	// ------------------------------------------------------------------ données

	private int insertId(String sql) {
		return jdbc.queryForObject(sql, Map.of(), Integer.class);
	}

	private int retrieving(int user, int site) {
		return insertId("INSERT INTO sample_retrieving (app_user_id, site_id, entered_date, sample_retrieve_date) "
				+ "VALUES (" + user + ", " + site + ", now(), CURRENT_DATE - 3) RETURNING id");
	}

	private int sample(String patient, int retrieving, String destinationLab) {
		return insertId("INSERT INTO sample (patient_identifier, collection_date, created_at, lastupdated_at, "
				+ "sample_status_id, sample_type_id, sample_retrieving_id, destination_lab_id, reference_lab_id) VALUES ('"
				+ patient + "', CURRENT_DATE - 3, now(), now(), "
				+ "(SELECT id FROM sample_status WHERE status = 'ON_TRANSIT'), "
				+ "(SELECT id FROM sample_type WHERE name = 'CV'), " + retrieving + ", " + destinationLab + ", "
				+ destinationLab + ") RETURNING id");
	}

	/**
	 * Création par le formulaire, comme dans le navigateur : pour un
	 * superviseur, niveau et type sont verrouillés à l'écran, donc non envoyés.
	 */
	private void createAccount(String login, String role, String level, String type, List<Integer> regions,
			List<Integer> districts) throws Exception {
		MockHttpServletRequestBuilder req = post("/appuser/new").with(as("admin")).with(csrf())
				.param("login", login).param("phoneContact", String.format("07%08d", phone++))
				.param("role", role).param("password", PASSWORD).param("repassword", PASSWORD)
				.param("active", "true");
		if (level != null) {
			req.param("userLevel", level);
		}
		if (type != null) {
			req.param("userType", type);
		}
		regions.forEach(r -> req.param("regions", String.valueOf(r)));
		districts.forEach(d -> req.param("districts", String.valueOf(d)));
		String body = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertTrue(body.contains("Ajout effectué avec succès"), login + " : compte non créé");
	}

	private RequestPostProcessor as(String login) {
		return user(userDetails.loadUserByUsername(login));
	}

	private static int num(Object v) {
		return ((Number) v).intValue();
	}

	private int userId(String login) {
		return insertId("SELECT id FROM app_user WHERE login = '" + login + "'");
	}

	// ------------------------------------------------------------------ lecture par écran

	private String read(String login, MockHttpServletRequestBuilder req) throws Exception {
		return mvc.perform(req.with(as(login))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	private int dashboardTotal(String login, String filter) throws Exception {
		String body = read(login, get("/dashboard/data/funnel" + filter));
		Matcher m = Pattern.compile("\"total\":(\\d+)").matcher(body);
		assertTrue(m.find(), body);
		return Integer.parseInt(m.group(1));
	}

	private String list(String login) throws Exception {
		return read(login, get("/sample/data").param("length", "100"));
	}

	private String export(String login) throws Exception {
		DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		return read(login, get("/sample/csv").param("start", LocalDate.now().minusDays(30).format(f))
				.param("end", LocalDate.now().format(f)));
	}

	private int detailStatus(String login, int sampleId) throws Exception {
		return mvc.perform(get("/sample/" + sampleId + "/details").with(as(login))).andReturn().getResponse()
				.getStatus();
	}

	private String api(String login) throws Exception {
		String role = jdbc.queryForObject("SELECT role FROM app_user WHERE login = '" + login + "'", Map.of(),
				String.class);
		String token = jwtService.generateToken(login, role, (long) userId(login));
		return mvc.perform(get("/api_v2/sync/samples/pull").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** Ce que le compte voit de chaque échantillon, écran par écran. */
	private void seesOnly(String login, List<String> visible) throws Exception {
		String list = list(login), export = export(login), api = api(login);
		for (String patient : List.of(A, B, B_TO_LAB_A)) {
			boolean expected = visible.contains(patient);
			assertEquals(expected, list.contains(patient), login + " liste " + patient);
			assertEquals(expected, export.contains(patient), login + " export " + patient);
			assertEquals(expected, api.contains(patient), login + " API " + patient);
		}
		assertEquals(visible.size(), dashboardTotal(login, ""), login + " tableau de bord");
		assertEquals(visible.contains(A) ? 200 : 404, detailStatus(login, sampleA), login + " fiche A");
		assertEquals(visible.contains(B) ? 200 : 404, detailStatus(login, sampleB), login + " fiche B");
		assertEquals(visible.contains(B_TO_LAB_A) ? 200 : 404, detailStatus(login, sampleBToLabA),
				login + " fiche B déposé en A");
	}

	/** Zones proposées par une liste de filtre de la page (hors option « Tout », valeur 0). */
	private static Set<Integer> options(String html, String selectId) {
		Matcher block = Pattern.compile("id=\"" + selectId + "\"(.*?)</select>", Pattern.DOTALL).matcher(html);
		assertTrue(block.find(), selectId);
		Set<Integer> out = new TreeSet<>();
		Matcher opt = Pattern.compile("<option[^>]*value=\"(\\d+)\"").matcher(block.group(1));
		while (opt.find()) {
			int id = Integer.parseInt(opt.group(1));
			if (id != 0) {
				out.add(id);
			}
		}
		return out;
	}

	private static Set<Integer> ids(String json) {
		Set<Integer> out = new TreeSet<>();
		Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(json);
		while (m.find()) {
			out.add(Integer.parseInt(m.group(1)));
		}
		return out;
	}

	private List<Integer> districtsOf(int region) {
		return jdbc.queryForList("SELECT id FROM district WHERE region_id = " + region + " ORDER BY id", Map.of(),
				Integer.class);
	}

	/**
	 * Action AJAX de l'administration, jeton CSRF en en-tête. Le jeton réel
	 * (cookie XSRF-TOKEN, en-tête X-XSRF-TOKEN) est vérifié en HTTP par
	 * AdminAjaxCsrfIntegrationTest : MockMvc remplace le dépôt de jetons.
	 */
	private MvcResult ajaxPost(String login, String url) throws Exception {
		return mvc.perform(post(url).with(as(login)).with(csrf().asHeader())).andReturn();
	}

	// ------------------------------------------------------------------ périmètre des données

	@Test
	void leFormulaireEnregistreNiveauTypeEtAffectations() {
		Map<String, Object> region = jdbc.queryForMap(
				"SELECT user_level, user_type FROM app_user WHERE login = 'supregion'", Map.of());
		assertEquals("REGION", region.get("user_level"));
		assertEquals("AUTRE", region.get("user_type"));
		Map<String, Object> district = jdbc.queryForMap(
				"SELECT user_level, user_type FROM app_user WHERE login = 'supdistrict'", Map.of());
		assertEquals("DISTRICT", district.get("user_level"));
		assertEquals("AUTRE", district.get("user_type"));
		assertEquals(List.of(regionA), jdbc.queryForList("SELECT region_id FROM app_user_has_region "
				+ "WHERE app_user_id = " + userId("supregion"), Map.of(), Integer.class));
		assertEquals(List.of(districtA), jdbc.queryForList("SELECT district_id FROM app_user_has_district "
				+ "WHERE app_user_id = " + userId("supdistrict"), Map.of(), Integer.class));
	}

	@Test
	void ladministrateurVoitTout() throws Exception {
		seesOnly("admin", List.of(A, B, B_TO_LAB_A));
	}

	@ParameterizedTest
	@ValueSource(strings = { "supregion", "supdistrict" })
	void unSuperviseurNeVoitQueSaZone(String login) throws Exception {
		seesOnly(login, List.of(A));
		// Filtres de l'écran : zone B refusée ; le filtre sur Labo A ne montre
		// pas PAT-B-VERS-A, collecté hors de sa zone.
		assertEquals(0, dashboardTotal(login, "?region=" + regionB));
		assertEquals(1, dashboardTotal(login, "?lab=" + labA));
	}

	@ParameterizedTest
	@ValueSource(strings = { "supvide", "sansaffect" })
	void unCompteSansAffectationExploitableNeVoitRien(String login) throws Exception {
		// supvide : région affectée sans aucun site ; sansaffect : aucune affectation.
		seesOnly(login, List.of());
	}

	@Test
	void laCarteCircuitsDuSuperviseurResteDansSaZone() throws Exception {
		String admin = read("admin", get("/dashboard/data/top-performers"));
		assertTrue(admin.contains("\"circuit\":\"CIRC-ZONE-A\""), admin);
		assertTrue(admin.contains("\"circuit\":\"CIRC-ZONE-B\""), admin);
		for (String login : List.of("supregion", "supdistrict")) {
			String body = read(login, get("/dashboard/data/top-performers"));
			assertTrue(body.contains("\"circuit\":\"CIRC-ZONE-A\""), login + " : " + body);
			assertFalse(body.contains("CIRC-ZONE-B"), login + " : " + body);
			assertFalse(body.contains("\"kind\":\"unattached\""), login + " : " + body);
		}
	}

	@Test
	void laZoneNonRenseigneeNApparaitQuePourUnCompteNational() throws Exception {
		String since = "?startDate=" + LocalDate.now().minusDays(1100);
		// Ligne « Zone non renseignée » : la seule sans identifiant de zone.
		for (String[] level : new String[][] { { "by-region", "region_id" }, { "by-district", "district_id" },
				{ "by-site", "site_id" } }) {
			String url = "/dashboard/data/" + level[0] + since;
			String unknown = "\"" + level[1] + "\":null";
			assertTrue(read("admin", get(url)).contains(unknown), "admin " + level[0]);
			for (String login : List.of("supregion", "supdistrict")) {
				assertFalse(read(login, get(url)).contains(unknown), login + " " + level[0]);
			}
		}
		assertTrue(read("admin", get("/dashboard/data/top-performers" + since)).contains("\"kind\":\"unattached\""));
		assertFalse(read("supregion", get("/dashboard/data/top-performers" + since)).contains("\"kind\":\"unattached\""));
		assertEquals(1, dashboardTotal("supregion", since));
	}

	@Test
	void laSaisieWebDUnEchantillonNExistePlus() throws Exception {
		// POST /sample enregistrait un échantillon à partir des seuls paramètres de
		// la requête, sans collecte : supprimé. Même un administrateur, avec son
		// jeton CSRF, reçoit 404 ou 405, et rien n'est enregistré.
		long before = jdbc.queryForObject("SELECT COUNT(*) FROM sample", Map.of(), Long.class);
		int code = mvc.perform(post("/sample").with(as("admin")).with(csrf())
				.param("patientIdentifier", "PAT-POST-SAMPLE").param("sampleTypeId", "1")
				.param("sampleStatusId", "1")).andReturn().getResponse().getStatus();
		assertTrue(code == 404 || code == 405, "statut " + code);
		assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM sample", Map.of(), Long.class));
	}

	@Test
	void uneFicheDeModificationHorsPerimetreEstRefusee() throws Exception {
		mvc.perform(get("/sample/update/" + sampleB).with(as("supregion"))).andExpect(status().isForbidden());
		mvc.perform(get("/sample/update/999999").with(as("supregion"))).andExpect(status().isForbidden());
		mvc.perform(get("/sample/update/" + sampleA).with(as("supregion"))).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------ listes des filtres

	@ParameterizedTest
	@ValueSource(strings = { "/dashboard", "/sample" })
	void lesListesDeFiltreSeLimitentALaZoneDuSuperviseurRegional(String page) throws Exception {
		String html = read("supregion", get(page));
		assertEquals(Set.of(regionA), options(html, "select_region"), page + " régions");
		assertEquals(new TreeSet<>(districtsOf(regionA)), options(html, "select_district"), page + " districts");
		assertEquals(Set.of(siteA), options(html, "select_site"), page + " sites");
		assertEquals(Set.of(labA), options(html, "select_lab"), page + " labos");
	}

	@Test
	void lesListesDeFiltreDuSuperviseurDeDistrict() throws Exception {
		String html = read("supdistrict", get("/dashboard"));
		assertEquals(Set.of(regionA), options(html, "select_region"));
		assertEquals(Set.of(districtA), options(html, "select_district"));
		assertEquals(Set.of(siteA), options(html, "select_site"));
		assertEquals(Set.of(labA), options(html, "select_lab"));
	}

	@Test
	void unCompteNationalVoitToutLeReferentiel() throws Exception {
		String html = read("admin", get("/dashboard"));
		assertTrue(options(html, "select_region").containsAll(List.of(regionA, regionB, regionC)));
		assertTrue(options(html, "select_site").containsAll(List.of(siteA, siteB)));
		assertTrue(options(html, "select_lab").containsAll(List.of(labA, labB)));
	}

	@Test
	void laCascadeDesFiltresResteDansLaZone() throws Exception {
		List<Integer> both = new ArrayList<>(districtsOf(regionA));
		both.addAll(districtsOf(regionB));
		String regionsParam = regionA + "," + regionB;
		assertEquals(new TreeSet<>(districtsOf(regionA)),
				ids(read("supregion", get("/district/names_by_regions").param("regions", regionsParam))));
		assertEquals(Set.of(districtA),
				ids(read("supdistrict", get("/district/names_by_regions").param("regions", regionsParam))));
		assertEquals(Set.of(siteA), ids(read("supregion",
				get("/site/names_by_districts").param("districts", districtA + "," + districtB))));
		assertEquals(new TreeSet<>(both),
				ids(read("admin", get("/district/names_by_regions").param("regions", regionsParam))));
		assertTrue(read("supvide", get("/site/names_by_districts").param("districts", districtA + "," + districtB))
				.replace(" ", "").equals("[]"));
	}

	// ------------------------------------------------------------------ administration

	@Test
	void lesFormulairesDeCompteSAffichent() throws Exception {
		assertTrue(read("admin", get("/appuser/new")).contains("Régions affectées"));
		String edit = read("admin", get("/appuser/update/" + userId("supregion")));
		assertTrue(edit.contains("Affecter les zones"));
		assertFalse(edit.contains("/appuser/delete/"), "pas de suppression de compte");
	}

	@Test
	void lEnTeteCommunChargeLeJetonCsrfUneSeuleFois() throws Exception {
		for (String page : List.of("/appuser", "/region", "/district", "/site", "/lab", "/circuit", "/samplestatus",
				"/sampletype", "/samplerejectiontype", "/dashboard", "/sample")) {
			String html = read("admin", get(page));
			// Adresse versionnée par empreinte (VersionResourceResolver) : /js/scripts-<empreinte>.js.
			assertEquals(1, html.split("/js/scripts(-[0-9a-f]+)?\\.js", -1).length - 1, page);
		}
	}

	@Test
	void sansJetonCsrfUneActionEstRefusee() throws Exception {
		mvc.perform(post("/appuser/" + userId("sansaffect") + "/toggle-lock").with(as("admin")))
				.andExpect(status().isForbidden());
	}

	@Test
	void ladministrateurDesactiveEtVerrouilleUnCompte() throws Exception {
		createAccount("adesactiver", "USER", "REGION_LEVEL", "OTHER", List.of(), List.of());
		int id = userId("adesactiver");
		assertEquals(200, ajaxPost("admin", "/appuser/" + id + "/toggle-active").getResponse().getStatus());
		assertEquals(200, ajaxPost("admin", "/appuser/" + id + "/toggle-lock").getResponse().getStatus());
		Map<String, Object> row = jdbc.queryForMap("SELECT is_active, is_locked FROM app_user WHERE id = " + id,
				Map.of());
		assertEquals(Boolean.FALSE, row.get("is_active"));
		assertEquals(Boolean.TRUE, row.get("is_locked"));
	}

	@Test
	void ladministrateurDesactiveUnElementDuMaillage() throws Exception {
		// Comme mesh-actions.js : formulaire POST avec le champ _csrf.
		int siteD = insertId("INSERT INTO site (name, dhis_code, district_id) VALUES ('Site D', 'SITE-D', "
				+ districtB + ") RETURNING id");
		mvc.perform(post("/site/deactivate/" + siteD).with(as("admin")).with(csrf()))
				.andExpect(status().is3xxRedirection());
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT is_active FROM site WHERE id = " + siteD, Map.of(),
				Boolean.class));
	}

	@Test
	void unSuperviseurNeDesactivePasDeCompte() throws Exception {
		int id = userId("sansaffect");
		assertEquals(403, ajaxPost("supregion", "/appuser/" + id + "/toggle-active").getResponse().getStatus());
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT is_active FROM app_user WHERE id = " + id, Map.of(),
				Boolean.class));
	}

	@Test
	void leMotDePasseTemporairePermetDeSeConnecterPuisImposeLeChangement() throws Exception {
		createAccount("areinit", "USER", "REGION_LEVEL", "OTHER", List.of(), List.of());
		int id = userId("areinit");
		MvcResult reset = ajaxPost("admin", "/appuser/" + id + "/reset-password");
		assertEquals(200, reset.getResponse().getStatus());
		Matcher m = Pattern.compile("\"temporaryPassword\":\"([^\"]+)\"").matcher(reset.getResponse()
				.getContentAsString());
		assertTrue(m.find());
		String temporary = m.group(1);

		// Connexion acceptée avec le mot de passe temporaire.
		MvcResult login = mvc.perform(formLogin("/login").user("areinit").password(temporary))
				.andExpect(status().is3xxRedirection()).andReturn();
		assertFalse(String.valueOf(login.getResponse().getRedirectedUrl()).contains("error"),
				"connexion refusée : " + login.getResponse().getRedirectedUrl());
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertNotNull(session);

		// Toute page renvoie vers le changement de mot de passe.
		assertEquals(PasswordChangeInterceptor.CHANGE_PATH,
				mvc.perform(get("/dashboard").session(session)).andExpect(status().is3xxRedirection()).andReturn()
						.getResponse().getRedirectedUrl());
		assertTrue(mvc.perform(get(PasswordChangeInterceptor.CHANGE_PATH).session(session))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
				.contains("Votre mot de passe a été réinitialisé"));

		// Changement : l'accès est rendu et l'indicateur levé.
		String chosen = "Nouveau@2026";
		mvc.perform(post(PasswordChangeInterceptor.CHANGE_PATH).session(session).with(csrf())
				.param("userId", String.valueOf(id)).param("oldPassword", temporary).param("password", chosen)
				.param("repassword", chosen)).andExpect(status().isOk());
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT password_reset FROM app_user WHERE id = " + id,
				Map.of(), Boolean.class));
		// La session en cours reste ouverte : seules les autres sont fermées.
		mvc.perform(get("/dashboard").session(session)).andExpect(status().isOk());
	}
}
