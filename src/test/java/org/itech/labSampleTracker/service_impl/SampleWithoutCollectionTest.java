package org.itech.labSampleTracker.service_impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.itech.labSampleTracker.dao.DashboardAdvancedRepository;
import org.itech.labSampleTracker.dao.DashboardNativeRepository;
import org.itech.labSampleTracker.dao.ReportJdbcRepository;
import org.itech.labSampleTracker.dao.SampleRepository;
import org.itech.labSampleTracker.dto.DashboardFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Un échantillon sans collecte (sample_retrieving_id NULL) reste compté
 * partout : cartes, répartitions (ligne « Zone non renseignée »), étapes par
 * type, rapports. À défaut de collecte, le site demandeur donne la zone. Avec
 * un filtre de zone ou un périmètre restreint, l'échantillon sans zone est
 * exclu. Base PostgreSQL réelle, schéma créé par le changelog Liquibase.
 */
@Testcontainers(disabledWithoutDocker = true)
class SampleWithoutCollectionTest {

	@Container
	static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:14");

	private static final LocalDate START = LocalDate.of(2025, 9, 1);
	private static final LocalDate END = LocalDate.of(2025, 9, 30);

	private static NamedParameterJdbcTemplate jdbc;
	private static DashboardAdvancedRepository dashboard;
	private static DashboardServiceImpl series;
	private static ConsistencyCheckService checks;
	private static int regionA, regionB, siteA, siteB;

