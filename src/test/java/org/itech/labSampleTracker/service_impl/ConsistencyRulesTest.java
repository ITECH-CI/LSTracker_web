package org.itech.labSampleTracker.service_impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConsistencyRulesTest {

	@Test
	void sumsWhateverNumericTypeTheDriverReturns() {
		assertEquals(7, ConsistencyCheckService.sum(List.of(Map.of("total", 3L), Map.of("total", BigInteger.valueOf(4)),
				Map.of("other", 9)), "total"));
		assertEquals(0, ConsistencyCheckService.num(null));
	}

	@Test
	void aCheckIsOkOnlyWhenValuesAreIdentical() {
		assertTrue(new ConsistencyCheckService.Check("Collectés", 120, "Répartition", 120).ok());
		assertFalse(new ConsistencyCheckService.Check("Analysés", 991, "Rapport PDF", 16).ok());
	}
}
