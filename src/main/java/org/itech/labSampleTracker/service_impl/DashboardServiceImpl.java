package org.itech.labSampleTracker.service_impl;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.itech.labSampleTracker.dao.DashboardNativeRepository;
import org.itech.labSampleTracker.dto.DashboardFilter;
import org.itech.labSampleTracker.dto.DayCountDTO;
import org.itech.labSampleTracker.dto.StepDurationDTO;
import org.itech.labSampleTracker.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

	@PersistenceContext
	private EntityManager em;

	private final DashboardNativeRepository repo;

	@Override
	public Map<String, Object> getSummary(DashboardFilter filter) {
		Map<String, Object> m = repo.summary(filter);
		Map<String, Object> out = new LinkedHashMap<>();
		for (String k : Arrays.asList("all_count", "in_transit", "received", "accepted", "result_ready",
				"result_collected", "result_on_site", "rejected", "failed")) {
			out.put(k, toLong(m.get(k)));
		}

		Long allCount = toLong(m.get("all_count"));
		Long inTransit = toLong(m.get("in_transit"));
		Long resultReady = toLong(m.get("result_ready"));
		Long resultCollected = toLong(m.get("result_collected"));
		Long resultOnSite = toLong(m.get("result_on_site"));
		Long rejected = toLong(m.get("rejected"));
		Long failed = toLong(m.get("failed"));

		// Cumuls "funnel-like" : un sample passé en aval est aussi compté
		// dans l'étape amont. Garde la symétrie résultat_ready ⊇ collecté ⊇ on_site.
		Long resultReadyCumul = resultReady + resultCollected + resultOnSite;
		Long resultCollectedCumul = resultCollected + resultOnSite;

		Map<String, Object> out2 = new LinkedHashMap<>();

		out2.put("all_count", allCount);
		out2.put("in_transit", inTransit);
		// "delivered" = nombre d'échantillons dont le résultat a été livré
		// sur site dans la fenêtre (déf "activité fenêtre", alignée sur les
		// rapports et le funnel du dashboard). Ne pas calculer par
		// soustraction (allCount - inTransit) — c'était faux car les deux
		// métriques ont des axes-date différents.
		out2.put("delivered", resultOnSite);
		out2.put("rejected", rejected);
		out2.put("result_ready", resultReadyCumul);
		out2.put("failed", failed);
		out2.put("result_collected", resultCollectedCumul);
		out2.put("result_on_site", resultOnSite);

		return out2;
	}

	@Override
	public List<DayCountDTO> seriesCollected(DashboardFilter filter) {
		return mapDayCount(repo.tsCollected(filter));
	}

	@Override
	public List<DayCountDTO> seriesDeposited(DashboardFilter filter) {
		return mapDayCount(repo.tsDeposited(filter));
	}

	@Override
	public List<DayCountDTO> seriesAnalysed(DashboardFilter filter) {
		return mapDayCount(repo.tsAnalysed(filter));
	}

	@Override
	public List<DayCountDTO> seriesDelivered(DashboardFilter filter) {
		return mapDayCount(repo.tsDelivered(filter));
	}

	@Override
	public List<StepDurationDTO> stepDurations(DashboardFilter filter) {
		List<Map<String, Object>> rows = repo.stepDurationsDays(filter);

		return rows.stream().map(m -> {
			StepDurationDTO dto = new StepDurationDTO();
			dto.setSampleType(String.valueOf(m.get("sample_type")));
			dto.setStep(String.valueOf(m.get("step")));
			dto.setN(toLong(m.get("n")).intValue());
			dto.setAvgDays(toLong(m.get("avg_days")).intValue());
			dto.setMedianDays(toLong(m.get("median_days")).intValue());
			dto.setMinDays(toLong(m.get("min_days")).intValue());
			dto.setMaxDays(toLong(m.get("max_days")).intValue());
			return dto;
		}).collect(Collectors.toList());
	}

	private List<DayCountDTO> mapDayCount(List<Map<String, Object>> rows) {
		List<DayCountDTO> out = new ArrayList<>(rows.size());
		for (Map<String, Object> m : rows) {
			Date sqlDate = (Date) m.get("day");
			LocalDate day = (sqlDate != null ? sqlDate.toLocalDate() : null);
			long cnt = toLong(m.get("cnt"));
			out.add(new DayCountDTO(day, cnt));
		}
		return out;
	}

	private Long toLong(Object v) {
		if (v == null)
			return 0L;
		if (v instanceof Number)
			return ((Number) v).longValue();
		try {
			return Long.parseLong(v.toString());
		} catch (Exception e) {
			return 0L;
		}
	}

}
