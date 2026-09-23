package org.itech.labSampleTracker.dao;

import java.util.Date;
import java.util.List;

import org.itech.labSampleTracker.entities.HelpDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HelpDocumentRepository extends JpaRepository<HelpDocument, String> {

	/** Métadonnées sans le contenu (liste de l'administration). */
	interface Info {
		String getCode();

		String getFileName();

		Long getSizeBytes();

		Date getUploadedAt();

		String getUploadedBy();
	}

	@Query("SELECT d.code AS code, d.fileName AS fileName, d.sizeBytes AS sizeBytes, "
			+ "d.uploadedAt AS uploadedAt, d.uploadedBy AS uploadedBy FROM HelpDocument d")
	List<Info> findAllInfo();
}
