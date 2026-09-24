-- =============================================================================
-- Dates aberrantes des échantillons (qualité des données)
-- =============================================================================
--
-- Constat : des dates saisies avant la validation des dates (v2.2.3) portent
-- des fautes de frappe sur l'année : 0024, 0204 (chiffres manquants), 20244,
-- 202412 (chiffre en trop), 2027, 5024, 2021 (chiffre erroné).
--
-- Est « aberrante » une date antérieure au 01/01/2024 (mise en service) ou
-- postérieure à maintenant. La correction proposée garde le jour, le mois et
-- l'heure saisis, et prend l'année la plus proche de created_at (horodatage
-- d'enregistrement posé par le système, fiable : écart médian de 0,2 jour avec
-- la date de collecte). Si le plancher n'est pas le bon pour un environnement,
-- le changer dans pg_temp.aberrante et dans la synthèse de l'étape 0.
--
-- Garde-fou : un échantillon n'est corrigé que si ses dates corrigées
-- respectent les règles de saisie (SampleDateValidator) : collecte au plus
-- tard le lendemain de l'enregistrement, étapes après la collecte, ordre au
-- sein d'une même chaîne. Sinon il est laissé « à vérifier » à la main.
--
-- Les étapes 0 et 1 ne modifient rien. L'étape 2 corrige, dans une
-- transaction, après sauvegarde des valeurs d'origine dans une table ; elle
-- ne s'exécute qu'avec l'option -v corriger=1.
--
-- À exécuter d'un seul tenant (les fonctions pg_temp vivent le temps de la
-- session psql). Audit seul :
--   docker exec -i lst_demo_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' \
--     < scripts/sql/dates_aberrantes.sql
-- Correction, après relecture de l'audit :
--   docker exec -i lst_demo_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v corriger=1' \
--     < scripts/sql/dates_aberrantes.sql
--
-- Faire une sauvegarde avant l'étape 2 : scripts/backup-db.sh
-- =============================================================================

SET search_path TO sample_tracker, public;

CREATE FUNCTION pg_temp.aberrante(v timestamp) RETURNS boolean LANGUAGE sql STABLE AS $$
  SELECT v IS NOT NULL AND (v < TIMESTAMP '2024-01-01' OR v > LOCALTIMESTAMP)
$$;

-- Même jour, même mois, même heure ; année la plus proche de ref, sans
-- dépasser maintenant.
CREATE FUNCTION pg_temp.corrigee(v timestamp, ref timestamp) RETURNS timestamp LANGUAGE sql STABLE AS $$
  SELECT c FROM (
    SELECT v + make_interval(years => y - EXTRACT(YEAR FROM v)::int) AS c
    FROM generate_series(EXTRACT(YEAR FROM ref)::int - 1, EXTRACT(YEAR FROM ref)::int + 1) AS y
  ) x
  WHERE c <= LOCALTIMESTAMP
  ORDER BY abs(EXTRACT(EPOCH FROM (c - ref)))
  LIMIT 1
$$;

CREATE TEMP VIEW dates_echantillon AS
SELECT s.id, s.sample_identifier, s.created_at, x.colonne, x.valeur
FROM sample s, LATERAL (VALUES
  ('collection_date',         s.collection_date),
  ('pickup_date',             s.pickup_date),
  ('deliver_at_hub_date',     s.deliver_at_hub_date),
  ('accepted_at_hub_date',    s.accepted_at_hub_date),
  ('deliver_at_lab_date',     s.deliver_at_lab_date),
  ('accepted_at_lab_date',    s.accepted_at_lab_date),
  ('rejection_date',          s.rejection_date),
  ('analysis_completed_date', s.analysis_completed_date),
  ('analysis_released_date',  s.analysis_released_date),
  ('result_collection_date',  s.result_collection_date),
  ('result_delivery_date',    s.result_delivery_date),
  ('result_reported_date',    s.result_reported_date)
) AS x(colonne, valeur)
WHERE x.valeur IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Étape 0 — Synthèse (lecture seule)
-- -----------------------------------------------------------------------------
\echo '== Échantillons touchés'
SELECT count(DISTINCT id) AS echantillons_touches,
       (SELECT count(*) FROM sample) AS echantillons_total
