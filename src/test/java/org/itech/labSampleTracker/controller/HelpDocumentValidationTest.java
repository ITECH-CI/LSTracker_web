package org.itech.labSampleTracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.itech.labSampleTracker.enums.HelpDocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Contrôles du téléversement des manuels d'aide (observation 3.2). */
class HelpDocumentValidationTest {

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("file", name, "application/pdf", content);
	}

	@Test
	void unVraiPdfEstAccepte() {
		byte[] pdf = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
		assertNull(HelpDocumentController.validate(HelpDocumentType.USER_MANUAL, file("manuel.pdf", pdf)));
	}

	@Test
	void unFichierRenommeEnPdfEstRefuse() {
		// Extension et type MIME ne suffisent pas : on vérifie la signature %PDF-.
		byte[] docx = "PK\u0003\u0004word/document.xml".getBytes(StandardCharsets.ISO_8859_1);
		assertEquals("Le fichier n'est pas un PDF.",
				HelpDocumentController.validate(HelpDocumentType.USER_MANUAL, file("manuel.pdf", docx)));
	}

	@Test
	void fichierVideTypeInconnuEtTailleSontControles() {
		assertEquals("Aucun fichier sélectionné.",
				HelpDocumentController.validate(HelpDocumentType.USER_MANUAL, file("vide.pdf", new byte[0])));
		assertEquals("Type de document inconnu.", HelpDocumentController.validate(null, file("a.pdf", new byte[] { 1 })));
		byte[] big = new byte[(int) HelpDocumentController.MAX_SIZE_BYTES + 1];
		System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 5);
		assertTrue(HelpDocumentController.validate(HelpDocumentType.USER_MANUAL, file("gros.pdf", big))
				.startsWith("Fichier trop volumineux"));
	}

	@Test
	void leNomDeFichierEstNettoye() {
		assertEquals("manuel v2.pdf", HelpDocumentController.safeFileName("C:\\Users\\x\\manuel v2.pdf", HelpDocumentType.USER_MANUAL));
		assertEquals("guide.pdf", HelpDocumentController.safeFileName("../../etc/guide.pdf", HelpDocumentType.USER_MANUAL));
		assertEquals("manuel-procedure.pdf", HelpDocumentController.safeFileName("  ", HelpDocumentType.PROCEDURE_MANUAL));
		assertEquals("a.pdf", HelpDocumentController.safeFileName("a\".pdf", HelpDocumentType.USER_MANUAL));
	}

	@Test
	void lesSlugsDesMenusCorrespondentAuxTypes() {
		assertEquals(HelpDocumentType.USER_MANUAL, HelpDocumentType.fromSlug("manuel-utilisateur").orElseThrow());
		assertEquals(HelpDocumentType.PROCEDURE_MANUAL, HelpDocumentType.fromSlug("manuel-procedure").orElseThrow());
		assertTrue(HelpDocumentType.fromSlug("../secret").isEmpty());
	}
}
