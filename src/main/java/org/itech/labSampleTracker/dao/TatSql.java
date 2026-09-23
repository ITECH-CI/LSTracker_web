package org.itech.labSampleTracker.dao;

/**
 * Définition unique du TAT (délai de traitement), cahier VI.1 : « médiane du
 * délai entre la collecte et la livraison du résultat ». Toutes les requêtes
 * l'utilisent via ces constantes, pour qu'un même indicateur affiche la même
 * valeur sur tous les écrans et rapports (cahier VI.4).
 *
 * <p>Constantes de compilation : utilisables dans les annotations
 * {@code @Query}. Alias attendus : {@code s} (sample), et {@code ss2}
 * (sample_status) pour {@link #SAMPLE_DAYS}.
 */
public final class TatSql {

	private TatSql() {
	}

	/**
	 * TAT d'un échantillon livré, en jours décimaux : livraison du résultat −
	 * collecte. NULL si le résultat n'est pas livré (ignoré par PERCENTILE_CONT).
	 */
	public static final String DAYS = "EXTRACT(EPOCH FROM (s.result_delivery_date - s.collection_date)) / 86400.0";

	/** Médiane du TAT sur les échantillons livrés, 0 s'il n'y en a aucun. */
	public static final String MEDIAN = "COALESCE(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY " + DAYS
			+ "), 0)::numeric(10,1)";

	/**
	 * TAT individuel affiché dans la liste des échantillons, en jours entiers :
	 * même point de départ (collecte), et fin à l'étape terminale — livraison du
	 * résultat, rejet ou échec d'analyse. Un dossier en cours (ou terminal sans
	 * date de fin saisie) court jusqu'à maintenant : un TAT anormal signale la
	 * donnée manquante plutôt que de la masquer. Pour un dossier livré, la
	 * valeur est celle de {@link #DAYS}, arrondie au jour inférieur.
	 */
	public static final String SAMPLE_DAYS = "CAST(FLOOR(EXTRACT(EPOCH FROM (COALESCE("
			+ "    CASE ss2.status "
			+ "      WHEN 'RESULT_ON_SITE' THEN CAST(s.result_delivery_date AS TIMESTAMP) "
			+ "      WHEN 'NON_CONFORM' THEN CAST(s.rejection_date AS TIMESTAMP) "
			+ "      WHEN 'ANALYSIS_FAILED' THEN CAST(s.analysis_completed_date AS TIMESTAMP) "
			+ "      ELSE NULL END, LOCALTIMESTAMP)"
			+ "    - CAST(s.collection_date AS TIMESTAMP))) / 86400) AS INT)";
}