FROM dates_echantillon WHERE pg_temp.aberrante(valeur);

\echo '== Par colonne (min et max des dates plausibles)'
SELECT colonne,
       count(*) FILTER (WHERE valeur < TIMESTAMP '2024-01-01') AS avant_2024,
       count(*) FILTER (WHERE valeur > LOCALTIMESTAMP)          AS futur,
       min(valeur) FILTER (WHERE NOT pg_temp.aberrante(valeur))::date AS min_plausible,
       max(valeur) FILTER (WHERE NOT pg_temp.aberrante(valeur))::date AS max_plausible
FROM dates_echantillon GROUP BY colonne ORDER BY colonne;

\echo '== Effet sur le TAT médian (collecte -> remise du résultat), en jours'
WITH t AS (
  SELECT EXTRACT(EPOCH FROM (result_delivery_date - collection_date)) / 86400.0 AS j,
         pg_temp.aberrante(collection_date) OR pg_temp.aberrante(result_delivery_date) AS douteux
  FROM sample WHERE result_delivery_date IS NOT NULL)
SELECT count(*) AS livres, count(*) FILTER (WHERE douteux) AS douteux,
       round(percentile_cont(0.5) WITHIN GROUP (ORDER BY j)::numeric, 1) AS mediane_actuelle,
       round((percentile_cont(0.5) WITHIN GROUP (ORDER BY j) FILTER (WHERE NOT douteux))::numeric, 1) AS mediane_sans_douteux
FROM t;

\echo '== Effet sur « Laboratoires les plus lents » (dépôt au labo -> validation), labos touchés'
WITH t AS (
  SELECT l.lab_name, EXTRACT(EPOCH FROM (s.analysis_released_date - s.deliver_at_lab_date)) / 86400.0 AS j,
         pg_temp.aberrante(s.deliver_at_lab_date) OR pg_temp.aberrante(s.analysis_released_date) AS douteux
  FROM sample s JOIN lab l ON l.id = s.reference_lab_id
  WHERE s.analysis_released_date > s.deliver_at_lab_date)
SELECT lab_name, count(*) AS n, count(*) FILTER (WHERE douteux) AS douteux,
       round(percentile_cont(0.5) WITHIN GROUP (ORDER BY j)::numeric, 1) AS mediane_actuelle,
       round((percentile_cont(0.5) WITHIN GROUP (ORDER BY j) FILTER (WHERE NOT douteux))::numeric, 1) AS mediane_sans_douteux
FROM t GROUP BY lab_name HAVING count(*) FILTER (WHERE douteux) > 0 ORDER BY lab_name;

-- -----------------------------------------------------------------------------
-- Étape 1 — Correction proposée, échantillon par échantillon (lecture seule)
-- -----------------------------------------------------------------------------
CREATE FUNCTION pg_temp.fixe(v timestamp, ref timestamp) RETURNS timestamp LANGUAGE sql STABLE AS $$
  SELECT CASE WHEN pg_temp.aberrante(v) THEN pg_temp.corrigee(v, ref) ELSE v END
$$;