	@BeforeAll
	static void schemaAndData() throws Exception {
		try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
			c.createStatement().execute("CREATE SCHEMA sample_tracker");
		}
		String url = PG.getJdbcUrl() + "&currentSchema=sample_tracker";
		try (Connection c = DriverManager.getConnection(url, PG.getUsername(), PG.getPassword())) {
			Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
			db.setDefaultSchemaName("sample_tracker");
			new Liquibase("db/changelog/master.xml", new ClassLoaderResourceAccessor(), db).update("");
		}
		jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, PG.getUsername(), PG.getPassword()));
		dashboard = new DashboardAdvancedRepository(jdbc);
		series = new DashboardServiceImpl(new DashboardNativeRepository(jdbc));
		checks = new ConsistencyCheckService(dashboard, series, new ReportJdbcRepository(jdbc));

		// Deux districts de deux régions différentes (référentiel chargé par Liquibase).
		List<Map<String, Object>> districts = jdbc.queryForList(
				"SELECT DISTINCT ON (region_id) id, region_id FROM district ORDER BY region_id, id LIMIT 2", Map.of());
		int districtA = num(districts.get(0).get("id"));
		regionA = num(districts.get(0).get("region_id"));
		int districtB = num(districts.get(1).get("id"));
		regionB = num(districts.get(1).get("region_id"));
		siteA = insertSite("Site A", districtA);
		siteB = insertSite("Site B", districtB);
		int user = jdbc.queryForObject("SELECT id FROM app_user WHERE login = 'admin'", Map.of(), Integer.class);
		int retrieving = jdbc.queryForObject("INSERT INTO sample_retrieving (app_user_id, site_id, entered_date, "
				+ "sample_retrieve_date) VALUES (" + user + ", " + siteA + ", now(), '2025-09-25') RETURNING id",
				Map.of(), Integer.class);

		insertSample("P-COLLECTE", "'2025-09-25'", String.valueOf(retrieving), "NULL");
		insertSample("P-SANS-ZONE", "'2025-09-26'", "NULL", "NULL");
		insertSample("P-DEMANDEUR", "'2025-09-27'", "NULL", String.valueOf(siteB));
		insertSample("P-HORS-PERIODE", "'2025-10-02'", "NULL", "NULL");
	}

	private static int insertSite(String name, int district) {
		return jdbc.queryForObject("INSERT INTO site (name, dhis_code, district_id) VALUES ('" + name + "', '"
				+ name.replace(' ', '-') + "', " + district + ") RETURNING id", Map.of(), Integer.class);
	}

	private static void insertSample(String patient, String collected, String retrieving, String requesterSite) {
		jdbc.update("INSERT INTO sample (patient_identifier, collection_date, created_at, lastupdated_at, "
				+ "sample_status_id, sample_type_id, sample_retrieving_id, requester_site_id) VALUES ('" + patient
				+ "', " + collected + ", now(), now(), "
				+ "(SELECT id FROM sample_status WHERE status = 'ON_TRANSIT'), "
				+ "(SELECT id FROM sample_type WHERE name = 'CV'), " + retrieving + ", " + requesterSite + ")",
				Map.of());
	}

	private static DashboardFilter filter(Integer region, List<Integer> accessibleSites) {
		return new DashboardFilter(START, END, region, null, null, null, null, null, accessibleSites);
	}

	private static int num(Object v) {
		return ((Number) v).intValue();
	}

	private static long total(List<Map<String, Object>> rows) {
		return ConsistencyCheckService.sum(rows, "total");
	}

	private static List<Map<String, Object>> unknownRows(List<Map<String, Object>> rows, String idColumn) {
		return rows.stream().filter(r -> r.get(idColumn) == null).toList();
	}

	@Test
	void sansFiltreLesTroisEchantillonsSontComptesPartout() {
		DashboardFilter f = filter(null, null);
		assertEquals(3, num(dashboard.funnel(f).get("total")));
		for (String[] level : new String[][] { { "region_id", "region" }, { "district_id", "district" },
				{ "site_id", "site" } }) {
			List<Map<String, Object>> rows = switch (level[0]) {
			case "region_id" -> dashboard.statsByRegion(f);
			case "district_id" -> dashboard.statsByDistrict(f);
			default -> dashboard.statsBySite(f);
			};
			assertEquals(3, total(rows), level[0]);
			List<Map<String, Object>> unknown = unknownRows(rows, level[0]);
			assertEquals(1, unknown.size(), level[0]);
			assertEquals(1, num(unknown.get(0).get("total")), level[0]);
			assertEquals(DashboardAdvancedRepository.UNKNOWN_ZONE, unknown.get(0).get(level[1]), level[0]);
			// « Zone non renseignée » en dernier.
			assertTrue(rows.get(rows.size() - 1).get(level[0]) == null, level[0]);
		}
		assertEquals(3, series.stepsByType(f).values().stream().mapToInt(m -> m.get("SAMPLE_COLLECTED")).sum());
	}

	@Test
	void leSiteDemandeurDonneLaZoneSansCollecte() {
		Map<String, Object> rowB = dashboard.statsBySite(filter(null, null)).stream()
				.filter(r -> Objects.equals(r.get("site_id"), siteB)).findFirst().orElseThrow();
		assertEquals(1, num(rowB.get("total")));
		assertEquals(1, num(dashboard.funnel(filter(regionB, null)).get("total")));
	}

	@Test
	void controleDeCoherenceAuVert() {
		for (DashboardFilter f : List.of(filter(null, null), filter(regionA, null), filter(regionB, null),
				filter(null, List.of(siteA)))) {
			List<ConsistencyCheckService.Check> all = checks.indicators(f);
			assertEquals(14, all.size());
			List<ConsistencyCheckService.Check> failed = all.stream().filter(c -> !c.ok()).toList();
			assertTrue(failed.isEmpty(), f + " : " + failed);
			assertTrue(checks.hierarchy(f).isEmpty(), f + " : " + checks.hierarchy(f));
		}
	}

	/** Comptage de la liste des échantillons, tel que l'écran l'exécute (période de collecte seule). */
	private static long sampleListCount(Integer region) throws Exception {
		Query q = java.util.Arrays.stream(SampleRepository.class.getMethods())
				.filter(m -> m.getName().equals("getSampleDetailsAdvanced")).findFirst().orElseThrow()
				.getAnnotation(Query.class);
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("regionId", region).addValue("districtId", null).addValue("siteId", null)
				.addValue("labId", null).addValue("labType", null)
				.addValue("startDate", java.sql.Date.valueOf(START)).addValue("endDate", java.sql.Date.valueOf(END))
				.addValue("statusIds", List.of(-1)).addValue("statusIdsActive", false)
				.addValue("typeIds", List.of(-1)).addValue("typeIdsActive", false)
				.addValue("natures", List.of("__none__")).addValue("naturesActive", false)
				.addValue("accessibleSiteIds", List.of(-1)).addValue("accessibleSiteIdsActive", false)
				.addValue("searchText", null).addValue("onlyRejected", false)
				.addValue("stuckDays", 0).addValue("stuckActive", false);
		return jdbc.queryForObject(q.countQuery(), p, Long.class);
	}

	@Test
	void laListeDesEchantillonsLesMontreAussi() throws Exception {
		assertEquals(3, sampleListCount(null));
		assertEquals(1, sampleListCount(regionB));
	}

	@Test
	void avecUnFiltreDeZoneOuUnPerimetreLEchantillonSansZoneEstExclu() {
		for (DashboardFilter f : List.of(filter(regionA, null), filter(null, List.of(siteA)))) {
			assertEquals(1, num(dashboard.funnel(f).get("total")), f.toString());
			List<Map<String, Object>> regions = dashboard.statsByRegion(f);
			assertEquals(1, total(regions), f.toString());
			assertTrue(unknownRows(regions, "region_id").isEmpty(), f.toString());
			assertEquals(1, series.stepsByType(f).values().stream().mapToInt(m -> m.get("SAMPLE_COLLECTED")).sum());
		}
	}
}
