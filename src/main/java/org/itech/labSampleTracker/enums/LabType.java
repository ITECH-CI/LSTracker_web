package org.itech.labSampleTracker.enums;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public enum LabType {
	DISTRICT_LAB("DISTRICT"), HUB_LAB("RELAIS"), PLATEFORM_LAB("PLATEFORME"), TB_LAB("CAT");

	/**
	 * Libellés affichés des valeurs de {@code lab.lab_type}, y compris celles
	 * sans constante dans cette énumération. Les listes de filtres ne
	 * proposent que les valeurs présentes dans le référentiel des laboratoires.
	 */
	private static final Map<String, String> LABELS = Map.of(
			"DISTRICT", "Laboratoire de district",
			"REGION", "Laboratoire régional",
			"CAT", "CAT",
			"PLATEFORME", "Plateforme de biologie moléculaire",
			"HUB", "Hub",
			"RELAIS", "Laboratoire relais");

	private String type;

	LabType(String type) {
		this.type = type;
	}

	public String getType() {
		return type;
	}

	public static LabType get(final String type) {
		for (final LabType e : LabType.values()) {
			if (StringUtils.equals(e.getType(), type)) {
				return e;
			}
		}
		return null;
	}

	/** Libellé lisible d'une valeur de {@code lab.lab_type} ; la valeur brute si elle est inconnue. */
	public static String label(String code) {
		if (code == null) {
			return null;
		}
		return LABELS.getOrDefault(code.trim().toUpperCase(), code);
	}
}
