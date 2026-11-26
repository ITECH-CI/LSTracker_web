package org.itech.labSampleTracker.service;

import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.dto.DashboardFilter;
import org.itech.labSampleTracker.dto.DayCountDTO;
import org.itech.labSampleTracker.dto.StepDurationDTO;

public interface DashboardService {

	public Map<String, Object> getSummary(DashboardFilter filter);

	/**
	 * Étapes atteintes par type d'échantillon (« Étapes des échantillons par
	 * type ») : type → SAMPLE_COLLECTED, SAMPLE_RECEIVED, SAMPLE_ACCEPTED,
	 * ANALYSIS_DONE, RESULT_COLLECTED, RESULT_ON_SITE, NON_CONFORM, FAILED.
	 */
	public Map<String, Map<String, Integer>> stepsByType(DashboardFilter filter);

	public List<DayCountDTO> seriesCollected(DashboardFilter filter);

	public List<DayCountDTO> seriesDeposited(DashboardFilter filter);

	public List<DayCountDTO> seriesAnalysed(DashboardFilter filter);

	public List<DayCountDTO> seriesDelivered(DashboardFilter filter);

	public List<StepDurationDTO> stepDurations(DashboardFilter filter);

	/** Durée médiane de chaque étape, tous types confondus : step, n, median_days. */
	public List<Map<String, Object>> stepMedians(DashboardFilter filter);

}
