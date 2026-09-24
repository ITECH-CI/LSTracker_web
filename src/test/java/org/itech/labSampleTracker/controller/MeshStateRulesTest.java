package org.itech.labSampleTracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class MeshStateRulesTest {

	private static final Set<String> TYPES = Set.of("region", "district", "site", "lab", "circuit");

	@Test
	void everyMeshTypeHasADeletionGuardAndALabel() {
		assertEquals(TYPES, MeshStateController.USAGE.keySet());
		assertEquals(TYPES, MeshStateController.LABELS.keySet());
	}

	@Test
	void parentsCheckActiveChildrenAndChildrenCheckTheirParent() {
		assertEquals(Set.of("region", "district"), MeshStateController.ACTIVE_CHILDREN.keySet());
		assertEquals(Set.of("district", "site", "lab"), MeshStateController.INACTIVE_PARENT.keySet());
		MeshStateController.ACTIVE_CHILDREN.values().forEach(sql -> assertTrue(sql.contains("is_active")));
	}

	@Test
	void participleAgreesWithRegion() {
		assertEquals("Cette région est désactivée",
				MeshStateController.LABELS.get("region") + " est " + MeshStateController.agree("region", "désactivé"));
		assertEquals("Ce site est désactivé",
				MeshStateController.LABELS.get("site") + " est " + MeshStateController.agree("site", "désactivé"));
	}
}
