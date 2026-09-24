package org.itech.labSampleTracker.controller;

import java.util.Map;

import org.itech.labSampleTracker.security.ConnectionLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compteurs de fréquentation du pied de page (utilisateur connecté) : en
 * ligne sur le web et le mobile, utilisateurs actifs et visites du mois.
 * Uniquement des totaux, aucune donnée nominative.
 */
@RestController
public class PresenceController {

	private final ConnectionLogService connectionLog;

	public PresenceController(ConnectionLogService connectionLog) {
		this.connectionLog = connectionLog;
	}

	@GetMapping("/presence")
	public Map<String, Long> presence() {
		return connectionLog.presence();
	}
}
