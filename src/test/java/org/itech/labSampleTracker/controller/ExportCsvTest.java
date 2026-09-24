package org.itech.labSampleTracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.helper.ExportUtils;
import org.junit.jupiter.api.Test;

class ExportCsvTest {

	@Test
	void preambleComesFirstThenBlankLineThenTable() throws Exception {
		byte[] bytes = ExportUtils.writeCSVData(List.of("LSTracker — Export des échantillons (1 lignes)",
				"Période : du 01/09/2026 au 24/09/2026 (date de collecte)",
				"Périmètre : Région A › District B — accès national"),
				List.of(Map.of("region", "Région A", "site", "Site C"))).readAllBytes();
		assertEquals((byte) 0xEF, bytes[0]); // BOM UTF-8 pour Excel
		String[] lines = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).split("\r\n");
		assertEquals("LSTracker — Export des échantillons (1 lignes)", lines[0]);
		assertTrue(lines[2].startsWith("Périmètre : Région A › District B"));
		assertEquals("", lines[3]);
		assertTrue(lines[4].startsWith("REGION,DISTRICT"));
		assertTrue(lines[5].startsWith("Région A,"));
	}
}
