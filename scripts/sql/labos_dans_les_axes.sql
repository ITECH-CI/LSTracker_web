-- =============================================================================
-- Laboratoires proposés comme sites de collecte dans les axes
-- (observation mobile 1-2 du 31/05/2026)
-- =============================================================================
--
-- Constat : des laboratoires existent aussi comme SITES rattachés aux axes. Le
-- convoyeur les voit alors deux fois : comme site de collecte (via l'axe) et
-- comme laboratoire de destination.
--
-- Deux cas, à trancher SITE PAR SITE après l'étape 1 :
--   a) le site n'a jamais servi à collecter (nb_collectes = 0)
--      -> le retirer des axes (étape 2) ;
--   b) le site collecte réellement (hôpital qui prélève ET analyse)
--      -> le garder, mais le renommer « … (collecte) » pour le distinguer du
--         laboratoire (étape 3), comme le demande l'observation
--         (« CAT BOUAKE (collecte) »).
--
-- Rien n'est modifié par l'étape 1. Les étapes 2 et 3 n'agissent que sur les
-- identifiants recopiés à la main : relire, remplir, puis exécuter dans une
-- transaction (BEGIN ... COMMIT) sur chaque environnement (démo, prod).
--
-- Faire une sauvegarde avant : scripts/backup-db.sh
-- =============================================================================

SET search_path TO sample_tracker, public;

-- -----------------------------------------------------------------------------
-- Étape 1 — Prévisualisation (lecture seule)
-- Sites dont le nom correspond à un laboratoire du même district (casse,
-- espaces et « de » / « d' » ignorés).
-- -----------------------------------------------------------------------------
WITH site_n AS (
  SELECT s.id AS site_id, s.name AS site_name, s.district_id,
         regexp_replace(upper(regexp_replace(s.name, '\s+(de|d'')\s+', ' ', 'gi')), '\s+', ' ', 'g') AS n
  FROM site s
), lab_n AS (
  SELECT l.id AS lab_id, l.lab_name, l.district_id,
         regexp_replace(upper(regexp_replace(l.lab_name, '\s+(de|d'')\s+', ' ', 'gi')), '\s+', ' ', 'g') AS n
  FROM lab l
)
SELECT sn.site_id,
       sn.site_name,
       ln.lab_name AS labo_homonyme,
       (SELECT count(*) FROM circuit_site cs WHERE cs.site_id = sn.site_id) AS nb_axes,
       (SELECT count(*) FROM sample_retrieving sr WHERE sr.site_id = sn.site_id) AS nb_collectes,
       CASE WHEN (SELECT count(*) FROM sample_retrieving sr WHERE sr.site_id = sn.site_id) = 0
            THEN 'retirer des axes (étape 2)'
            ELSE 'renommer « (collecte) » (étape 3)' END AS suggestion
FROM site_n sn
JOIN lab_n ln ON ln.n = sn.n AND ln.district_id = sn.district_id
ORDER BY nb_collectes, sn.site_name;

-- Compléter à la main les sites que la prévisualisation ne détecte pas (nom
-- trop différent, ex. « EPHR BEOUMI ») : chercher par nom.
-- SELECT id, name FROM site WHERE name ILIKE '%BEOUMI%';

-- -----------------------------------------------------------------------------
-- Étape 2 — Retirer des axes les sites qui ne collectent pas
-- Remplacer la liste par les site_id retenus. Le site reste en base (les
-- échantillons historiques y restent rattachés) ; seul le lien aux axes
-- disparaît. Équivalent à le décocher dans Administration → Axes.
-- -----------------------------------------------------------------------------
-- BEGIN;
-- DELETE FROM circuit_site
--  WHERE site_id IN (/* ex. 2, 179 */)
--    AND NOT EXISTS (SELECT 1 FROM sample_retrieving sr WHERE sr.site_id = circuit_site.site_id);
-- COMMIT;

-- -----------------------------------------------------------------------------
-- Étape 3 — Renommer les sites qui collectent réellement
-- Remplacer la liste par les site_id retenus.
-- -----------------------------------------------------------------------------
-- BEGIN;
-- UPDATE site
--    SET name = name || ' (collecte)', last_updated_at = now()
--  WHERE id IN (/* ex. 116, 176 */)
--    AND name NOT ILIKE '%(collecte)%';
-- COMMIT;

-- Après exécution : les convoyeurs récupèrent la nouvelle liste à la prochaine
-- synchronisation des métadonnées (ou en se reconnectant).
