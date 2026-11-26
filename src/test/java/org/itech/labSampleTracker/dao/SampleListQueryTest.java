package org.itech.labSampleTracker.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * La liste des échantillons et son comptage appliquent les mêmes filtres que
 * la ligne du tableau de répartition : type de labo, période de collecte
 * jour de fin compris.
 */
class SampleListQueryTest {

	private static Query advancedQuery() {
		Method m = Arrays.stream(SampleRepository.class.getMethods())
				.filter(x -> x.getName().equals("getSampleDetailsAdvanced")).findFirst().orElseThrow();
		return m.getAnnotation(Query.class);
	}

	@Test
	void listeEtComptageAppliquentLesMemesFiltres() {
		Query q = advancedQuery();
		for (String sql : new String[] { q.value(), q.countQuery() }) {
			assertTrue(sql.contains("flt.lab_type = CAST(:labType AS VARCHAR)"), sql);
			assertTrue(sql.contains("s.collection_date < CAST(:endDate AS DATE) + 1"), sql);
			assertTrue(sql.contains("(:naturesActive = FALSE OR s.sample_nature IN (:natures))"), sql);
		}
	}
}
