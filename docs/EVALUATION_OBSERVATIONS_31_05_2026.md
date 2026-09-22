# Évaluation des travaux — Observations du 31/05/2026

*Révision du 22/09/2026. LSTracker web 2.2.2 + mobile 2.2.2 + oedatarepo 2.4.0.*

Chaque point du document d'observations a été confronté au code source, puis —
pour les points vérifiables — **rejoué sur une base de données réelle** (2 995
échantillons). Les références de fichier et de ligne permettent de contrôler
chaque verdict.

> **Ce qui change depuis la première version.** Trois observations que nous
> avions déclarées fondées ne le sont pas : la vérification sur données
> réelles les a infirmées (1.7, 1.9, 4.2). Deux erreurs de notre première
> analyse sont corrigées (voir §8). Plusieurs charges sont revues à la baisse.
> Les lots 0 et 1 sont livrés.

---

## Avertissement méthodologique — confirmé, et déterminant

Le document d'observations précise en en-tête : *« Cette nouvelle version
utilise l'ancienne base de données du serveur test, pas la version en
production. »*

Cet avertissement s'est révélé **plus structurant que prévu**. Il explique à lui
seul trois des observations, désormais classées non fondées :

| Point | Symptôme observé | Cause réelle |
|---|---|---|
| 1.1 | Collectés < transmis, gap de 247 | Axes-dates distincts, comportement documenté |
| 1.7 | Types TB / HPV / PrEP absents | Types présents au référentiel, mais **0 échantillon** en base |
| 1.9 | Le bouton « développer » n'affiche rien | Sur la période par défaut, **2 régions sur 34** ont des données |

Sur la période par défaut du tableau de bord (`aujourd'hui − 2 ans`, soit
sept. 2024 → sept. 2026), la base de test ne contient que **17 échantillons**
répartis sur 2 régions — alors qu'elle en compte 2 967 sur 2024-2025, les
données s'arrêtant en mai 2026. Un testeur qui déplie une région tombe donc
presque à coup sûr sur une zone vide.

**Recommandation inchangée, et renforcée : rejouer la revue sur un jeu de
données représentatif de la production**, en vérifiant d'abord que la période
sélectionnée couvre les données.

## Légende des verdicts

| | Signification |
|---|---|
| ✅ | **Fondé** — défaut confirmé dans le code |
| 🟡 | **Fondé, mal diagnostiqué** — le symptôme est réel, la cause est ailleurs |
| 🔵 | **Évolution** — pas un défaut, une fonctionnalité nouvelle |
| ⚪ | **Non fondé** — comportement voulu, ou artefact du jeu de données |
| 🔴 | **À ne pas faire tel quel** — voir l'argumentaire |
| ✔︎ | **Livré** — développé, testé et commité |

---

## Synthèse

Sur **23 points** relevés :

- **2 défauts réels confirmés** dans le code — tous deux livrés
- **3 observations non fondées**, établies par vérification sur données réelles
- **2 points à refuser ou renégocier** avant toute mise en œuvre
- **1 point qui n'est pas un correctif** mais un chantier d'interopérabilité
- le reste se répartit entre libellés, évolutions et fonctionnalités mobiles

**Les deux trouvailles que le document d'observations ne soupçonnait pas :**

1. **Le TAT ne se figeait jamais** — pas seulement pour les rejetés comme
   signalé, mais pour **tous** les échantillons clos, y compris les dossiers
   terminés avec succès. Mesuré sur 982 dossiers : 818 jours affichés au lieu
   de 60. Corrigé.
2. **Trois écrans d'administration n'existaient pas du tout.** Ce n'était pas
   un problème d'affichage sur tablette : les contrôleurs retournaient un nom
   de vue vide et aucun gabarit n'avait jamais été écrit. Construits.

**Quatre défauts supplémentaires**, absents des observations, ont été trouvés
en testant (§7) — dont deux de sécurité et une suppression sans confirmation.

**Charge restante estimée** : ~31 jours-homme hors interopérabilité,
~61 jours-homme avec.

---

## 1. LSTracker web — Tableau de bord

### 1.1 ⚪🟡 « Collectés inférieur aux transmis au laboratoire, gap de 247 »

**Verdict : non fondé comme anomalie de calcul, fondé comme problème de
lisibilité.**

Le comportement est **documenté dans le code**,
`dao/DashboardNativeRepository.java` lignes 38-46 :

