package org.itech.labSampleTracker.helper;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.itech.labSampleTracker.entities.Sample;

/**
 * Cohérence des dates d'étape d'un échantillon, même règle que le mobile
 * ({@code SampleDao.stepDateError}) :
 * <ul>
 * <li>aucune date dans le futur ;</li>
 * <li>aucune étape avant la collecte ;</li>
 * <li>ordre imposé seulement au sein d'une même chaîne : dépôt → acceptation,
 * fin d'analyse → validation → récupération des résultats → remise.</li>
 * </ul>
 * Les dates de dépôt et d'acceptation sont des horodatages de saisie, souvent
 * enregistrés après l'analyse (391 cas sur base réelle) : l'analyse n'est donc
 * pas comparée au dépôt ni à l'acceptation.
 */
public final class SampleDateValidator {

	/** Tolérance sur l'horloge avant de considérer une date « dans le futur ». */
	private static final long FUTURE_TOLERANCE_MS = 5 * 60 * 1000L;

	private SampleDateValidator() {
	}

	/**
	 * @throws IllegalArgumentException avec un message lisible si une date est
	 *                                  incohérente
	 */
	public static void validate(Sample s) {
		Date limit = new Date(System.currentTimeMillis() + FUTURE_TOLERANCE_MS);
		Date collection = s.getCollectionDate();

		notFuture("collecte", collection, limit);
		afterCollection("dépôt au labo relais", s.getDeliverAtHubDate(), collection, limit);
		afterCollection("acceptation au labo relais", s.getAcceptedAtHubDate(), collection, limit);
		afterCollection("dépôt au labo", s.getDeliverAtLabDate(), collection, limit);
		afterCollection("acceptation au labo", s.getAcceptedAtLabDate(), collection, limit);
		afterCollection("fin d'analyse", s.getAnalysisCompletedDate(), collection, limit);
		afterCollection("validation biologique", s.getAnalysisReleasedDate(), collection, limit);
		afterCollection("récupération des résultats", s.getResultCollectionDate(), collection, limit);
		afterCollection("remise des résultats", s.getResultDeliveryDate(), collection, limit);
		afterCollection("rejet", s.getRejectionDate(), collection, limit);

		ordered("dépôt au labo relais", s.getDeliverAtHubDate(), "acceptation au labo relais",
				s.getAcceptedAtHubDate());
		ordered("dépôt au labo", s.getDeliverAtLabDate(), "acceptation au labo", s.getAcceptedAtLabDate());
		ordered("fin d'analyse", s.getAnalysisCompletedDate(), "validation biologique",
				s.getAnalysisReleasedDate());
		ordered("validation biologique", s.getAnalysisReleasedDate(), "récupération des résultats",
				s.getResultCollectionDate());
		ordered("récupération des résultats", s.getResultCollectionDate(), "remise des résultats",
				s.getResultDeliveryDate());
	}

	private static void notFuture(String label, Date d, Date limit) {
		if (d != null && d.after(limit)) {
			throw new IllegalArgumentException("date de " + label + " dans le futur (" + fmt(d) + ")");
		}
	}

	private static void afterCollection(String label, Date d, Date collection, Date limit) {
		notFuture(label, d, limit);
		ordered("collecte", collection, label, d);
	}

	private static void ordered(String beforeLabel, Date before, String afterLabel, Date after) {
		if (before != null && after != null && after.before(before)) {
			throw new IllegalArgumentException("date de " + afterLabel + " (" + fmt(after)
					+ ") antérieure à la date de " + beforeLabel + " (" + fmt(before) + ")");
		}
	}

	private static String fmt(Date d) {
		return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(d);
	}
}