CREATE TEMP TABLE correction AS
WITH c AS (
  SELECT s.id, s.created_at,
         pg_temp.aberrante(s.collection_date)         AS a_col,
         pg_temp.aberrante(s.deliver_at_hub_date)     AS a_hdep,
         pg_temp.aberrante(s.accepted_at_hub_date)    AS a_hacc,
         pg_temp.aberrante(s.deliver_at_lab_date)     AS a_ldep,
         pg_temp.aberrante(s.accepted_at_lab_date)    AS a_lacc,
         pg_temp.aberrante(s.rejection_date)          AS a_rej,
         pg_temp.aberrante(s.analysis_completed_date) AS a_fin,
         pg_temp.aberrante(s.analysis_released_date)  AS a_val,
         pg_temp.aberrante(s.result_collection_date)  AS a_rrec,
         pg_temp.aberrante(s.result_delivery_date)    AS a_rrem,
         pg_temp.fixe(s.collection_date, s.created_at)         AS col,
         pg_temp.fixe(s.pickup_date, s.created_at)             AS pickup,
         pg_temp.fixe(s.deliver_at_hub_date, s.created_at)     AS hdep,
         pg_temp.fixe(s.accepted_at_hub_date, s.created_at)    AS hacc,
         pg_temp.fixe(s.deliver_at_lab_date, s.created_at)     AS ldep,
         pg_temp.fixe(s.accepted_at_lab_date, s.created_at)    AS lacc,
         pg_temp.fixe(s.rejection_date, s.created_at)          AS rej,
         pg_temp.fixe(s.analysis_completed_date, s.created_at) AS fin,
         pg_temp.fixe(s.analysis_released_date, s.created_at)  AS val,
         pg_temp.fixe(s.result_collection_date, s.created_at)  AS rrec,
         pg_temp.fixe(s.result_delivery_date, s.created_at)    AS rrem,
         pg_temp.fixe(s.result_reported_date, s.created_at)    AS rrep
  FROM sample s
  WHERE s.id IN (SELECT id FROM dates_echantillon WHERE pg_temp.aberrante(valeur))
)
-- Une règle ne compte que si l'une de ses dates a été corrigée : une
-- incohérence déjà présente entre dates saisies correctement n'est pas de
-- notre fait.
SELECT c.*, (
  SELECT string_agg(motif, ' ; ') FROM (VALUES
    (a_col AND col > created_at + INTERVAL '1 day',  'collecte après l''enregistrement'),
    ((a_col OR a_hdep) AND hdep < col,  'dépôt relais avant la collecte'),
    ((a_col OR a_hacc) AND hacc < col,  'acceptation relais avant la collecte'),
    ((a_col OR a_ldep) AND ldep < col,  'dépôt labo avant la collecte'),
    ((a_col OR a_lacc) AND lacc < col,  'acceptation labo avant la collecte'),
    ((a_col OR a_rej)  AND rej  < col,  'rejet avant la collecte'),
    ((a_col OR a_fin)  AND fin  < col,  'fin d''analyse avant la collecte'),
    ((a_col OR a_val)  AND val  < col,  'validation avant la collecte'),
    ((a_col OR a_rrec) AND rrec < col,  'récupération avant la collecte'),
    ((a_col OR a_rrem) AND rrem < col,  'remise avant la collecte'),
    ((a_hdep OR a_hacc) AND hacc < hdep, 'acceptation relais avant le dépôt'),
    ((a_ldep OR a_lacc) AND lacc < ldep, 'acceptation labo avant le dépôt'),
    ((a_fin OR a_val)   AND val  < fin,  'validation avant la fin d''analyse'),
    ((a_val OR a_rrec)  AND rrec < val,  'récupération avant la validation'),
    ((a_rrec OR a_rrem) AND rrem < rrec, 'remise avant la récupération')
  ) AS r(ko, motif) WHERE ko
) AS a_verifier
FROM c;

\echo '== Bilan'
SELECT count(*) FILTER (WHERE a_verifier IS NULL)     AS corrigeables,
       count(*) FILTER (WHERE a_verifier IS NOT NULL) AS a_verifier
FROM correction;

\echo '== À vérifier à la main (non corrigés par l''étape 2)'
SELECT d.id, d.colonne, to_char(d.valeur, 'YYYY-MM-DD HH24:MI') AS saisie,
       to_char(d.created_at, 'YYYY-MM-DD') AS enregistre_le,
       to_char(pg_temp.corrigee(d.valeur, d.created_at), 'YYYY-MM-DD HH24:MI') AS proposition,
       c.a_verifier AS motif