> *« Chaque métrique est filtrée sur SON axe-date d'étape (activité fenêtre,
> déf. "C"). […] Conséquence assumée : la métaphore "funnel" ne tient plus
> strictement (received peut être > collected pour une période courte si du
> backlog antérieur arrive au labo dans la fenêtre). »*

Un échantillon collecté en avril et déposé au laboratoire en mai compte dans
« déposés » de mai, mais pas dans « collectés » de mai. L'écart est donc
**mathématiquement attendu**, et d'autant plus visible que la période est
courte.

**Mais l'observation reste utile** : si un relecteur averti tombe dans le
piège, les utilisateurs de terrain y tomberont aussi.

| Option | Effet | Charge |
|---|---|---|
| Renforcer le bandeau explicatif et les info-bulles | Documente la surprise sans la supprimer | 0,5 j |
| **Ajouter un mode « cohorte »** en bascule | Restitue un entonnoir strict. Règle le sujet | 3-4 j |

**Recommandation** : le mode cohorte. C'est ce que les utilisateurs attendent
intuitivement, sans renoncer à la définition « activité période », qui reste la
bonne pour le pilotage.

### 1.2 ✔︎ Remplacer les icônes « ? » par « i » — **livré**

Fondé. Les 11 pastilles d'aide de `home/index.html` utilisaient le caractère
`?` — un point d'interrogation suggère une question sans réponse là où il
s'agit d'une définition.

Le texte du bandeau, qui invitait lui-même à survoler « les ? », a été mis à
jour dans le même geste.

*(Le document annonçait 13 occurrences entre les lignes 613 et 670 ; il y en
avait 11, de la ligne 613 à la ligne 676.)*

### 1.3 ✔︎ Renommer « AU HUB » et « AU LABO » — **livré**

Fondé et cohérent : hub = laboratoire relais, labo = laboratoire de référence.

Les cartes affichent désormais **« Déposés labo relais »** et **« Déposés labo
référence »**. Les libellés restent courts pour ne pas casser la grille
`auto-fit` ; la définition complète est passée en info-bulle, comme le
recommandait le document.

### 1.4 🔴 Renommer « ANALYSES » en « ÉCHANTILLONS DÉPOSÉS AU LABO (RELAIS ET RÉFÉRENCE) »

**À ne pas appliquer tel quel — ce n'est pas un renommage, c'est un changement
de sens.**

La carte « Analysés » compte `analysis_released_date` : des **résultats rendus
disponibles par le laboratoire**. Le libellé demandé décrit `au hub + au labo`,
c'est-à-dire la **somme des deux cartes précédentes** — une grandeur
entièrement différente.

Appliquer ce libellé rendrait le chiffre faux au regard de son intitulé. C'est
exactement la classe de défaut que la version 2.2.x vient de corriger.

**Deux issues, à arbitrer avec le demandeur :**

- conserver « Analysés » avec une info-bulle plus explicite ;
- **ajouter** une carte « Total déposés (relais + référence) », qui est
  probablement le besoin réel.

**Charge si ajout de carte : 1 j**

### 1.5 🔵 Comparatif de période sans « vs préc. »

Évolution. La brique existe : endpoint `dashboard/data/funnel-previous`
(`DashboardController.java:286`, appel JS `home/index.html:1060`, rendu du
delta l.1083-1096). La période précédente est déjà calculée par différence de
longueur.

Travail réel : étendre le sélecteur de période (trimestre, semestre), calculer
la période précédente pour chacun des six pas demandés, et remplacer
l'affichage du pourcentage par la valeur du taux précédent.

**Charge : 2-3 j**

### 1.6 ✔︎ Couverture du programme — règle de calcul et dénominateur — **livré**

**Afficher la règle de calcul : fondé.** La carte indique désormais
« Distance totale ÷ total collectés », avec la définition complète en
info-bulle.

**« Le calcul est à revoir » : non fondé tel que formulé — mais il révélait une
vraie ambiguïté**, et surtout un défaut de données.

Le dénominateur est `COUNT(*) FROM scope_samples`, soit **tous les échantillons
du périmètre et de la période** (`DashboardAdvancedRepository.java:393`), et
non les seuls « collectés » au sens d'une étape aboutie.

Mesure sur la base de test :

| Dénominateur | Km moyen |
|---|---|
| Tous les échantillons du périmètre (actuel) | **2 124 km** |
| Seulement ceux ayant un trajet enregistré | 2 639 km |

