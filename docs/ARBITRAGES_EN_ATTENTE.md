# LSTracker — Points en attente d'arbitrage

*Note du 23/09/2026 · version 2.2.3 (web et mobile), déployée en démonstration.*

Les points ci-dessous ne peuvent pas être traités sans une décision de la DAP,
de la DISD ou du demandeur. Pour chacun : ce qu'il faut décider, les options,
notre recommandation et ce que la décision déclenche. Tout le reste du plan de
charge est engagé sans attendre.

---

## Décisions de fond

### 1. Profils d'habilitation et reprise des comptes — cahier V.2 et V.4

**Constat.** Le cahier définit sept profils (administrateur national,
administrateur régional, superviseur de district, superviseur de laboratoire,
technicien de laboratoire, convoyeur, administrateur système). L'application
en compte cinq, dont un profil `USER` générique : **17 convoyeurs sur 19** y
sont rattachés. Manquent notamment l'administrateur régional et le superviseur
de district, relais indispensables de l'extension nationale.

**À décider.**
- la validation de la matrice des habilitations du cahier telle quelle, ou ses ajustements ;
- les règles de reprise : à partir de quel usage actuel rattacher chaque compte à un profil et à un périmètre ;
- le circuit de validation de l'**état de reprise** par la DISD et la DAP, et la date de bascule.

**Recommandation.** Valider la matrice du cahier sans modification ; nous
produisons un projet d'état de reprise (proposition de profil et de périmètre
par compte, comptes non rattachables à suspendre) soumis à validation avant
toute bascule.

**Charge.** 4 à 6 jours de développement, plus la recette du cloisonnement des accès.

### 2. Dénominateur du kilométrage moyen — cahier VI.2

**Constat.** Le cahier prévoit que ce dénominateur « sera arrêté avec la DAP et
la DISD ». Il est aujourd'hui fixé à **« distance totale ÷ total des
échantillons collectés »**, règle affichée à l'écran. La distance a été
fiabilisée (trajets comptés une seule fois, relevés aberrants écartés) : la
démonstration affiche environ 6,4 km par échantillon, contre 11 357 km avant
correction.

**Options.**
- *÷ échantillons collectés* (actuel) : lecture « coût de transport par échantillon », mais dilué par les échantillons sans kilométrage exploitable ;
- *÷ échantillons disposant d'un kilométrage exploitable* : non dilué, mais sur une base plus étroite ;
- *÷ trajets* : distance moyenne d'une tournée, indépendante du nombre d'échantillons transportés.

**Recommandation.** Conserver le dénominateur actuel et afficher à côté la
distance moyenne par trajet.

### 3. Mode « cohorte » de l'entonnoir — observation 1.1

**Constat.** Chaque étape compte les échantillons dont *cette* étape a eu lieu
dans la période : un échantillon collecté en août et déposé en septembre compte
dans les dépôts de septembre, pas dans ses collectes. D'où « plus de dépôts que
de collectes » sur une période courte. C'est un comportement documenté et
affiché à l'écran, mais contre-intuitif.

**Option.** Ajouter un mode « cohorte » : suivre les seuls échantillons
collectés dans la période à travers les étapes (un vrai entonnoir, toujours
décroissant).

**Recommandation.** À retenir si les utilisateurs lisent l'entonnoir comme un
taux de passage. **Charge** : 3 à 4 jours.

---

## Réponses attendues sur les observations du 31/05

| Observation | Position proposée | À confirmer par le demandeur |
|---|---|---|
| **1.4** Renommer « Analysés » en « Échantillons déposés au labo (relais et référence) » | Refus du renommage tel quel : le chiffre deviendrait faux au regard de son libellé. Contre-proposition : une carte « Total déposés » en plus. | Accord sur la contre-proposition (1 j) |
| **1.14** Classements régions / districts / laboratoires / convoyeurs | La demande dit à la fois « sur une période » et « en temps réel ». Proposé : sur la période sélectionnée, comme le reste du tableau de bord. Le classement des régions et districts avec rang et écart à la moyenne (cahier VI.2) est engagé. | Période ou temps réel |
| **3-3** Visualiser les mots de passe des utilisateurs | Refus : techniquement impossible (hachage BCrypt) et contraire aux engagements de sécurité. Contre-proposition : réinitialisation par l'administrateur (point 5 ci-dessous). | Accord sur le refus |
| **1.13** Tableau comparatif OpenELIS / LSTracker avec écart | À rattacher au lot interopérabilité : sans alignement préalable des référentiels géographiques, l'écart mesurerait des différences de référentiel, pas de données. | Rattachement au lot FHIR (8 à 12 j) |

---

## Décisions opérationnelles

### 4. Nettoyage des axes — observation mobile 1-2

Des laboratoires figurent comme **sites de collecte** dans les axes. Un script
(`scripts/sql/labos_dans_les_axes.sql`) a été préparé et seulement
prévisualisé en démonstration :

- **12 sites à retirer des axes** (aucune collecte, ce sont des laboratoires) : CAT Bouaké, EPHR et EPHU Bouaké, EPHD Béoumi, CHR et CSUS-CAT Man, CHR et HG San Pedro, CHR et CAT Yamoussoukro, CDV AIBEF Daloa, EPHD Botro ;
- **11 sites à renommer « (collecte) »** (hôpitaux qui prélèvent réellement, jusqu'à 1 336 collectes pour HG Zoukougbeu) ;
- **3 cas à trancher** : CHR Daloa (7 collectes, un site de collecte dédié existe déjà), HG Tabou (7), HG Vavoua (1).

**À décider** : validation des listes, puis date d'application (démo, puis production).

### 5. Réinitialisation des mots de passe

La consigne du bailleur exclut la collecte d'adresses électroniques. La
fonction existante n'en a pas besoin : l'administrateur génère un mot de passe
temporaire, affiché à l'écran, qu'il transmet à l'utilisateur. Elle souffre
d'un défaut connu : le mot de passe temporaire bloque la connexion au lieu
d'imposer un changement.

**À décider** : retenir ce mode de dépannage (correction : ~1 j).

### 6. Migration de la production

La production tourne sur l'ancienne installation. La passer en 2.2.3 est une
migration (base, configuration, bascule du proxy), pas une simple mise à jour.

**À décider** : la fenêtre de bascule, la période d'observation en
démonstration préalable, et le plan de retour arrière.

### 7. Qualité de saisie — action terrain

Les corrections ont mis en évidence des défauts de saisie qu'aucun
développement ne corrige a posteriori :

- **24 732 relevés kilométriques inexploitables** en démonstration (départ à 0, arrivée avant le départ, plus de 1 000 km) ;
- **77 dossiers** aux dates incohérentes sur la base de test (étape antérieure à la collecte, date future) — la saisie est désormais contrôlée, mais l'existant reste à corriger.

**À décider** : une campagne de rappel auprès des convoyeurs, et la
correction des dossiers existants (liste fournie sur demande).

---

## Engagé sans attendre d'arbitrage

Pour mémoire, les travaux suivants sont lancés :

- conformité au cahier : filtres homogènes sur toutes les visualisations,
  taux de non-conformité distinct des échecs d'analyse, classement des régions et
  districts (rang, écart à la moyenne), classement des convoyeurs (délai médian
  d'acheminement) ;
- fiabilité : unification du calcul du TAT ;
- performance : allègement du tableau de bord, mesure des requêtes sur un
  volume national ;
- exploitation : verrou distribué de la synchronisation OpenELIS, supervision ;
- observation 3.2 : manuels téléversables depuis l'administration.
