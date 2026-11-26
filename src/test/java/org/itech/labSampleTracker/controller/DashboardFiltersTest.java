package org.itech.labSampleTracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.enums.LabType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Filtres du tableau de bord : liaison des paramètres, option « Tout », types de labo. */
class DashboardFiltersTest {

	@RestController
	static class EchoController {
		@GetMapping("/echo")
		String echo(DashboardQuery q) {
			return q.start() + "|" + q.end() + "|" + q.regionId() + "|" + q.labId() + "|" + q.labTypeCode() + "|"
					+ q.sampleTypeId();
		}
	}

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new EchoController()).build();

	@Test
	void lesParametresDeLaPageSontLiesALaRequete() throws Exception {
		mvc.perform(get("/echo").param("startDate", "2026-09-01").param("endDate", "2026-09-30")
				.param("region", "3").param("lab", "7").param("lab_type", "CAT").param("sample_type", "2"))
				.andExpect(content().string("2026-09-01|2026-09-30|3|7|CAT|2"));
	}

	@Test
	void laValeurZeroToutNeFiltrePas() throws Exception {
		mvc.perform(get("/echo").param("startDate", "2026-09-01").param("endDate", "2026-09-30")
				.param("region", "0").param("lab", "0").param("lab_type", "0").param("sample_type", "0"))
				.andExpect(content().string("2026-09-01|2026-09-30|null|null|null|null"));
	}

	@Test
	void periodeParDefautDeuxAns() {
		DashboardQuery q = new DashboardQuery(null, null, null, null, null, null, " ", null);
		assertEquals(LocalDate.now().minusYears(2), q.start());
		assertEquals(LocalDate.now(), q.end());
		assertNull(q.labTypeCode());
	}

	@Test
	void typesDeLaboDistinctsAvecLibelles() {
		List<Map<String, String>> options = DashboardController.labTypeOptions(List.of(
				Map.of("id", 1, "labType", "PLATEFORME"), Map.of("id", 2, "labType", "DISTRICT"),
				Map.of("id", 3, "labType", "DISTRICT"), Map.of("id", 4, "labType", "CAT"), Map.of("id", 5)));
		assertEquals(List.of(Map.of("code", "CAT", "label", "CAT"),
				Map.of("code", "DISTRICT", "label", "Laboratoire de district"),
				Map.of("code", "PLATEFORME", "label", "Plateforme de biologie moléculaire")), options);
	}

	@Test
	void circuitsLesPlusActifsPuisNonRattache() {
		java.util.Map<String, Object> none = new java.util.HashMap<>();
		none.put("circuit_id", null);
		none.put("collected", 40);
		List<Map<String, Object>> rows = List.of(none, Map.of("circuit_id", 1, "collected", 30),
				Map.of("circuit_id", 2, "collected", 20), Map.of("circuit_id", 3, "collected", 10));
		List<Map<String, Object>> top = DashboardController.topCircuits(rows, 2);
		assertEquals(3, top.size());
		assertEquals(1, top.get(0).get("circuit_id"));
		assertEquals(2, top.get(1).get("circuit_id"));
		assertNull(top.get(2).get("circuit_id"));
		assertEquals(2, DashboardController.topCircuits(rows.subList(1, 4), 2).size());
	}

	@Test
	void typeDeLaboDeLaListeDesEchantillons() {
		assertNull(SampleController.labTypeCode("0"));
		assertNull(SampleController.labTypeCode(" "));
		assertEquals("CAT", SampleController.labTypeCode(" CAT "));
	}

	@Test
	void libellesDesTypesDeLabo() {
		assertEquals("Laboratoire régional", LabType.label("REGION"));
		assertEquals("Hub", LabType.label("HUB"));
		assertEquals("Laboratoire relais", LabType.label("RELAIS"));
		assertEquals("AUTRE", LabType.label("AUTRE"));
		assertNull(LabType.label(null));
	}
}
