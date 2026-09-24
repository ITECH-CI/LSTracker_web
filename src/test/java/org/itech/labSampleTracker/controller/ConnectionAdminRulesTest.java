package org.itech.labSampleTracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ConnectionAdminRulesTest {

	@Test
	void periodIsWhitelisted() {
		assertEquals(30, ConnectionAdminController.days(null));
		assertEquals(7, ConnectionAdminController.days(7));
		assertEquals(30, ConnectionAdminController.days(5000));
		assertEquals(30, ConnectionAdminController.days(-1));
	}

	@Test
	void periodIncludesToday() {
		assertEquals(LocalDate.now(), ConnectionAdminController.start(1));
		assertEquals(LocalDate.now().minusDays(6), ConnectionAdminController.start(7));
	}

	@Test
	void csvQuotesAndNeutralisesFormulas() {
		assertEquals("\"a;b\"", ConnectionAdminController.csv("a;b"));
		assertEquals("\"dit \"\"non\"\"\"", ConnectionAdminController.csv("dit \"non\""));
		assertEquals("\"'=HYPERLINK(1)\"", ConnectionAdminController.csv("=HYPERLINK(1)"));
		assertEquals("\"'@cmd\"", ConnectionAdminController.csv("@cmd"));
		assertEquals("", ConnectionAdminController.csv(null));
	}
}
