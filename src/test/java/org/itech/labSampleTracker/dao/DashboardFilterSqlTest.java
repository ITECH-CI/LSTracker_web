package org.itech.labSampleTracker.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.dto.DashboardFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Chaque requête du tableau de bord, des rapports et du contrôle de cohérence
 * applique le type de labo, le type d'échantillon et le périmètre de
 * l'utilisateur, en plus des filtres géographiques.
 */
class DashboardFilterSqlTest {

	private static final DashboardFilter F = new DashboardFilter(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
			1, 2, 3, 4, "CAT", 5, List.of(3, 8));

	private static NamedParameterJdbcTemplate jdbc() {
		NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
		when(jdbc.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(Map.of());
		when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
		return jdbc;
	}

	private static void assertAllFiltered(NamedParameterJdbcTemplate jdbc, int expectedQueries) {
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
		verify(jdbc, atLeastOnce()).queryForList(sql.capture(), params.capture());
		List<String> all = new java.util.ArrayList<>(sql.getAllValues());
		List<SqlParameterSource> allParams = new java.util.ArrayList<>(params.getAllValues());
		ArgumentCaptor<String> mapSql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<SqlParameterSource> mapParams = ArgumentCaptor.forClass(SqlParameterSource.class);
		try {
			verify(jdbc, atLeastOnce()).queryForMap(mapSql.capture(), mapParams.capture());
			all.addAll(mapSql.getAllValues());
			allParams.addAll(mapParams.getAllValues());
		} catch (AssertionError none) {
			// dépôt sans requête à une ligne
		}
		assertEquals(expectedQueries, all.size());
		for (String q : all) {
			assertTrue(q.contains(":labType"), q);
			assertTrue(q.contains(":sampleTypeId"), q);
			assertTrue(q.contains(":accessibleSiteIds"), q);
		}
		for (SqlParameterSource p : allParams) {
			assertEquals("CAT", p.getValue("labType"));
			assertEquals(5, p.getValue("sampleTypeId"));
			assertEquals(Boolean.TRUE, p.getValue("accessibleSiteIdsActive"));
		}
	}

	@Test
	void requetesAvanceesDuTableauDeBord() {
		NamedParameterJdbcTemplate jdbc = jdbc();
		DashboardAdvancedRepository repo = new DashboardAdvancedRepository(jdbc);
		repo.funnel(F);
		repo.stuckSamples(F, 7, 50);
		repo.statsByRegion(F);
		repo.statsByDistrict(F);
		repo.statsBySite(F);
		repo.topRejectionSites(F, 5, 5);
		repo.slowestLabs(F, 5, 5);
		repo.circuits(F);
		repo.coverage(F);
		repo.typeBreakdown(F);
		repo.zoneByType(F);
		repo.zoneNonConformReasons(F);
		assertAllFiltered(jdbc, 12);
	}

	@Test
	void seriesEtDureesDuTableauDeBord() {
		NamedParameterJdbcTemplate jdbc = jdbc();
		DashboardNativeRepository repo = new DashboardNativeRepository(jdbc);
		repo.summary(F);
		repo.tsCollected(F);
		repo.tsDeposited(F);
		repo.tsAnalysed(F);
		repo.tsDelivered(F);
		repo.stepDurationsDays(F);
		repo.stepMedians(F);
		assertAllFiltered(jdbc, 7);
	}

	@Test
	void requetesDesRapports() {
		NamedParameterJdbcTemplate jdbc = jdbc();
		ReportJdbcRepository repo = new ReportJdbcRepository(jdbc);
		repo.collectedByType(F, null);
		repo.receivedByTypeAndLabKind(F, null);
		repo.acceptedByType(F, null);
		repo.resultReadyByType(F, null);
		repo.resultCollectedByType(F, null);
		repo.resultDeliveredByType(F, null);
		repo.rejectedByType(F, null);
		repo.failedByType(F, null);
		repo.tatMedianDaysByType(F, null);
		assertAllFiltered(jdbc, 9);
	}

	@Test
	void sansFiltreLesParametresSontNeutres() {
		MapSqlParameterSource p = new DashboardFilter(null, null, null, null, null, null, " ", null, List.of())
				.params();
		assertNull(p.getValue("labType"));
		assertNull(p.getValue("sampleTypeId"));
		assertFalse((Boolean) p.getValue("accessibleSiteIdsActive"));
		assertEquals(List.of(-1), p.getValue("accessibleSiteIds"));
	}
}
