# Changelog — LabSampleTracker (web + backend)

## 2026-09-23 — Accès des convoyeurs par district, dates, tableau de bord (v2.2.3)

Règle métier : un convoyeur a accès aux laboratoires situés dans les districts où il intervient (circuit → site → district → labo), sur l'ensemble de ses circuits.

- **Métadonnées mobile** (`/api/meta/full`) : la liste des labos du convoyeur est dédoublonnée et ne retient plus que les labos, circuits et affectations circuit/site **actifs**.
- **Périmètre web** (`UserScopeService`) : les labos des districts couverts par les circuits sont désormais inclus. Auparavant un convoyeur n'avait aucun labo dans son périmètre : un filtre labo sur le tableau de bord ou la liste des échantillons renvoyait une page vide.
- **Synchronisation** : un échantillon poussé avec un labo de destination ou de dépôt hors périmètre est **accepté mais journalisé** (`WARN`), pour ne pas perdre une saisie faite hors ligne avec d'anciennes métadonnées.
- `GET /lab/names_by_users` reconnaît le biologiste par son rôle **ou** son `user_type`, comme les métadonnées mobile.

### Métadonnées mobile : référentiel complet des labos

- `/api/meta/full` renvoie aussi `allLabs` (tous les labos) en plus de `labs` (labos sélectionnables). Le mobile 2.2.3 s'en sert pour afficher le nom et le type de tout labo référencé par un échantillon ; un mobile plus ancien l'ignore.

### Connexion mobile : rôle effectif

- `POST /api_v2/auth/login` renvoie le type effectif (rôle **ou** `user_type`). 17 convoyeurs sur 19 ont `role = USER` : ils arrivaient sur le tableau de bord générique, sans action de collecte. Le rôle porté par le JWT (autorisations) est inchangé.

### Validation des dates d'étape (observation 7.5)

