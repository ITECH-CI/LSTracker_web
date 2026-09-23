package org.itech.labSampleTracker.api.meta;

import java.util.List;

public class MetaDtos {

	public record LabDto(Long id, String name, String labType) {
	}

	public record CircuitDto(Long id, String name) {
	}

	public record SiteDto(Long id, String name, String dhisCode) {
	}

	public record RejectionTypeDto(Long id, String name) {
	}

	public record CircuitSiteDto(Long circuitId, Long siteId) {
	}

	/**
	 * {@code labs} = labos que l'utilisateur peut choisir à la saisie ;
	 * {@code allLabs} = référentiel complet, pour afficher le nom et le type de
	 * n'importe quel labo référencé par un échantillon.
	 */
	public record MetaFullResponse(String version, List<LabDto> labs, List<CircuitDto> circuits, List<SiteDto> sites,
			List<RejectionTypeDto> rejectionTypes, List<CircuitSiteDto> circuitSites, List<LabDto> allLabs) {
	}
}