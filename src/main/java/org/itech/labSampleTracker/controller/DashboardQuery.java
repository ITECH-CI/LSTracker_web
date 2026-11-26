package org.itech.labSampleTracker.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.BindParam;

/**
 * Filtres choisis à l'écran du tableau de bord, communs à tous ses points
 * d'accès. Dans chaque liste, la valeur 0 (option « Tout ») ou l'absence de
 * valeur ne filtre pas.
 */
public record DashboardQuery(
		@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
		@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
		Integer region, Integer district, Integer site, Integer lab,
		@BindParam("lab_type") String labType,
		@BindParam("sample_type") Integer sampleType) {

	/** Période par défaut : les deux dernières années. */
	public LocalDate start() {
		return startDate != null ? startDate : LocalDate.now().minusYears(2);
	}

	public LocalDate end() {
		return endDate != null ? endDate : LocalDate.now();
	}

	public Integer regionId() {
		return all(region);
	}

	public Integer districtId() {
		return all(district);
	}

	public Integer siteId() {
		return all(site);
	}

	public Integer labId() {
		return all(lab);
	}

	public Integer sampleTypeId() {
		return all(sampleType);
	}

	public String labTypeCode() {
		return labType == null || labType.isBlank() || "0".equals(labType.trim()) ? null : labType.trim();
	}

	private static Integer all(Integer v) {
		return v == null || v == 0 ? null : v;
	}
}
