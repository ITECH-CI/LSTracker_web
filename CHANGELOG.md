# Changelog — LabSampleTracker (web + backend)

La version 2.2.3 regroupe les trois sections datées du 2026-09-23, du 2026-09-24 et du 2026-11-26. Elle s'accompagne de l'application mobile 2.2.3 (build 10), dont le détail figure dans le CHANGELOG du dépôt mobile.

## 2026-11-26 — Habilitations, filtres et indicateurs du tableau de bord (v2.2.3)

### Profils superviseur régional et superviseur de district

- Administration centralisée : seuls l'administrateur national (ADMIN) et l'administrateur système (SUPER_ADMIN) écrivent dans l'administration (comptes, maillage, référentiels). Aucun rôle d'administration n'est délégué aux régions ni aux districts.
- Deux rôles nouveaux, ajoutés par changeset Liquibase : **SUPERVISEUR_REGION** et **SUPERVISEUR_DISTRICT**. Consultation et export limités aux régions ou aux districts affectés, sur le tableau de bord, la liste, la fiche, l'export, les rapports et l'API mobile ; ni accès par le laboratoire destinataire, ni écran d'administration (refus en 403 côté serveur).
- Formulaire de compte : libellés des profils (« Administrateur national », « Administrateur système », « Pilotage national (consultation) », « Superviseur régional », « Superviseur de district », « Technicien ou superviseur de laboratoire », « Convoyeur », « Utilisateur (consultation) ») ; un superviseur doit être affecté à au moins une zone de son niveau, son niveau et son type sont fixés par le rôle. Seul un administrateur système attribue ou retire le rôle Administrateur système. L'écran d'affectation s'intitule « Affecter les zones ».
- Les profils de consultation ne saisissent plus d'échantillon : la saisie web est réservée aux administrateurs, l'API mobile la refuse. À la connexion mobile, ils reçoivent le rôle de consultation et ouvrent le tableau de bord de consultation au lieu de « Rôle non reconnu ».
- Le bouton de suppression de compte, sans point d'accès, est retiré : un compte se désactive.

### Sécurité du périmètre

- Refus par défaut : un compte non national affecté à une région ou à un district sans site voyait toutes les données (une liste de sites vide valait « aucune restriction »). Ce cas renvoie désormais un résultat vide. SUPER_ADMIN rejoint les rôles nationaux.
- Listes Région, District, Site et Laboratoire du tableau de bord et de la liste des échantillons limitées au périmètre du compte, cascade comprise.
- `GET /sample/update/{id}` hors périmètre : refus (403) avant la construction du formulaire, même réponse que pour un échantillon inconnu.
- Accès à un échantillon sans collecte par son site demandeur, selon la même règle de zone que la liste et le tableau de bord.
- Points d'accès retirés : `GET /sample/{id}` (vue vide, erreur 500, non appelé) et `POST /sample` (enregistrement sans collecte ni contrôle de périmètre, non utilisé).

### Connexion et mots de passe

- Messages avant blocage : à partir du 3e échec consécutif, la page de connexion et `/api_v2/auth/login` indiquent le nombre de tentatives restantes ; au 5e, « Compte bloqué après 5 tentatives. Contactez l'administration centrale. » Le compteur et les messages valent pour tout identifiant saisi, existant ou non, pour ne pas révéler quels comptes existent ; seul un compte réel est verrouillé en base. Le déverrouillage par un administrateur remet le compteur à zéro.
- Mot de passe temporaire : la réinitialisation fixait l'expiration à l'instant même et rendait le compte inutilisable. Le mot de passe temporaire permet de se connecter, puis chaque page renvoie vers le changement de mot de passe jusqu'au choix d'un nouveau. Changer son mot de passe ferme les autres sessions du compte, et non plus la session en cours.
- `/api_v2/auth/login` renvoie l'indicateur `password_reset` ; l'API n'impose pas encore le changement.
- Jeton anti-falsification (CSRF) des appels en arrière-plan chargé sur toutes les pages : les actions de l'administration des comptes (désactiver, verrouiller, réinitialiser un mot de passe) ne reçoivent plus de 403.

### Filtres du tableau de bord

- Option « Tout » (valeur 0) en tête de chaque liste de filtre, sélectionnée par défaut.
- Filtre « Type de labo » (valeurs de `lab.lab_type`, libellés lisibles), qui restreint aussi la liste des laboratoires, et filtre « Type d'échantillon ».
- Les filtres passent par un objet unique (`DashboardFilter`) appliqué par toutes les requêtes du tableau de bord, des rapports et du contrôle de cohérence, en plus du périmètre de l'utilisateur. L'en-tête de l'export CSV les mentionne. `DashboardRepository`, inutilisé, est supprimé.
- Liste des échantillons : filtre « Type de labo » (laboratoire de destination), date de fin incluant toute la journée, comptage appliquant le filtre « nature », export CSV corrigé pour le type d'échantillon.