L'écart de 24 % s'explique par **579 échantillons sur 2 969 (19 %) sans aucun
kilométrage saisi**. Ils entrent au dénominateur sans rien apporter au
numérateur.

**Décision retenue** : conserver le dénominateur actuel — le changer aurait
modifié le chiffre sans que l'utilisateur puisse le rapprocher de l'historique
— et **afficher le nombre d'échantillons sans kilométrage** à côté de la
valeur. Le défaut de saisie devient visible au lieu d'être silencieux.

> Ce point illustre l'intérêt de la vérification sur données réelles : la
> question posée était « la formule est-elle juste ? », la vraie réponse est
> « la formule est juste, mais un cinquième des données manque ».

### 1.7 ⚪ Absence des types TB, HPV, PrEP — **non fondé**

**Les trois types existent déjà.** Le référentiel en compte neuf :

```
BI(139)  BS(45)  CV(2391)  EID(238)  TB(182)  HPV(0)  PrEP(0)  IVSA(0)  Autre(0)
```

- **TB** a 182 échantillons et s'affiche normalement.
- **HPV, PrEP et IVSA** ont **0 échantillon** : leur absence à l'écran traduit
  une absence de données, pas un défaut logiciel.
- Les trois sont bien migrés (`sql/sample_type.sql` pour les six premiers,
  `sql/add_sample_type_01.sql` pour PrEP, IVSA et Autre) — vérifié en
  reconstruisant une base vierge depuis le changelog complet.

**Aucun développement nécessaire.** À revérifier sur données de production.

### 1.8 ✔︎ Icône de choix sur « Répartition par zone géographique » — **livré**

Un **sélecteur de niveau Régions / Districts / Sites** a été ajouté au-dessus du
tableau, permettant de consulter un niveau directement sans dérouler la
cascade.

Cela a demandé du travail backend non prévu : `by-district` exigeait un
paramètre `region` et `by-site` un `district`, donc aucune vue à plat n'était
servable. Ces paramètres sont devenus optionnels, et les requêtes renvoient le
contexte parent (un district affiche sa région, un site affiche
« région › district »). La cascade d'origine est préservée.

### 1.9 ⚪ Le bouton « développer » n'affiche rien — **non fondé**

**Le mécanisme fonctionne.** Reproduit puis vérifié endpoint par endpoint : sur
une région ayant des données, le dépliement renvoie bien ses districts
(HAUT-SASSANDRA → DALOA 12, ISSIA 2).

Le symptôme s'explique par le jeu de données : sur la période par défaut, seules
**2 régions sur 34** ont des données. Les 32 autres ont tous leurs districts à
zéro, et l'interface masque par défaut les lignes vides — d'où l'impression que
rien ne s'affiche.

**Aucun correctif nécessaire.** Deux améliorations d'ergonomie restent possibles
si le sujet doit être fermé définitivement : cocher « afficher les zones sans
données » par défaut, ou afficher un message explicite « aucune donnée sur la
période » plutôt qu'un dépliement silencieux. **0,5 j.**

### 1.10 ✔︎ Colonne ID avant « Zone » — **livré**

### 1.11 ✔︎ Renommer « zone géographique » → « région sanitaire » — **livré**

La section s'intitule « Répartition par région sanitaire » et l'en-tête de
colonne « Région / District / Site », conformément au découpage administratif
(33 régions sanitaires, 113 districts).

### 1.12 🔵 Reproduire le tableau pour les districts sanitaires

Le gabarit existe ; il s'agit de le décliner. **Le point 1.8 en a déjà livré une
partie** : la vue à plat « Districts » du sélecteur de niveau répond
partiellement au besoin.

**Charge : 1 j** *(était 1,5 j)*

### 1.13 🔵🔴 Tableau comparatif OpenELIS / LSTracker avec gap

**Le point le plus lourd du document — et il n'a pas sa place dans un lot de
corrections.**

Ce n'est pas un widget, c'est un **tableau de réconciliation inter-systèmes**.
Il suppose :

1. que le serveur consolidé OpenELIS expose des comptages par région, district,
   site et convoyeur — ce n'est pas le cas, l'API
   `/api/v1/order-analysis/{labno}` répond échantillon par échantillon ;
2. qu'une **correspondance fiable des référentiels géographiques** existe entre
   les deux systèmes. La table `fhir_location_site_map` existe côté oedatarepo,
   mais rien ne garantit son alignement avec le maillage LSTracker. Sans cet
   alignement, le « gap » affiché mesurera surtout des écarts de référentiel,
   pas des écarts de données.