- Nouveau `SampleDateValidator` : aucune date dans le futur (tolérance 5 min), aucune étape avant la collecte, ordre imposé au sein d'une même chaîne (dépôt → acceptation, fin d'analyse → validation → récupération → remise des résultats). L'analyse n'est pas comparée au dépôt ni à l'acceptation : sur base réelle, ces deux dates sont des horodatages de saisie, souvent postérieurs à l'analyse (391 cas).
- Appliqué à la modification et à la création d'un échantillon sur le web ; les sélecteurs de date du formulaire ne proposent plus de date future.
- Synchronisation mobile : dates incohérentes acceptées mais journalisées (`WARN`).
- Sur la base de test, 77 fiches sur 2 995 violent ces règles (dates avant la collecte, dans le futur, validation avant fin d'analyse) : leur modification exigera de corriger la date fautive.

### Axes : laboratoires proposés comme sites de collecte (observation mobile 1-2)

- Métadonnées mobile : seuls les axes et rattachements site/axe **actifs** sont proposés.
- Script `scripts/sql/labos_dans_les_axes.sql` (à relire, non exécuté automatiquement) : liste les sites homonymes d'un laboratoire, puis retire des axes ceux qui n'ont jamais collecté et renomme « … (collecte) » ceux qui collectent réellement (sur la base de test : 7 à retirer, 5 à renommer, dont HG Zoukougbeu avec 262 collectes).
- Tableau de bord : la vue « Districts » s'intitule « Répartition par district sanitaire » (observation 1.12).

### Tableau de bord : période comparée (observation 1.5) et refonte visuelle

- Raccourcis de période calendaire : Aujourd'hui, Semaine, Mois, Trimestre, Semestre, Année. La période précédente est calée sur le calendrier à nombre de jours égal (mois au 23/09 → 01→23/08) ; dates saisies à la main : période de même durée juste avant. Premier test métier Java : `DashboardPreviousPeriodTest`.
- Tendances : plus de « vs préc. » ; la valeur de la période précédente est affichée (« ↑ +15 % · préc. 1 234 »), ses dates en info-bulle ; facteur au-delà de +1000 % (« ×297 ») ; rien quand la période précédente est vide ; hausse des non-conformités, échecs et TAT en rouge.
- Nouvelle structure : bandeau « Parcours de l'échantillon » (étapes fléchées, « En transit » en pastille temps réel), puis trois colonnes Qualité & délais / Couverture (jauges) / Répartition par type.
- Graphiques : même couleur pour une même étape partout (échelle violette validée), rouge réservé aux rejets ; « Parcours par type » sans les types vides, avec bascule Nombre / % des collectés ; durées médianes en tableau de chaleur ; axes et grilles adoucis.
- Couleur propre à chaque type d'échantillon, commune au web et au mobile (palette catégorielle validée).
- Tableau de répartition (régions / districts / sites) : tri par colonne (clic sur l'en-tête), pagination (25 / 50 / 100 lignes) et export CSV pour Excel du niveau affiché (toutes les lignes).
- Écrans étroits (tablette, mobile) : les blocs et graphiques suivent la largeur de l'écran au lieu d'être tronqués.

### Administration des utilisateurs

- Page « Modifier l'utilisateur » : après enregistrement, l'identifiant s'affichait vide et l'en-tête « @null » (le champ désactivé n'est pas soumis par le navigateur). Le login est repris de la base. L'ID technique n'est plus affiché.

## 2026-05-29 — Intégration OpenELIS consolidé (oedatarepo) + page de suivi (v2.2.2)

### Intégration OpenELIS (récupération statut/dates d'analyse)

- **Synchronisation par `labNumber`** : LSTracker interroge le serveur OpenELIS consolidé (`oedatarepo`) pour récupérer le statut consolidé et les dates d'analyse d'un échantillon, et applique le mapping `RESULT_READY → ANALYSIS_DONE`, `ANALYSIS_FAILED`, `NON_CONFORM` (+ dates `analysis_completed_date` / `analysis_released_date`). Client JWT avec cache/renouvellement de token et retry sur 401.
- **Trois déclenchements** partageant la même logique de lot (`OeAnalysisBatchService`, avec garde de concurrence) : job planifié (`@Scheduled`, 30 min par défaut), refresh ciblé, et page d'administration.
- **Compteur de tentatives + épuisement** : un `labNumber` absent d'oedatarepo (404) est ré-essayé jusqu'à `OEDATAREPO_MAX_ATTEMPTS` (5) puis exclu des passages automatiques, réintroduit par un *reset* admin. Un labno présent mais en cours (`PENDING`) ou résolu (`UPDATED`) remet le compteur à zéro (auto-guérison).

### Page d'administration `/sync-openelis` (ADMIN / SUPER_ADMIN)

- *Administration → Interopérabilité → Suivi Synchro. OpenELIS*. Trois panneaux : **état** de l'intégration (connexion, dernier/prochain passage, config) avec bouton « Lancer maintenant » ; **liste des éligibles** avec prévisualisation à la demande (sans persister), refresh et reset par ligne, marqueur d'épuisement ; **historique** des exécutions.

### Robustesse

- `labNumber` non interrogeable (contient `/`, `\`, `?`, `#`, `%`) classé `NOT_FOUND` sans appel réseau — évite les `400 Bad Request` Tomcat sur slash encodé.
- Messages d'erreur HTTP tronqués (plus de corps HTML brut dans les logs ni l'UI).
- Pages d'erreur **404 / générique** personnalisées (`templates/error/`).

### Schéma & configuration

- Nouvelles tables `oedatarepo_sample_sync` (suivi par échantillon) et `oedatarepo_sync_run` (historique) — changeset Liquibase dédié.
- Variables `OEDATAREPO_ENABLED/URL/USER/PASSWORD` + réglages `OEDATAREPO_BATCH_SIZE/INTERVAL_MS/MAX_ATTEMPTS`. Désactivé par défaut.
- Documentation : [docs/INTEGRATION_OEDATAREPO.md](docs/INTEGRATION_OEDATAREPO.md).

## 2026-05-25 — Audit cohérence + refonte UI dashboard + rapports

### Sécurité

- **Rapports Jasper appliquent désormais l'UserScope** (correctif critique) : un utilisateur scopé région/district recevait jusqu'ici des PDF avec des données nationales si aucun filtre region/district n'était explicitement coché. `accessibleSiteIds` est désormais propagé du `ReportController` jusqu'aux 9 requêtes SQL de `ReportJdbcRepository`.

### Bugs de cohérence des chiffres (audit)

- **Filtres géographiques cassés dans les séries temporelles du dashboard** : dans `DashboardNativeRepository.tsCollected/tsDeposited/tsAnalysed/tsDelivered`, les conditions `region/district/site/lab/accessibleSiteIds` étaient accrochées au `ON` d'un `LEFT JOIN`, donc silencieusement inopérantes. Réécriture en `generate_series LEFT JOIN (subquery)` qui place les filtres dans un `WHERE` correctement scopé.
- **`SampleRepository.rawSummaryWithStatus`** : requête fausse (jointure `site.id = sample_retrieving_id` mélangeait deux clés étrangères), inutilisée. Supprimée.
- **`DashboardNativeRepository.summary` : COALESCE faux sur `at_lab`** : la métrique utilisait `COALESCE(deliver_at_lab_date, deliver_at_hub_date)` ce qui faisait que tous les samples passés par hub étaient comptés dans « Au labo », rendant `at_hub = at_lab`. Corrigé : `at_lab` filtre uniquement sur `deliver_at_lab_date`.
- **« Au hub » durci** avec `hub_id IS NOT NULL` côté dashboard et rapports — exclut les samples avec date de hub mais sans hub_id (donnée dégradée).

### Refonte de la définition des métriques (déf C « activité fenêtre »)

Toutes les surfaces (dashboard summary, dashboard funnel, rapports Jasper) utilisent désormais la même définition par métrique :

- **Total collectés** : `collection_date BETWEEN`
- **En transit** : snapshot global `status = 'ON_TRANSIT'` (non lié à la période)
- **Au hub** : `hub_id IS NOT NULL AND deliver_at_hub_date BETWEEN`
- **Au labo** : `deliver_at_lab_date BETWEEN`
- **Analysés** : `analysis_released_date BETWEEN`
- **Résultats collectés** : `result_collection_date BETWEEN`
- **Résultats livrés** : `result_delivery_date BETWEEN`
- **Non-conformités** : `status = 'NON_CONFORM' AND rejection_date BETWEEN`
- **Échecs d'analyse** : `status = 'ANALYSIS_FAILED' AND analysis_completed_date BETWEEN`
- **TAT canonique** : médiane(`result_delivery_date - collection_date`) sur les samples livrés. Sauf le widget « Labos les plus lents » qui garde `analysis_released_date - deliver_at_lab_date` (segment labo seul, justifié) mais passe en médiane.

### Rapports Jasper

- **`lab_kind` mutuellement exclusif** dans `receivedByTypeAndLabKind` :
  - RELAIS : `hub_id IS NOT NULL AND deliver_at_lab_date IS NULL`
  - DISTRICT / CAT / BM : déposé au lab final, lecture du `lab.lab_type`
  - Somme des 4 colonnes = total reçus dans la fenêtre.
- **Mise en page** : 4 rapports (conveyor, district, lab, region) — police des titres de section réduite (10 → 8 pt), zone « Observations » repositionnée au même niveau que « Nom et prénoms, Date et Signature », libellé « BM » remplacé par « Laboratoire de référence (Biologie Moléculaire) ».
- Recompilation des 6 `.jasper`.

### Dashboard web — UI

- **Refonte des KPI cards** en style « icône en bulle » (icône colorée dans une pastille à gauche, valeur saillante, couleurs respectant le contraste WCAG AA).
- **Cards réorganisées sur 2 rangées** : 7 cards de parcours en haut (auto-fit), 3 cards d'incident + performance en bas (3 colonnes égales).
- **Zone funnel supprimée** : devenue redondante avec les cards depuis l'alignement déf C.
  - Note : un bug de double-comptage avait été identifié dans le JS funnel (`at_hub + at_lab + analysed + result_collected + delivered` re-cumulait des valeurs déjà distinctes). Le funnel SQL renvoyait des bons chiffres ; c'est le JS qui les inflatait.
- **« Rejets » scindé en 2 cards** : « Non-conformités » et « Échecs d'analyse » (aligné sur le modèle mobile).
- **`delivered` du summary corrigé** : était calculé par `allCount - inTransit` (faux conceptuellement). Devient `result_on_site` directement.
- **Documentation contextuelle** : bandeaux explicatifs au-dessus de chaque zone du dashboard ; tooltips actifs (`data-tip` + CSS instantané) sur chaque carte avec la formule métier précise.

### Pages /sample, /home — UI

- Layout `/sample` : filtre avancé collapsable, table sortable côté serveur, tooltips, etc. (avant l'audit).
- Filtres dashboard alignés sur le même style que `/sample`.

### Misc

- Favicon `/report` : version font-awesome corrigée (5.1.0 → 5.15.4, version inexistante en webjars).