### Présentation et indicateurs

- Retrait de la comparaison avec la période précédente : bandeau « Comparée à », tendances des cartes, mentions dans les infobulles. Le point d'accès `funnel-previous` reste disponible.
- Tableau « Répartition par zone géographique » : niveau courant dans le sous-titre et le fil d'Ariane ; colonnes « Rang » et « Écart moy. » retirées ; en-têtes explicites (« Échantillons collectés », « Échantillons en cours d'acheminement », « Échantillons déposés au laboratoire », « Échantillons non conformes », « Échecs d'analyse », « Résultats rendus aux sites », « TAT médian ») ; colonne « Détails » ouvrant une ligne dépliable (types d'échantillon, durée médiane par étape, non-conformités par motif, lien vers la liste filtrée).
- Carte « Circuits » à la place de « Convoyeurs les plus actifs » : collectes, dépôts, délai médian d'acheminement (collecte au premier dépôt) et sites desservis. Rattachement : circuit du trajet s'il existe ; sinon circuit du site (collecte, à défaut site demandeur) s'il n'appartient qu'à un seul circuit actif ; ligne « Sites partagés entre plusieurs circuits » s'il en a plusieurs ; « Non rattaché » s'il n'en a aucun. Seuls les circuits actifs reçoivent des échantillons. Chaque échantillon est compté une fois : la somme des lignes égale le total collecté, ce que vérifie le contrôle de cohérence. La mini-carte « Convoyeurs actifs » est conservée.
- « En transit » devient « En cours d'acheminement » : tableau de bord, export CSV, contrôle de cohérence, libellé du statut ON_TRANSIT (changeset Liquibase).
- Le TAT s'affiche sans coloration (les seuils de 3 et 7 jours ne correspondaient à aucune cible).

### Libellés

