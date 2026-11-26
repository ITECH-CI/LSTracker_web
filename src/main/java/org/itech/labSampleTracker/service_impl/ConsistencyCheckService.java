package org.itech.labSampleTracker.service_impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.itech.labSampleTracker.dao.DashboardAdvancedRepository;
import org.itech.labSampleTracker.dao.ReportJdbcRepository;
import org.itech.labSampleTracker.dto.DashboardFilter;
import org.itech.labSampleTracker.dto.DayCountDTO;
import org.itech.labSampleTracker.service.DashboardService;
import org.itech.labSampleTracker.service.SampleService;
import org.springframework.stereotype.Service;

/**
 * Contrôle de cohérence des indicateurs (cahier VI.4 et critère d'acceptation
 * XII.3) : un même indicateur doit afficher la même valeur sur tous les
 * écrans et rapports, à filtres et période identiques, et la somme des sites
 * doit donner le district, celle des districts la région (V.5).
 *
 * <p>Chaque indicateur est recalculé par le code réel de chaque écran — pas
 * par une requête écrite pour l'occasion — et comparé à la référence : les
 * cartes et l'entonnoir du tableau de bord.
 */
@Service
public class ConsistencyCheckService {

	/** Une comparaison : référence (tableau de bord) contre un autre écran. */
	public record Check(String indicator, long reference, String source, long value) {
		public boolean ok() {
			return reference == value;
		}
	}

	/** Un écart de hiérarchie : parent contre somme de ses enfants. */
	public record HierarchyGap(String level, String name, String indicator, long parent, long children) {
	}

	private static final String[] HIERARCHY_INDICATORS = { "total", "in_transit", "deposited", "delivered",
			"non_conform", "failed" };
	private static final Map<String, String> HIERARCHY_LABELS = Map.of("total", "Collectés", "in_transit",
			"En cours d'acheminement", "deposited", "Déposés au laboratoire", "delivered", "Résultats rendus aux sites",
			"non_conform", "Non-conformités", "failed", "Échecs d'analyse");

	private final DashboardAdvancedRepository dashboard;
	private final DashboardService series;
	private final SampleService samples;
	private final ReportJdbcRepository reports;

	public ConsistencyCheckService(DashboardAdvancedRepository dashboard, DashboardService series,
			SampleService samples, ReportJdbcRepository reports) {
		this.dashboard = dashboard;
		this.series = series;
		this.samples = samples;
		this.reports = reports;
	}

	public List<Check> indicators(DashboardFilter f) {
		Map<String, Object> ref = dashboard.funnel(f);
		long collected = num(ref.get("total"));
		List<Check> out = new ArrayList<>();

		out.add(new Check("Collectés", collected, "Répartition par région (somme)",
				sum(dashboard.statsByRegion(f), "total")));
		out.add(new Check("Collectés", collected, "Répartition par district (somme)",
				sum(dashboard.statsByDistrict(f), "total")));
		out.add(new Check("Collectés", collected, "Répartition par site (somme)",
				sum(dashboard.statsBySite(f), "total")));
		out.add(new Check("Collectés", collected, "Répartition par type (somme)",
				sum(dashboard.typeBreakdown(f), "total")));
		out.add(new Check("Collectés", collected, "Évolution dans le temps (somme)",
				days(series.seriesCollected(f))));
		out.add(new Check("Collectés", collected, "Parcours par type (somme)",
				samples.getSampleStatusBySampleType(f).values().stream()
						.mapToLong(m -> m.getOrDefault("SAMPLE_COLLECTED", 0)).sum()));
		out.add(new Check("Collectés", collected, "Rapport PDF (somme par type)",
				sum(reports.collectedByType(f, null), "cnt")));

		long atLab = num(ref.get("at_lab"));
		out.add(new Check("Reçus au laboratoire", atLab, "Évolution dans le temps (somme)",
				days(series.seriesDeposited(f))));

		long analysed = num(ref.get("analysed"));
		out.add(new Check("Analysés", analysed, "Évolution dans le temps (somme)",
				days(series.seriesAnalysed(f))));
		out.add(new Check("Analysés", analysed, "Rapport PDF (résultats prêts)",
				sum(reports.resultReadyByType(f, null), "cnt")));

		long delivered = num(ref.get("delivered"));
		out.add(new Check("Résultats livrés", delivered, "Évolution dans le temps (somme)",
				days(series.seriesDelivered(f))));
		out.add(new Check("Résultats livrés", delivered, "Rapport PDF (résultats remis)",
				sum(reports.resultDeliveredByType(f, null), "cnt")));

		out.add(new Check("Non-conformités", num(ref.get("non_conform")), "Rapport PDF (rejetés)",
				sum(reports.rejectedByType(f, null), "cnt")));
		out.add(new Check("Échecs d'analyse", num(ref.get("failed")), "Rapport PDF (échecs)",
				sum(reports.failedByType(f, null), "cnt")));
		return out;
	}

	/** Somme des sites = district, somme des districts = région (V.5). */
	public List<HierarchyGap> hierarchy(DashboardFilter f) {
		List<Map<String, Object>> regions = dashboard.statsByRegion(f);
		List<Map<String, Object>> districts = dashboard.statsByDistrict(f);
		List<Map<String, Object>> sites = dashboard.statsBySite(f);
		List<HierarchyGap> gaps = new ArrayList<>();
		for (String ind : HIERARCHY_INDICATORS) {
			Map<String, Long> byRegion = group(districts, ind, "region");
			for (Map<String, Object> r : regions) {
				long children = byRegion.getOrDefault(Objects.toString(r.get("region")), 0L);
				if (num(r.get(ind)) != children) {
					gaps.add(new HierarchyGap("Région", Objects.toString(r.get("region")), HIERARCHY_LABELS.get(ind),
							num(r.get(ind)), children));
				}
			}
			Map<String, Long> byDistrict = group(sites, ind, "region", "district");
			for (Map<String, Object> d : districts) {
				String key = d.get("region") + " › " + d.get("district");
				long children = byDistrict.getOrDefault(key, 0L);
				if (num(d.get(ind)) != children) {
					gaps.add(new HierarchyGap("District", key, HIERARCHY_LABELS.get(ind), num(d.get(ind)), children));
				}
			}
		}
		return gaps;
	}

	private static Map<String, Long> group(List<Map<String, Object>> rows, String indicator, String... keys) {
		Map<String, Long> out = new LinkedHashMap<>();
		for (Map<String, Object> r : rows) {
			List<String> k = new ArrayList<>();
			for (String key : keys) {
				k.add(Objects.toString(r.get(key)));
			}
			out.merge(String.join(" › ", k), num(r.get(indicator)), Long::sum);
		}
		return out;
	}

	static long sum(List<Map<String, Object>> rows, String column) {
		return rows.stream().mapToLong(r -> num(r.get(column))).sum();
	}

	private static long days(List<DayCountDTO> series) {
		return series.stream().mapToLong(DayCountDTO::getCnt).sum();
	}

	static long num(Object v) {
		return v instanceof Number n ? n.longValue() : 0L;
	}
}
