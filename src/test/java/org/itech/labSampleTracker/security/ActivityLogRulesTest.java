package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.security.ActivityLogService.Entry;
import org.junit.jupiter.api.Test;

class ActivityLogRulesTest {

	private static final String[] NAMES = { "sampleStatusId", "patientIdentifier", "lastupdatedAt", "version",
			"labNumber", "sampleType" };

	@Test
	void diffKeepsOnlyRealChangesWithPreviousValue() {
		Object[] before = { 3, "P-001", "x", 1L, null, new Object() };
		Object[] after = { 5, "P-001", "y", 2L, "LAB-9", new Object() };
		Map<String, Object[]> d = ActivityLogService.diff(NAMES, before, after);
		assertEquals(List.of("sampleStatusId", "labNumber"), List.copyOf(d.keySet()));
		assertArrayEquals(new Object[] { 3, 5 }, d.get("sampleStatusId"));
		assertArrayEquals(new Object[] { null, "LAB-9" }, d.get("labNumber"));
	}

	@Test
	void sensitiveValuesAreNeverStored() {
		Map<String, Object[]> d = ActivityLogService.diff(NAMES, new Object[] { 3, "P-001", null, null, null, null },
				new Object[] { 3, "P-002", null, null, null, null });
		assertArrayEquals(new Object[] { "***", "***" }, d.get("patientIdentifier"));
		Map<String, Object[]> created = ActivityLogService.snapshot(new String[] { "login", "password" },
				new Object[] { "agent", "$2a$10$hash" }, true);
		assertArrayEquals(new Object[] { null, "***" }, created.get("password"));
		assertArrayEquals(new Object[] { null, "agent" }, created.get("login"));
	}

	@Test
	void onlyLastLoginChangedMeansNothingToLog() {
		Map<String, Object[]> d = ActivityLogService.diff(new String[] { "lastLogin", "login" },
				new Object[] { new java.util.Date(0), "agent" }, new Object[] { new java.util.Date(), "agent" });
		assertTrue(d.isEmpty());
	}

	private static Entry perimeter(String action, int userId, int siteId) {
		boolean created = "CREATE".equals(action);
		Map<String, Object[]> changes = new java.util.LinkedHashMap<>();
		changes.put("appUserId", created ? new Object[] { null, userId } : new Object[] { userId, null });
		changes.put("siteId", created ? new Object[] { null, siteId } : new Object[] { siteId, null });
		return new Entry(action, "AppUserHasSite", String.valueOf(siteId * 10 + userId), changes, null, "admin",
				"web", null);
	}

	@Test
	void unchangedPerimeterSavedAgainLeavesNoTrace() {
		// Réenregistrement : tout est retiré puis réajouté ; seul le site 30 est nouveau.
		List<Entry> netted = ActivityLogService.net(List.of(
				perimeter("DELETE", 5, 10), perimeter("DELETE", 5, 20),
				perimeter("CREATE", 5, 10), perimeter("CREATE", 5, 20), perimeter("CREATE", 5, 30)));
		assertEquals(1, netted.size());
		assertEquals("CREATE", netted.get(0).action());
		assertArrayEquals(new Object[] { null, 30 }, netted.get(0).changes().get("siteId"));
	}

	@Test
	void technicalEntitiesAreNotTracked() {
		assertFalse(ActivityLogService.tracked("OeSampleSync"));
		assertFalse(ActivityLogService.tracked("RefreshToken"));
		assertTrue(ActivityLogService.tracked("Sample"));
		assertTrue(ActivityLogService.tracked("AppUserHasSite"));
	}
}