- Tableau de bord : « Étapes de l'échantillon », « Étapes des échantillons par type », « Évolution des échantillons dans le temps », « Échantillons collectés par type », « Durée médiane par étape, en jours (échantillons de la période) », « en cours d'acheminement actuellement », « Résultats récupérés au laboratoire », « Résultats rendus aux sites », « Laboratoires les plus lents (délai au laboratoire) », « Échantillons non conformes par site (taux) ». Infobulles et contrôle de cohérence alignés.
- Statut RESULT_COLLECTED : libellé « RESULTATS RECUPERES » (changeset Liquibase ; un libellé modifié depuis l'administration est conservé). Les deux changements de libellé de statut sont regroupés dans `rename_sample_status_labels.xml`.
- Textes affichés sans tiret cadratin ni demi-cadratin : valeur absente « - », pagination « 1 à 25 sur 120 », période des rapports PDF « X au Y ». Rapports Jasper recompilés (même version de JasperReports).

### Échantillons sans collecte

- La zone d'un échantillon est celle de son site de collecte, à défaut celle de son site demandeur, dans toutes les requêtes d'indicateurs, les rapports et la liste. Les répartitions ajoutent à chaque niveau une ligne « Zone non renseignée » (comptes nationaux seulement) : leurs totaux égalent ceux des cartes.
- Création et modification refusées pour un échantillon sans collecte ni site demandeur, avec un message affiché par l'écran de modification ; la fiche d'un tel échantillon ne plante plus.
- Contrainte `ck_sample_collection_or_requester` posée en `NOT VALID` (changeset Liquibase) : les lignes existantes ne sont pas vérifiées, toute insertion ou modification doit la respecter. Sa validation fera l'objet d'un changeset distinct, une fois les échantillons concernés rattachés ou supprimés.
- L'échec d'enregistrement d'une collecte remonte avec sa cause au lieu de renvoyer null.

### Tests et intégration continue

- Tests d'intégration sur PostgreSQL (Testcontainers 1.21.4, schéma créé par Liquibase) : cloisonnement des superviseurs sur toutes les surfaces, compte sans affectation, actions d'administration avec jeton CSRF, mot de passe temporaire, carte Circuits, échantillons sans zone.
- CI : `actions/checkout` et `actions/setup-java` en v5.

### Mise à jour

- Quatre changesets Liquibase s'appliquent au démarrage : rôles des superviseurs (`add_supervisor_roles.xml`), libellés des statuts ON_TRANSIT et RESULT_COLLECTED (`rename_sample_status_labels.xml`), contrainte `ck_sample_collection_or_requester` non validée (`add_sample_zone_check.xml`).
- Comptes de consultation régionaux ou de district : à reprendre avec les nouveaux profils et une affectation de zone ; un compte non national sans site exploitable ne voit plus aucune donnée.
- Avant la migration de la production : vérifications de `docs/DOSSIER_DAP_2.2.3.md` (sites sans circuit ou partagés, échantillons sans collecte, années aberrantes, région de test).

## 2026-09-24 — Qualité des dates, connexions et journal d'activité (v2.2.3)

### Correctif : utilisateurs de niveau central sans données

- L'accès national n'était accordé que selon le rôle (ADMIN, SUPPORT, MANAGER). Un utilisateur de niveau central avec le rôle « USER » — sans rattachement, puisqu'il couvre tout le pays — avait un périmètre vide : tableau de bord, listes, rapports et exports vides. Règle unique désormais : rôle national **ou** niveau central = accès à tout le pays, sur le web, dans les métadonnées mobiles et à la synchronisation.
- Le rôle **MANAGER** (pilotage national) était reconnu par l'application mais absent des listes de création et de modification des utilisateurs : il est désormais proposé. Il voit les données de tout le pays et **consulte** l'administration (maillage, référentiels, utilisateurs, journaux, manuels, synchronisation OpenELIS) sans rien pouvoir créer, modifier, désactiver ou supprimer — y compris les profils des autres utilisateurs. Contrôle côté serveur, route par route ; boutons d'écriture masqués.
- Sécurité : neuf anciens contrôleurs générés, sans écran (`/approle`, `/appuserhasrole`, `/sampleretrieving`, `/trackingevent`, `/ride`…), acceptaient des créations de tout utilisateur connecté ; ils sont réservés aux administrateurs. Les suppressions de types d'échantillon et de motifs de rejet passent d'un lien (GET) à un POST protégé.

### Correctif : retour après connexion

- Après connexion, l'utilisateur pouvait arriver sur l'icône du site (`/favicon-<empreinte>.ico?continue`) au lieu de l'accueil : le favicon, renommé avec une empreinte pour le cache navigateur, n'était plus reconnu comme ressource publique, et Spring Security le mémorisait comme page à rouvrir. Le favicon est désormais public (il s'affiche aussi sur la page de connexion), et seule une vraie page (GET, HTML) peut servir de destination après connexion — jamais une image, une police ou un appel en arrière-plan.

### Dates aberrantes

- Base de production (restaurée en démonstration) : 423 échantillons sur 46 392 portent une année mal tapée, saisie avant la validation des dates — surtout « 0025 » pour 2025 (août-octobre 2025), aussi 0204, 2028, 2055, 20244. Ces échantillons sortaient de toutes les périodes du tableau de bord ; les médianes affichées n'en étaient presque pas affectées.
- Saisie : toute date antérieure au 01/01/2024 (mise en service) est refusée, comme les dates futures (`SampleDateValidator`, web et synchronisation mobile ; bornes des champs du formulaire web). Le mobile était déjà protégé par ses sélecteurs de date.
- Script `scripts/sql/dates_aberrantes.sql` : audit (lecture seule), puis correction avec `-v corriger=1`. L'année corrigée est la plus proche de la date d'enregistrement (`created_at`) ; un échantillon n'est corrigé que si ses dates corrigées respectent les règles de saisie, sinon il est listé « à vérifier à la main ». Valeurs d'origine gardées dans `sample_dates_avant_correction` ; annulation automatique si le contrôle final échoue. À appliquer en démonstration, puis en production lors de la migration.

### Connexions et fréquentation

- Journal des connexions (`connection_log`) : chaque tentative, web ou mobile, réussie ou non (mauvais mot de passe, compte verrouillé, désactivé, expiré), avec l'adresse IP et le navigateur ou l'application. Les réussites sont limitées aux connexions explicites (formulaire web, `/api_v2/auth/login`) ; les échecs sont tous enregistrés, identifiant inconnu compris.
- Visites (`user_activity_day`) : un utilisateur actif un jour donné, sur un canal ; au plus une écriture par utilisateur, jour et canal.
- Pied de page, sur une ligne : utilisateurs en ligne (actifs dans les 15 dernières minutes, web et mobile), utilisateurs et visites du mois. Totaux seulement ; chargé une fois par page, pour ne pas maintenir la session ouverte.
- Page Administration → Connexions : en ligne maintenant, visites par jour, fréquentation par type de compte et niveau, comptes inactifs (jamais connectés en tête), échecs regroupés par identifiant et adresse, journal filtrable et exportable (CSV).
- Correctif : `@EnableWebMvc` privait les filtres de sécurité de la requête courante (`RequestContextHolder`) ; le filtre standard de Spring Boot est rétabli (`WebConfig`).

### Journal d'activité (cahier V, journalisation)

- Toute création, modification ou suppression enregistrée par JPA est journalisée (`activity_log`) : auteur, date, canal (web, mobile, système), objet et **valeur antérieure** de chaque champ modifié — échantillons (web, synchronisation mobile, synchronisation OpenELIS), utilisateurs et périmètres, maillage, référentiels, manuels.
- Les entrées d'une transaction ne sont écrites qu'après sa validation ; un périmètre réenregistré à l'identique ne laisse aucune trace (retrait + ajout identiques annulés). Champs techniques ignorés (version, dernière connexion…) ; mot de passe et identifiant patient jamais stockés (« masqué »).
- Exports journalisés : CSV des échantillons, rapports PDF, exports des journaux.
- Page Administration → Journal d'activité : période, filtres (utilisateur, objet, n°, action, canal), références affichées par leur nom (statut, labo, site…), historique d'un objet en un clic, export CSV. Bouton « Historique » sur la fiche de modification d'un échantillon (administrateurs).
- Journal des connexions, visites et journal d'activité conservés 12 mois (purge quotidienne, une seule instance) ; consultation par les administrateurs et le profil Pilotage national (MANAGER, `@AdminRead`).

### Maillage : désactivation plutôt que suppression (cahier IV.b, IV.f)

- Régions, districts et sites ont désormais un état actif / inactif (les laboratoires et circuits l'avaient déjà). Un élément utilisé (rattachements, échantillons, utilisateurs) ne peut plus être supprimé : il est désactivé, disparaît des listes de saisie (web et mobile) et garde son historique. Un parent ne peut être désactivé tant qu'il a des enfants actifs, ni un enfant réactivé sous un parent inactif.
- Listes d'administration : état affiché (« Inactif »), inactifs en fin de liste, boutons Désactiver / Réactiver / Supprimer.
- Taux de couverture : dénominateurs limités aux éléments actifs.
- Sécurité : les écrans d'administration des sites, laboratoires et circuits, et la suppression des régions et districts, étaient accessibles à tout utilisateur connecté ; ils sont réservés aux administrateurs. La suppression se faisait par un simple lien (GET), déclenchable à l'insu d'un administrateur depuis une autre page : elle passe en POST avec jeton anti-falsification.

### Exports : date, période et périmètre (cahier V.5)

- Export CSV des échantillons : en-tête avec la date et l'auteur de l'export, la période (date de collecte), le périmètre (zone demandée, et accès national ou restriction de l'utilisateur) et les filtres ; fichier horodaté, encodé en UTF-8 lisible directement par Excel.
- Export du tableau de répartition : il portait déjà ces mentions ; la colonne « TAT moyen » est renommée « TAT médian », conformément au calcul.
- Rapports PDF : date d'édition, période et zone déjà présentes.

### Contrôle de cohérence des indicateurs (cahier VI.4, V.5, XII.3)

- Page de contrôle de cohérence, ouverte aux administrateurs et au profil Pilotage national (MANAGER, `@AdminRead`), accessible à l'adresse `/coherence` (outil de recette, sans entrée de menu) : pour une période et une région, chaque indicateur (collectés, reçus au laboratoire, analysés, résultats livrés, non-conformités, échecs) est recalculé par le code réel de chaque écran — répartitions régions / districts / sites, répartition par type, courbes, parcours par type, rapports PDF — et comparé aux cartes du tableau de bord ; l'additivité site → district → région est vérifiée. Verdict global et liste des écarts : outil de recette.
- Écart trouvé et corrigé : le rapport laboratoire (« Nombre de résultats disponibles ») ne comptait que les résultats encore en attente de récupération (16 contre 991 « analysés » sur la base de test) ; il compte désormais tous les résultats validés sur la période, comme le tableau de bord.

### Réglages documentés (cahier VII.2)

- `docs/REGLAGES.md` : paramètres PostgreSQL retenus (production et démonstration) avec leur règle de calcul, limites des conteneurs, pool de connexions, JVM, lots, délais et purges ; vérification du dimensionnement du serveur à faire avant la migration de la production (les valeurs de production réservent 27 Gio et plafonnent à 46 Gio).

### Test de montée en charge (cahier XI) et deux défauts corrigés

- `scripts/charge/test_charge.py` : utilisateurs web et mobiles simultanés par paliers ; rapport `docs/TEST_CHARGE.md` (600 000 échantillons).
- Défaut corrigé : les répartitions par site ou district pouvaient tourner plusieurs minutes (plan d'exécution « générique » de PostgreSQL ignorant les filtres optionnels), épuisant le pool de connexions : effondrement à 50 utilisateurs. Plans calculés avec les valeurs réelles et durée maximale de 2 minutes par requête. Résultat à 50 utilisateurs : 0 erreur et 46,5 requêtes/s, contre 34 erreurs et 4,7 requêtes/s.
- Défaut corrigé : mémoire partagée de 64 Mo par défaut du conteneur PostgreSQL, épuisée par les requêtes parallèles ; `shm_size` fixé (1 Go en production, 256 Mo en démonstration).
- Reste à améliorer : comptage total de la liste des échantillons (4,5 s à 50 utilisateurs sur 600 000 échantillons).

### Documentation d'architecture (livrable n° 4, cahier XII.1)

- `docs/ARCHITECTURE.md` : composants, cycle de vie de l'échantillon, modules, données, sécurité et périmètre, traçabilité, indicateurs, mobile, intégration OpenELIS, déploiement.

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
- **Distance totale et km moyen corrigés** : seuls les trajets exploitables comptent (départ renseigné et non nul, arrivée après le départ, 1 000 km au plus), et un trajet partagé par plusieurs échantillons n'est compté qu'une fois. Les données 2024 de l'ancienne application portent un départ à 0 sur le trajet des résultats : la « distance » était le compteur entier du véhicule (386 M km affichés sur la démo, ~238 500 km après correction). Nombre de trajets et de relevés écartés affichés.

### Sécurité — périmètre des données (cahier V.3)

- Un filtre région / district / site levait la restriction aux sites de l'utilisateur : un compte restreint voyait toute la région filtrée. Corrigé (`UserScopeService.intersect`) ; seul un filtre labo seul lève encore la restriction.
- L'export CSV des échantillons (`/sample/csv`) ignorait le périmètre et renvoyait tous les échantillons du pays, identifiants patients compris. Corrigé.
- La fiche, les détails et la modification d'un échantillon étaient accessibles par identifiant hors périmètre. Corrigé (`canAccessSample`) ; un identifiant hors périmètre est traité comme inexistant.

### Conformité au cahier (VI.2, VI.3, VI.4)

- Répartition région / district / site : filtres de l'écran appliqués, non-conformités et échecs d'analyse en colonnes distinctes, rang et écart à la moyenne (affichés et exportés).
- Taux de non-conformité par site (sans les échecs d'analyse), détaillé par type d'échantillon.
- Convoyeurs : collectes, dépôts et délai médian d'acheminement.
- TAT : définition unique (`TatSql`) sur le tableau de bord, les rapports, la liste et l'export ; tri de la liste par TAT corrigé ; code mort à définitions concurrentes supprimé.

### Performance (VII.1)

- Sur 600 000 échantillons simulés, requêtes du tableau de bord de 2,35 s à 0,87 s (répartition par site : 542 → 33 ms) : filtres de date indexables, agrégation avant jointure géographique, index de jointure (changeset 004).
- CSS et JS du tableau de bord en fichiers statiques avec empreinte de contenu, mis en cache un an (gabarit de 78 à 23 Ko).

### Exploitation (IX.2)

- Synchronisation OpenELIS : verrou partagé entre instances (verrou consultatif PostgreSQL).
- Supervision : points de gestion sur un port interne (9300), métriques Prometheus, santé détaillée ; pile `docker-compose.monitoring.yml` (Prometheus, Alertmanager, exportateurs système et PostgreSQL, 11 règles d'alerte). Guide : `docs/SUPERVISION.md`. **Le healthcheck du conteneur passe sur le port 9300 : déployer l'image avec les composes du même bundle.**

### Administration (observation 3.2)

- Manuels d'aide téléversables (Administration → Contenu → Manuels d'aide) : manuel utilisateur et manuel de procédure, servis par le menu Aide (changeset 005).

### Corrections issues de la revue de code du 23/09

- Indicateurs de performance : les trois classements (sites à rejet élevé, labos lents, convoyeurs) suivent désormais les filtres région / district / site / labo de l'écran, comme le reste du tableau de bord (ils restaient au niveau national).
- Lien partagé avec un paramètre `period` invalide : la page ne reste plus blanche (valeur contrôlée contre la liste des périodes).
- Export CSV : protection contre l'injection de formule (texte commençant par = + - @) et en-tête horodaté indiquant la période et le périmètre (cahier V.5).
- Classements : échappement HTML appliqué au rendu, pour tous les champs.
- CI : tests unitaires lancés à chaque push (`.github/workflows/ci.yml`).

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