**C'est un livrable du lot interopérabilité, pas du lot correctif.** Le produire
avant l'alignement des référentiels donnerait un tableau qui affiche du bruit —
et qui sera lu comme une mesure de qualité des données.

**Charge : 8-12 j, conditionnée au lot FHIR**

### 1.14 🔵 Classements régions / districts / laboratoires / convoyeurs

Quatre widgets « du plus actif au moins actif ». Le gabarit existe et est déjà
réutilisé deux fois (`slowestLabs` l.287, `topConveyors` l.324,
`topRejectionSites` l.253) : médiane par `PERCENTILE_CONT`, seuil de bruit
`HAVING COUNT >= :minSamples`, filtre de périmètre utilisateur.

⚠️ **Contradiction à lever** : la demande dit à la fois *« sur une période »* et
*« afficher les données en temps réel »*. À clarifier avant développement.

**Charge : 3-4 j**

---

## 2. LSTracker web — Échantillons

### 2.1 ✔︎✅ TAT qui n'arrête jamais d'incrémenter — **livré**

**Défaut confirmé, et nettement plus grave que ce que décrivait
l'observation.**

`dao/SampleRepository.java:458` appliquait `NOW()` **sans aucune condition de
statut** :

```sql
CAST(FLOOR(EXTRACT(EPOCH FROM (NOW() - s.collection_date)) / 86400) AS INT) AS tat_days
```

Le TAT croissait donc indéfiniment pour **tous** les échantillons clos — pas
seulement les rejetés signalés, mais aussi ceux en statut « Résultat sur site »,
c'est-à-dire les dossiers terminés avec succès.

**Mesure avant / après sur la base de test :**

| Statut | Avant | Après | Dossiers |
|---|---|---|---|
| `RESULT_ON_SITE` | 818 j | **60 j** | 982 |
| `NON_CONFORM` | 793 j | **25 j** | 64 |
| `ON_TRANSIT` | 581 j | 581 j *(inchangé, non terminal)* | 38 |
| `ANALYSIS_DONE` | 794 j | 794 j *(inchangé, non terminal)* | 11 |

**982 dossiers affichaient un TAT 13 fois trop élevé.**

**Correctif livré** : la date de fin dépend du statut terminal
(`RESULT_ON_SITE` → `result_delivery_date`, `NON_CONFORM` → `rejection_date`,
`ANALYSIS_FAILED` → `analysis_completed_date`, sinon `LOCALTIMESTAMP`).

Une remarque sur la structure, qui reste à traiter : **il existe trois
définitions concurrentes du TAT** dans la base de code, et la formule
`COALESCE(deliver_at_lab_date, deliver_at_hub_date, now()) − collection_date`
est dupliquée dans une **douzaine de requêtes** (l.128, 152, 177, 202, 227, 252,
278, 304, 330…), auxquelles s'ajoute un découpage TAT1/TAT2/TAT3 (l.330-333).
L'unification de ces définitions n'est pas faite.

**Charge restante (unification) : 2 j**

> Détail révélateur : juste sous la ligne fautive, le code calculait
> correctement `days_since_last_movement` avec un `GREATEST` sur toutes les
> dates d'étape, et le `sla_color` savait distinguer les statuts terminaux. La
> logique de fin de parcours existait donc déjà **dans la même requête** — elle
> n'avait simplement pas été appliquée au TAT.

---

## 3. LSTracker web — Administration

### 3.1 ✔︎✅🔴 « Erreur sur les menus types d'échantillons, statuts et types de rejets » — **livré**

**Fondé, mais le diagnostic était faux — et le problème réel bien plus large.**

Ce n'était pas un défaut d'affichage sur tablette. **Ces trois écrans
n'existaient pas.**

