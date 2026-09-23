package org.itech.labSampleTracker.entities;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Manuel d'aide téléversé depuis l'administration (un par type). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "help_document")
public class HelpDocument implements Serializable {
	private static final long serialVersionUID = 1L;

	/** {@link org.itech.labSampleTracker.enums.HelpDocumentType#name()}. */
	@Id
	@Column(name = "code", length = 40)
	private String code;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Basic(fetch = FetchType.LAZY)
	@Column(name = "content", nullable = false)
	private byte[] content;

	@Column(name = "size_bytes", nullable = false)
	private Long sizeBytes;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "uploaded_at", nullable = false)
	private Date uploadedAt;

	@Column(name = "uploaded_by", length = 100)
	private String uploadedBy;
}