FROM correction c JOIN dates_echantillon d ON d.id = c.id AND pg_temp.aberrante(d.valeur)
WHERE c.a_verifier IS NOT NULL
ORDER BY d.id, d.colonne;

\echo '== Détail des corrections'
SELECT d.id, d.colonne, to_char(d.valeur, 'YYYY-MM-DD HH24:MI') AS saisie,
       to_char(d.created_at, 'YYYY-MM-DD') AS enregistre_le,
       to_char(pg_temp.corrigee(d.valeur, d.created_at), 'YYYY-MM-DD HH24:MI') AS correction
FROM correction c JOIN dates_echantillon d ON d.id = c.id AND pg_temp.aberrante(d.valeur)
WHERE c.a_verifier IS NULL
ORDER BY d.id, d.colonne;

-- -----------------------------------------------------------------------------
-- Étape 2 — Correction, seulement avec l'option -v corriger=1
-- Ne touche que les échantillons corrigeables. Les valeurs d'origine sont
-- gardées dans sample_dates_avant_correction (une ligne par échantillon et par
-- passage). lastupdated_at est avancé pour que les mobiles récupèrent la
-- correction. Si le résultat ne correspond pas au bilan, tout est annulé.
-- -----------------------------------------------------------------------------
\if :{?corriger}
\echo '== Correction'
BEGIN;

CREATE TABLE IF NOT EXISTS sample_dates_avant_correction AS
SELECT s.id, s.collection_date, s.pickup_date, s.deliver_at_hub_date, s.accepted_at_hub_date,
       s.deliver_at_lab_date, s.accepted_at_lab_date, s.rejection_date, s.analysis_completed_date,
       s.analysis_released_date, s.result_collection_date, s.result_delivery_date,
       s.result_reported_date, s.lastupdated_at, LOCALTIMESTAMP AS corrige_le
FROM sample s WITH NO DATA;

INSERT INTO sample_dates_avant_correction
SELECT s.id, s.collection_date, s.pickup_date, s.deliver_at_hub_date, s.accepted_at_hub_date,
       s.deliver_at_lab_date, s.accepted_at_lab_date, s.rejection_date, s.analysis_completed_date,
       s.analysis_released_date, s.result_collection_date, s.result_delivery_date,
       s.result_reported_date, s.lastupdated_at, LOCALTIMESTAMP
FROM sample s JOIN correction c ON c.id = s.id
WHERE c.a_verifier IS NULL;

UPDATE sample s SET
  collection_date         = c.col,
  pickup_date             = c.pickup,
  deliver_at_hub_date     = c.hdep,
  accepted_at_hub_date    = c.hacc,
  deliver_at_lab_date     = c.ldep,
  accepted_at_lab_date    = c.lacc,
  rejection_date          = c.rej,
  analysis_completed_date = c.fin,
  analysis_released_date  = c.val,
  result_collection_date  = c.rrec,
  result_delivery_date    = c.rrem,
  result_reported_date    = c.rrep,
  lastupdated_at          = LOCALTIMESTAMP
FROM correction c
WHERE c.id = s.id AND c.a_verifier IS NULL;

-- Contrôle : ne doivent rester que les échantillons « à vérifier ».
SELECT (SELECT count(DISTINCT id) FROM dates_echantillon WHERE pg_temp.aberrante(valeur)) AS restants,
       (SELECT count(*) FROM correction WHERE a_verifier IS NOT NULL) AS attendus \gset
SELECT :restants = :attendus AS controle_ok \gset
\if :controle_ok
COMMIT;
\echo '== Correction validée. Échantillons restant à vérifier à la main :' :restants
\else
ROLLBACK;
\echo '== ANNULÉ : ' :restants ' échantillons aberrants restants, ' :attendus ' attendus'
\endif
\else
\echo '== Aucune modification. Pour corriger : relancer avec -v corriger=1'
\endif
