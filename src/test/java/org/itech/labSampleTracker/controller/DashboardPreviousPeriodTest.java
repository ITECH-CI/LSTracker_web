package org.itech.labSampleTracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Période de comparaison des cartes du tableau de bord (observation 1.5) :
 * période calendaire en cours comparée à la même portion de la précédente.
 */
class DashboardPreviousPeriodTest {

	private static void assertPrev(String period, LocalDate start, LocalDate end, LocalDate expStart,
			LocalDate expEnd) {
		LocalDate[] prev = DashboardController.previousPeriod(start, end, period);
		assertEquals(expStart, prev[0], "début");
		assertEquals(expEnd, prev[1], "fin");
	}

	@Test
	void aujourdHuiComparéAHier() {
		LocalDate d = LocalDate.of(2026, 9, 23);
		assertPrev("today", d, d, d.minusDays(1), d.minusDays(1));
	}

	@Test
	void semaineEnCoursComparéeAuxMêmesJoursDeLaPrécédente() {
		// lundi 21/09 → mercredi 23/09  ⇒  lundi 14/09 → mercredi 16/09
		assertPrev("week", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 23),
				LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16));
	}

	@Test
	void moisEnCoursComparéAuMoisPrécédentALaMêmeDate() {
		assertPrev("month", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 23),
				LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 23));
	}

	@Test
	void finDeMoisRameneeAuDernierJourDuMoisPrécédent() {
		// 31 mars → 28 février (2026 n'est pas bissextile)
		assertPrev("month", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
				LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
	}

	@Test
	void trimestreSemestreEtAnnée() {
		assertPrev("quarter", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 23),
				LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 23));
		assertPrev("semester", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 23),
				LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 23));
		assertPrev("year", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 23),
				LocalDate.of(2025, 1, 1), LocalDate.of(2025, 9, 23));
	}

	@Test
	void datesSaisiesALaMainDuréeÉgaleJusteAvant() {
		// 10 → 19/09 (9 jours d'écart) ⇒ 31/08 → 09/09
		assertPrev(null, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19),
				LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 9));
		assertPrev("inconnu", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19),
				LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 9));
	}
}
