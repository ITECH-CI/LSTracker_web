package org.itech.labSampleTracker.enums;

import org.apache.commons.lang3.StringUtils;

public enum ESampleStatus {
	ON_TRANSIT("EN COURS D'ACHEMINEMENT"), RECEIVED_AT_HUB("TRANSMIS LABO RELAIS"), RECEIVED_AT_REFERENCE_LAB("TRANSMIS LABO REFERENCE"),
	RECEIVED_AT_DISTRICT_LAB("TRANSMIS LABO DISTRCIT"), RECEIVED_AT_TB_LAB("TRANSMIS LABO CAT"),
	ACCEPTED_AT_HUB("ACCEPTE LABO RELAIS"), ACCEPTED_AT_REFERENCE_LAB("ACCEPTE LABO REFERENCE"),ACCEPTED_AT_DISTRICT_LAB("ACCEPTE LABO DISTRICT"),
	ACCEPTED_AT_TB_LAB("ACCEPTE LABO CAT"),ANALYSIS_DONE("ANALYSE TERMINEE"), NON_CONFORM("NON CONFORME"), ANALYSIS_FAILED("ECHEC ANALYSE"),
	RESULT_COLLECTED("RESULTATS RECUPERES"), RESULT_ON_SITE("RESULTAT SUR SITE");

	private String status;

	ESampleStatus(String status) {
		this.status = status;
	}

	public String getStatus() {
		return status;
	}

	public static ESampleStatus get(final String status) {
		for (final ESampleStatus e : ESampleStatus.values()) {
			if (StringUtils.equals(e.getStatus(), status)) {
				return e;
			}
		}
		return null;
	}
}