- `SampleTypeController`, `SampleStatusController` et
  `SampleRejectionTypeController` alimentaient leur modèle puis retournaient
  `""` — un nom de vue vide — à **cinq endroits chacun** (soit la totalité du
  CRUD, pas seulement l'index). Ce sont des squelettes générés (Telosys) jamais
  implémentés.
- **Aucun gabarit correspondant n'existait** dans `templates/`.
- Les liens étaient pourtant publiés dans le menu.

L'erreur se produisait donc sur poste de travail exactement comme sur tablette.

**Périmètre livré, volontairement différent selon l'entité :**

| Entité | Périmètre |
|---|---|
| `sample_type` | CRUD complet + garde d'intégrité |
| `sample_rejection_type` | CRUD complet + garde d'intégrité |
| `sample_status` | **Consultation + édition du libellé seul** |

La restriction sur les statuts est délibérée. Le champ `status` est la machine
d'état du suivi : ses codes sont référencés **en dur 36 fois dans 6 fichiers**
(tableaux de bord, rapports, calcul du TAT, `SampleServiceImpl`) et dans
l'application mobile. Un CRUD ouvert aurait permis de casser silencieusement
ces surfaces — y compris le correctif du TAT livré au point 2.1. Vérifié en
forçant `status` et `id` dans la requête POST : seul le libellé change.

Les gardes d'intégrité sont actives : supprimer `CV` (2 391 échantillons) est
refusé avec un message explicite ; un type à 0 échantillon se supprime
normalement.

> C'est le meilleur exemple à présenter au demandeur : une observation dont le
> diagnostic est faux, mais qui révèle un défaut plus important que celui
> signalé.

### 3.2 🔵 Manuel utilisateur et manuel de procédure téléversables

Légitime et bien vu : le manuel est aujourd'hui un fichier statique, sa mise à
jour suppose une intervention sur le code et un redéploiement.

Travail : téléversement, stockage, remplacement de version, exposition.
**Charge : 2 j**

### 3.3 ⚪ Synchronisation OpenELIS — « besoin d'échange »

Pas de développement. La documentation existe :
`docs/INTEGRATION_OEDATAREPO.md` (contrat d'API, mapping des statuts, machine
d'état, page d'administration, dépannage).

**Action : une séance de présentation d'une heure**, éventuellement complétée
d'une fiche d'une page à destination des administrateurs.

### 3.4 🔴🔴 « Visualiser les mots de passe de chaque utilisateur »

**À refuser. Ce point ne doit pas être mis en œuvre, sous aucune forme.**

**Ce n'est pas un choix, c'est une impossibilité technique.** Les mots de passe
sont stockés en **BCrypt** — un hachage à sens unique (`SecurityConfig.java:61`,
`AppUserServiceImpl.java:104` et `121`). Il n'existe aucun moyen de retrouver le
mot de passe d'origine à partir de ce qui est en base.

Y répondre supposerait de **stocker les mots de passe en clair**. Ce serait :

- une régression de sécurité critique sur une application de santé ;
- en contradiction directe avec le chapitre Sécurité du cahier des charges ;
- contraire aux obligations de protection des données à caractère personnel
  (loi 2013-450) ;
- une exposition majeure en cas de fuite de base, les utilisateurs réemployant
  fréquemment leurs mots de passe ailleurs.

**Le besoin réel derrière la demande** est vraisemblablement : *« je dois
pouvoir dépanner un utilisateur qui a perdu son mot de passe. »*

**Alternative à proposer, qui y répond entièrement** : réinitialisation par
l'administrateur avec génération d'un **mot de passe temporaire à usage
unique**, communiqué à l'utilisateur, avec changement obligatoire à la première
connexion.

Bonne nouvelle : **la brique existe déjà**. L'entité `AppUser` porte les champs
`password_reset` et `password_expire_at`. Il reste à câbler l'action côté
administration.

**Charge : 1,5 j** — et l'option `update` demandée est conservée.

---

## 4. LSTracker mobile

### 4.1 🔵 Convoyeur — détail des échantillons acceptés par les laboratoires

Légitime, mais **nettement plus léger qu'estimé**.

Précision de vocabulaire : il n'existe aucun mécanisme « dépliable » dans les
dashboards mobiles. Ce qui existe est une **navigation par push** vers
`SampleTypesScreen`. Étendre ce mécanisme ne veut donc pas dire brancher un
composant d'expansion, mais ajouter des actions au tap.

Or **5 des 7 cartes du convoyeur sont déjà navigables**. Il manque deux cartes
de la section « Suivi » et le helper `_openStatusesList` (variante
multi-statuts), présent chez l'admin et le labo.

Les trois dashboards partagent le même `SampleDao` et le même
`dashboardCounters()`, sans paramètre de rôle : la transposition est directe.

**Charge : 0,5 j** *(était 2 j)*

### 4.2 ⚪ Enregistrement — séparation « axe » et « laboratoire de destination » — **hypothèse infirmée**

Le document alertait : *« si le convoyeur peut choisir n'importe quel
laboratoire indépendamment de son circuit, la cohérence n'est plus garantie…
à trancher avec le métier. »*

**Cet arbitrage n'a pas lieu d'être : les deux champs sont déjà
indépendants.**

- Les deux listes sont chargées ligne à ligne, **sans aucun filtre**
  (`sample_edit_screen.dart:106-107`).
- `_onCircuitChanged` ne réinitialise que le **site**, jamais le laboratoire.
- Il n'existe **aucune table `circuit_lab`** au schéma SQLite — seulement
  `circuit_site`.
- Le même chargement non filtré se retrouve dans tous les autres écrans de
  saisie.

Impact métier de la séparation demandée : **nul**. Il ne reste qu'un travail de
libellés (convention « (collecte) »).

**Charge : 0,5 j** *(était 2-3 j)*

### 4.3 ✅ Laboratoire — détail des résultats prêts, récupérés, déposés

**Fondé, et c'est le besoin mobile le plus réel des trois.**

Les quatre cartes de la section « Suivi » du dashboard laboratoire — dont les
trois citées — n'ont **aucun accès au détail** : ni dépliement, ni navigation.
Le biologiste voit un chiffre mort. C'est le dashboard le plus fermé des trois.

**Charge : 1,5 j**

---

## 5. Ce que les observations ne couvrent pas

Ces points ne figurent pas au document mais conditionnent des engagements du
cahier des charges.

| Sujet | Enjeu | Charge |
|---|---|---|
| **Verrou distribué et sessions externalisées** | La synchronisation OpenELIS n'a qu'une garde `AtomicBoolean` en mémoire (`OeAnalysisBatchService.java:41`) ; aucun ShedLock ni verrou en base. En multi-instance elle s'exécutera en double. Les sessions HTTP sont également en mémoire (aucune dépendance Spring Session). **Bloquant pour la montée en charge horizontale annoncée.** | 2 j |
| **Couverture de tests** | 5 fichiers de test, 13 méthodes au total. Les **11 vrais tests sont tous côté mobile** ; les deux backends Java n'ont que le `contextLoads()` généré par Spring Initializr, soit **zéro test métier sur les deux applications qui calculent les indicateurs**. Le chapitre IX du CDC engage des tests unitaires sur cette logique. | 8-10 j |
| **Supervision** | Actuator n'expose que `health` et `info`, avec `show-details=never` (`application.properties:103`). Ni métriques, ni Prometheus. Le CDC promet supervision et alertes. | 3 j |
| **Interopérabilité FHIR R4** | Le lot principal du cahier des charges. Prérequis du point 1.13. | 25-30 j |

Deux points de cette section ont été **traités pendant la revue** — voir §7.

---

## 6. Lots de travail

| Lot | Contenu | Charge | État |
|---|---|---|---|
| **Lot 0 — Bloquants** | 2.1 TAT · 3.1 écrans d'administration | — | ✔︎ **Livré** |
| **Lot 1 — Libellés et lisibilité** | 1.2 · 1.3 · 1.6 · 1.8 · 1.10 · 1.11 | — | ✔︎ **Livré** |
| **Lot 2 — Sécurité** | 3.4 réinitialisation · method-security | **~2 j** | TLS et API livrés (§7) |
| **Lot 3 — Évolutions tableau de bord** | 1.1 cohorte · 1.4 carte ajoutée · 1.5 comparatif · 1.12 districts · 1.14 classements | **~11 j** | 2 clarifications à lever |
| **Lot 4 — Mobile** | 4.1 · 4.2 · 4.3 | **~2,5 j** | |
| **Lot 5 — Administration et contenu** | 3.2 manuels | **~2 j** | 1.7 sans objet |
| **Lot 6 — Interopérabilité** | FHIR R4 + 1.13 réconciliation | **~30 j** | |
| **Lot 7 — Dette technique** | tests · supervision · unification TAT | **~13 j** | verrou distribué à remonter |

**Total restant hors interopérabilité : ~31 j-h. Total général : ~61 j-h.**

### Ce que ça dit du calendrier

Les lots 0 et 1 représentaient environ 10 j-h, désormais livrés. Il reste
**~61 jours-homme**, soit environ **12 semaines de développement à temps
plein** — près de **3 mois pour un seul développeur**, hors recette, hors
documentation, hors formation, hors déploiement et hors reprise des anomalies
détectées en recette.

Le cahier des charges prévoit **6 mois calendaires**. La conclusion reste la
même, même si elle est moins tendue qu'à la première estimation : **le planning
suppose au minimum 1,2 à 1,5 équivalent temps plein en développement**, ou un
arbitrage explicite sur les lots 3 et 4, seuls lots entièrement composés
d'évolutions de confort.

C'est un élément à porter en comité de pilotage **avant** la suite des travaux.

### Deux points à remonter en priorité

1. **Le verrou distribué (2 j, lot 7)** est bloquant pour la montée en charge
   horizontale annoncée au CDC. Or le lot 7 est celui qui sautera en premier
   dans un arbitrage. Ce point mériterait d'être traité hors lot.
2. **La method security** (lot 2) reste à activer, avec la précaution décrite
   en §7.

---

## 7. Défauts trouvés en testant, absents des observations

Quatre défauts réels ont été découverts au cours de la vérification. Trois sont
corrigés.

### 7.1 ✔︎ Suppression sans confirmation — **corrigé**

Sur les écrans d'administration, le bouton « Supprimer » ne demandait **aucune
confirmation** : l'élément était supprimé au clic.

L'attribut était construit par concaténation avec `JSON.stringify`, ce qui
produisait :

```html
onclick="return confirm("Supprimer ce type d'échantillon ?");"
```

Le navigateur fermait l'attribut au deuxième guillemet ; le handler devenait
invalide, échouait silencieusement, et le lien naviguait directement.

Le défaut venait du gabarit `region`, où il était **latent** — « Supprimer cette
région ? » ne contient pas d'apostrophe. Corrigé sur les cinq écrans concernés
par délégation d'événement.

> Ce défaut a fait perdre des données pendant la revue elle-même : un type
> d'échantillon a été supprimé par inadvertance des deux côtés, testeur et
> développeur.

### 7.2 ✔︎ Validation TLS désactivée en production (oedatarepo) — **corrigé**

La validation du certificat du hub FHIR était désactivée (`TrustAllStrategy` +
`NoopHostnameVerifier`) dès que l'URL commençait par `https` :

```java
if (sslTrustAll || fhirBaseUrl.startsWith("https")) { ... }
```

Le second terme court-circuitait le flag d'opt-in `interop.fhir.ssl.trustAll`
(défaut `false`, défini nulle part). L'URL de production étant en `https`, **le
trust-all était actif en production** sans que personne ne l'ait demandé — sur
le canal qui transporte les identifiants Basic vers le hub.

La désactivation est devenue un opt-in explicite, et l'URL du hub est désormais
paramétrable par variable d'environnement.

### 7.3 ✔︎ Endpoints interop accessibles sans contrôle de rôle — **corrigé**

La method security n'est pas activée : les **24 `@PreAuthorize` du projet
oedatarepo sont décoratifs**, ce que `SecurityConfig` documente déjà pour
`/site/delete/**`.

Sur la chaîne `/api/**`, la seule règle appliquée était
`.anyRequest().authenticated()`. **Tout porteur d'un JWT valide accédait à tous
les endpoints interop**, quel que soit son rôle.

Des règles au niveau URL ont été posées, avec les mêmes rôles que les
`@PreAuthorize` correspondants.

⚠️ **L'activation globale de `prePostEnabled` a été volontairement reportée** :
elle rendrait 24 annotations effectives d'un coup, sur des contrôleurs web
autant que sur les API. L'enum `UserRole` ne contient que `USER/ADMIN/PUSHER`,
alors que `BaseController` et les contrôleurs REST testent aussi `ROLE_MANAGER`
et `ROLE_SUPPORT`, **absents de l'enum**. À traiter après vérification des rôles
réellement portés par les comptes en base. **Charge : 1 j + recette.**

### 7.4 ✔︎ Séquences d'identité non alignées — **corrigé**

Les séquences de `sample_type` et `sample_rejection_type` n'avançaient pas lors
des chargements initiaux (ids fournis via l'ancienne `sample_type_id_seq`,
vestige d'avant le passage en `GENERATED BY DEFAULT AS IDENTITY`). La première
création depuis l'interface échouait donc sur une violation de clé primaire.

Défaut **invisible jusqu'ici** : sans écran d'administration, aucune création
n'était possible. Corrigé par changeset Liquibase, sur le modèle de
`fix_sequences_after_loaddata.xml` qui traite le même problème pour `region` et
`district`.

### 7.5 ⚠️ Données aberrantes — **non corrigé, à arbitrer**

La vérification du TAT a mis en évidence des anomalies de saisie :

- **13 `collection_date` antérieures à l'an 2000**, dont une en `0024-05-08` —
  saisie d'année à deux chiffres. Produit un TAT de 731 351 jours (~2 000 ans).
- **2 dossiers** dont la date de livraison précède la date de collecte
  (TAT négatif).
- **39 `RESULT_ON_SITE` (4 %) et 3 `ANALYSIS_FAILED` sur 4** sans aucune date
  d'étape — reprise de données du 29/07/2024.

Ces anomalies plaident pour une **contrainte de validation à la saisie**
(date de collecte non future, non antérieure à une borne raisonnable, date de
fin postérieure à la collecte). **Charge : 1 j.**

Pour les dossiers sans date de fin, le choix retenu est de **laisser le TAT
courir** : un TAT anormal signale la donnée manquante, là où un repli sur une
date technique la masquerait.

---

## 8. Corrections apportées à la première version de ce document

Par souci de traçabilité, voici ce que la vérification a infirmé dans notre
propre analyse.

| Élément | Première version | Après vérification |
|---|---|---|
| **1.7 — PrEP** | « N'existe pas dans le référentiel. Ajout légitime. » | PrEP, IVSA et Autre **existent et sont migrés**. Aucun développement. |
| **1.9 — dépliement** | « ✅ Défaut à confirmer. Charge 1 j. » | **Non fondé.** Le mécanisme fonctionne ; artefact du jeu de données. |
| **4.2 — circuit/labo** | « Impact métier à valider avant codage. » | **Déjà indépendants.** L'arbitrage annoncé n'a pas lieu d'être. |
| **4.1 / 4.3 — « dépliables »** | « Le mécanisme existe déjà. » | Aucun dépliable n'existe ; c'est une navigation par push. 4.1 bien plus léger, 4.3 plus fermé qu'annoncé. |
| **2.1 — TAT** | « 4 occurrences, charge 2-3 j. » | **Une douzaine** d'occurrences + TAT1/2/3. Correctif livré ; unification restante 2 j. |
| **« 4 tests au total »** | Chiffre avancé | **5 fichiers, 13 méthodes** — mais les 11 vrais tests sont tous sur le mobile, et les deux backends n'ont que `contextLoads()`. |
| **TLS oedatarepo** | « Validation désactivée sur toute connexion HTTPS. » | Portée **plus étroite** (client FHIR seul), criticité **plus haute** (actif en production sans opt-in). |
| **Authentification API** | « HTTP Basic. » | `/api/**` est en **JWT, Basic explicitement désactivé**. Le vrai défaut était l'absence de contrôle de rôle. |
| **1.2 — info-bulles** | « 13 occurrences, l.613-670. » | 11 occurrences, l.613-676. |

**Ce que cela enseigne pour la suite** : sur neuf points, la relecture de code
seule avait produit un verdict inexact ou incomplet. La vérification sur données
réelles et l'exécution des correctifs ont été déterminantes. Les points restants
qui n'ont pas encore été confrontés à une base réelle — notamment le lot 3 —
doivent être considérés avec la même prudence.

---

## 9. Les trois points à arbitrer avec le demandeur

| Point | Position |
|---|---|
| **3.4 — visualisation des mots de passe** | **Refus argumenté.** Techniquement impossible (hachage BCrypt) et contraire aux engagements de sécurité. Contre-proposition : réinitialisation avec mot de passe temporaire. |
| **1.4 — renommage de la carte « Analysés »** | **Refus du renommage tel quel** : il rendrait le chiffre faux au regard de son libellé. Contre-proposition : ajouter une carte « Total déposés ». |
| **1.13 — tableau comparatif OpenELIS / LSTracker** | **Ni refus ni report sec** : à rattacher au lot interopérabilité. Le produire avant l'alignement des référentiels géographiques donnerait un écart qui mesure du bruit de référentiel, pas de la qualité de donnée. |

Deux points appellent une **clarification** et non un arbitrage :

- **1.14** — la contradiction « sur une période » / « en temps réel ».
- **7.5** — la règle de validation à retenir pour les dates de collecte
  aberrantes.

---

*Document maintenu par l'équipe technique LSTracker / I-TECH Côte d'Ivoire.*
