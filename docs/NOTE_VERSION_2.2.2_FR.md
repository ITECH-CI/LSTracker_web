# Note de version — LSTracker (mai 2026)

*Synthèse des travaux : versions 2.2.0 → 2.2.2 (web, mobile) et interconnexion OpenELIS*

> Document destiné aux utilisateurs, responsables de laboratoire, partenaires et
> au pilotage projet. Il décrit, en langage simple, les améliorations apportées
> et leur intérêt concret sur le terrain.

---

## Vue d'ensemble

Cette période de travail a porté sur **quatre axes** :

1. **Fiabilisation des chiffres** — garantir que les tableaux de bord et rapports affichent des données justes et cohérentes.
2. **Améliorations de l'expérience utilisateur** — interfaces plus claires, web et mobile.
3. **Renforcement de la sécurité et de la confidentialité**.
4. **Interconnexion avec OpenELIS** — récupération automatique du statut des analyses.

Le tout accompagné d'une **modernisation de l'infrastructure de déploiement** pour des mises à jour plus simples et plus sûres.

---

## 1. Des chiffres fiables et cohérents

Un audit approfondi des indicateurs a permis de corriger plusieurs écarts qui pouvaient fausser la lecture des tableaux de bord et des rapports :

- **Définitions harmonisées** : un même indicateur (collectés, au hub, au labo, analysés, résultats livrés, non-conformités, échecs…) est désormais calculé **de façon identique** partout — tableau de bord web, entonnoir, rapports PDF. Fini les écarts entre deux écrans censés montrer la même chose.
- **Filtres géographiques réparés** : les filtres par région / district / site, qui restaient parfois sans effet sur certaines courbes du tableau de bord, fonctionnent désormais correctement.
- **Comptages corrigés** : suppression de doublons de comptage (échantillons passés par un hub comptés deux fois, échecs d'analyse comptés en double, etc.).
- **Délai de traitement (TAT)** : calcul unifié et plus représentatif (médiane du délai entre la collecte et la livraison du résultat).

**Bénéfice** : les responsables peuvent s'appuyer sur les chiffres en confiance, pour le suivi comme pour le reporting aux partenaires.

## 2. Sécurité et confidentialité renforcées

- **Rapports respectant le périmètre de chaque utilisateur (correctif important)** : un utilisateur limité à une région/district ne peut plus, par inadvertance, générer un rapport contenant des données nationales. Le périmètre est désormais appliqué à tous les rapports.
- **Application mobile — protection des données sur l'appareil** :
  - les données locales sont **effacées à la déconnexion** (un autre utilisateur du même téléphone ne voit plus les données du précédent) ;
  - si des saisies ne sont pas encore synchronisées au moment de se déconnecter, l'agent est **prévenu et a le choix** (synchroniser d'abord, forcer, ou annuler) ;
  - au changement d'utilisateur, les données du précédent sont purgées avant la nouvelle session.

## 3. Expérience utilisateur (UI/UX)

**Tableau de bord web**
- **Cartes d'indicateurs repensées** (icônes en pastille, valeurs mises en avant, couleurs accessibles), organisées clairement (parcours de l'échantillon en haut, incidents et performance en bas).
- **Aides à la lecture intégrées** : bandeaux explicatifs et info-bulles indiquant précisément ce que mesure chaque indicateur.
- **Rejets séparés** en deux notions distinctes : « Non-conformités » et « Échecs d'analyse ».

**Listes et écrans**
- Écran des échantillons modernisé : filtres avancés repliables, tri, recherche.
- Rapports PDF : mise en page améliorée, libellés plus clairs (ex. « Laboratoire de référence (Biologie Moléculaire) »).

**Application mobile**
- Vocabulaire des tableaux de bord harmonisé avec le web (« Déposés (à recevoir) », « Reçus (à finaliser) »).
- **Bandeau « Vue temps réel »** précisant que les compteurs mobiles reflètent l'état actuel des échantillons (et peuvent différer des chiffres web qui couvrent une période).
- **Garde-fous de saisie des dates** : impossible de saisir une date aberrante (trop ancienne ou dans le futur), source d'erreurs auparavant.

## 4. Interconnexion avec OpenELIS (nouveauté majeure)

LSTracker récupère désormais **automatiquement** le statut et les dates d'analyse des échantillons depuis **OpenELIS** (le système du laboratoire), à partir du numéro de laboratoire — **sans ressaisie manuelle**.

- **Mise à jour automatique** du statut (« Analyse terminée », « Échec d'analyse », « Non conforme ») et des dates, à intervalle régulier.
- **Page de suivi pour les administrateurs** (« Suivi Synchro. OpenELIS ») : état de la connexion, prévisualisation sans appliquer, lancement immédiat, historique des synchronisations.
- **Gestion intelligente** des numéros introuvables (réessais limités, puis mise en pause réactivable d'un clic ; reprise automatique si le numéro apparaît plus tard).
- **Côté mobile** : la saisie manuelle de la « date de début d'analyse » a été retirée — l'information vient désormais d'OpenELIS, allégeant le travail des agents.
- **Sécurité** : page réservée aux administrateurs ; LSTracker **lit** seulement OpenELIS (sans le modifier) via une connexion sécurisée ; l'intégration peut être activée/désactivée sans impact sur le reste.

## 5. Infrastructure et déploiement

- **Mises à jour automatisées** : la construction et la publication des nouvelles versions (web et OpenELIS) sont désormais automatisées, avec des environnements **démo** et **production** séparés pour valider avant diffusion.
- **Déploiements plus simples et plus sûrs** : un déploiement se résume à indiquer la version souhaitée et à la récupérer — moins de manipulations manuelles, moins de risque d'erreur.
- **Documentation technique** complète produite (intégration, déploiement, configuration serveur) pour faciliter la maintenance et d'éventuelles nouvelles installations.

---

## Bénéfices par profil

| Pour… | Bénéfice |
|---|---|
| **Agents de terrain** | Moins de saisie, garde-fous anti-erreurs, app mobile plus claire |
| **Laboratoires** | Cohérence garantie avec OpenELIS, statuts d'analyse à jour automatiquement |
| **Responsables / superviseurs** | Tableaux de bord et rapports fiables, respect du périmètre d'accès |
| **Administrateurs** | Visibilité et contrôle de l'interconnexion ; déploiements simplifiés |
| **Pilotage / partenaires** | Données de suivi et de délais fiabilisées et traçables |

---

## Repères de versions

- **LSTracker web / back-end** : **2.2.2**
- **Application mobile** : **2.2.2 (build 8)**
- **OpenELIS consolidé (oedatarepo)** : **2.3.7** (expose le service d'interopérabilité)

---

## Prochaines étapes

- Déploiement en **production** après validation en **démo**.
- Ajustement des fréquences de synchronisation selon le volume réel.
- Renforcement de la sécurité du serveur (pare-feu) — planifié.

*Pour toute question : équipe LSTracker / I-TECH Côte d'Ivoire.*
