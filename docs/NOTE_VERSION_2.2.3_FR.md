# Note de version : LSTracker 2.2.3

*Version livrée au titre du contrat UCP-FM/2026/150 (novembre 2026)*

> Document destiné aux utilisateurs, aux responsables de laboratoire, à la DISD, à la DAP et aux partenaires. Il présente, en langage simple, ce que la version 2.2.3 apporte et ce qui change dans l'usage quotidien.

---

## Vue d'ensemble

La version 2.2.3 répond aux trois axes de développement du contrat :

1. **Habilitations et accès** : des profils clairs, chacun limité à son périmètre géographique, et une administration centralisée.
2. **Centralisation des données pour le pilotage national** : une vue nationale consolidée, des répartitions par zone dont les totaux concordent avec les indicateurs, des exports datés.
3. **Indicateurs et tableau de bord de suivi de la performance** : de nouveaux filtres, des indicateurs par circuit, des libellés explicites et des chiffres contrôlés.

Elle renforce aussi la sécurité, la traçabilité et l'exploitation de la plateforme, dans le cadre de la maintenance des serveurs.

---

## 1. Habilitations et accès

### Des profils nommés selon les fonctions

| Profil | Ce qu'il permet |
|---|---|
| Administrateur national | Administration des comptes, du maillage et des référentiels ; consultation et export sur tout le territoire |
| Administrateur système | Comme l'administrateur national, plus l'attribution du profil Administrateur système |
| Pilotage national (consultation) | Consultation et export de tout le pays ; écrans d'administration en lecture seule |
| Superviseur régional | Consultation et export des données de sa ou ses régions |
| Superviseur de district | Consultation et export des données de son ou ses districts |
| Technicien ou superviseur de laboratoire | Opérations et suivi des laboratoires affectés |
| Convoyeur | Collecte et acheminement sur ses circuits ; accès aux laboratoires des districts où il intervient |
| Utilisateur (consultation) | Consultation dans la limite de son affectation |

### Une administration centralisée

Seuls les administrateurs nationaux créent les comptes et administrent le maillage et les référentiels. Les régions et les districts adressent leurs demandes à l'administration centrale. Le superviseur régional et le superviseur de district consultent et exportent les données de leur zone ; ils n'ont accès à aucun écran d'administration.

### Chacun ne voit que son périmètre

- Le périmètre s'applique partout : tableau de bord, liste et fiche des échantillons, exports, rapports, application mobile. Les listes de filtre ne proposent que les zones et les laboratoires du périmètre.
- Un compte dont l'affectation est incomplète ne voit aucune donnée, au lieu de voir le pays entier.
- Un échantillon hors périmètre est traité comme inexistant, y compris par son adresse directe.
- Un compte qui doit être affecté à une zone (superviseur) ne peut pas être enregistré sans elle.

### Connexion plus sûre

- À partir du troisième échec, l'écran de connexion indique le nombre de tentatives restantes ; au cinquième, le compte est bloqué et l'utilisateur est invité à contacter l'administration centrale. Les messages sont les mêmes pour un identifiant inexistant, pour ne pas révéler quels comptes existent.
- Après une réinitialisation par l'administrateur, le mot de passe temporaire permet de se connecter, puis l'application impose d'en choisir un nouveau avant toute autre page.
- Les actions d'administration des comptes (désactiver, verrouiller, réinitialiser un mot de passe) sont protégées contre les requêtes falsifiées.

### Traçabilité

- **Journal d'activité** : chaque création, modification ou suppression est enregistrée avec son auteur, sa date, son canal (web, mobile, système) et la valeur antérieure de chaque champ modifié. Les exports et les rapports sont eux aussi journalisés.
- **Journal des connexions** : chaque tentative de connexion, réussie ou non, est enregistrée. Une page d'administration présente la fréquentation, les comptes inactifs et les échecs répétés.
- Conservation de 12 mois ; consultation réservée aux administrateurs.

---

## 2. Centralisation des données pour le pilotage national

- **Vue nationale consolidée** : les profils nationaux voient l'ensemble du pays sur le même tableau de bord que les zones, avec les mêmes définitions de calcul.
- **Répartition par zone géographique** : un seul tableau, du niveau région au niveau site, avec le niveau courant indiqué dans le titre et le fil d'Ariane. Chaque ligne se déplie pour montrer la répartition par type d'échantillon, les durées médianes par étape et les non-conformités par motif, et ouvre la liste des échantillons correspondante, qui donne le même nombre.
- **Totaux concordants** : un échantillon est rattaché à la zone de son site de collecte, à défaut à celle de son site demandeur. Les totaux de la répartition égalent ceux des indicateurs.
- **Exports datés et situés** : l'export des échantillons et celui de la répartition indiquent la date, l'auteur, la période, le périmètre et les filtres appliqués.
- **Maillage préservé** : régions, districts et sites se désactivent au lieu d'être supprimés ; ils disparaissent des listes de saisie et gardent leur historique. Les taux de couverture ne comptent que les éléments actifs.
- **Dates fiabilisées** : les dates antérieures à la mise en service ou futures sont refusées à la saisie ; un script d'audit et de correction traite les années mal saisies dans les données existantes.

---

## 3. Indicateurs et tableau de bord de suivi de la performance

### Filtres

- Chaque liste de filtre commence par « Tout », sélectionné par défaut.
- Nouveau filtre **« Type de labo »** (laboratoire de district, laboratoire régional, CAT, plateforme de biologie moléculaire, hub), qui restreint aussi la liste des laboratoires.
- Nouveau filtre **« Type d'échantillon »** (CV, EID, TB, HPV…).
- Les filtres s'appliquent à tous les blocs du tableau de bord, aux exports et aux rapports, toujours en plus du périmètre de l'utilisateur.

### Indicateurs par circuit

La carte **« Circuits »** présente, pour chaque tournée de convoyeur : échantillons collectés, échantillons déposés, délai médian d'acheminement (de la collecte au premier dépôt) et nombre de sites desservis. Chaque échantillon y est compté une seule fois : les échantillons d'un site desservi par plusieurs circuits sont regroupés sur une ligne « Sites partagés entre plusieurs circuits », ceux d'un site sans circuit sur une ligne « Non rattaché ». Le décompte des convoyeurs actifs reste affiché.

### Autres indicateurs de performance

- Durée médiane par étape du parcours de l'échantillon, pour localiser les retards.
- Laboratoires les plus lents (délai au laboratoire) et taux de non-conformité par site, détaillé par type d'échantillon.
- Taux de couverture du déploiement et kilométrage, calculé sur les seuls trajets exploitables.

### Lecture simplifiée

- Libellés qui nomment ce qui est compté : « Étapes de l'échantillon », « Échantillons collectés par type », « Résultats récupérés au laboratoire », « Résultats rendus aux sites », « Échantillons non conformes par site (taux) »…
- « En transit » devient **« En cours d'acheminement »** partout : écrans, exports, rapports et application mobile.
- À la demande des utilisateurs, les comparaisons avec la période précédente et les rangs du tableau de répartition sont retirés.
- Le délai de traitement (TAT) s'affiche sans coloration : les seuils de couleur précédents ne correspondaient pas aux délais cibles propres à chaque type d'examen.

### Chiffres contrôlés

Une page de contrôle de cohérence, réservée aux administrateurs, recalcule chaque indicateur par le code de chaque écran (répartitions, graphiques, rapports, carte Circuits) et signale tout écart, pour les filtres choisis.

---

## 4. Exploitation et maintenance des serveurs

- Réglages de la base de données et des conteneurs documentés, avec leur règle de calcul.
- Tests de montée en charge représentatifs : deux défauts corrigés, qui faisaient chuter le service à 50 utilisateurs simultanés.
- Supervision de la disponibilité et des ressources, avec alertes.

---

## Bénéfices par profil

| Pour… | Bénéfice |
|---|---|
| **DISD (administration centrale)** | Comptes et maillage administrés en un seul point ; profils explicites ; traçabilité complète |
| **DAP et pilotage national** | Vue nationale consolidée, filtres par type de laboratoire et d'échantillon, indicateurs par circuit |
| **Superviseurs régionaux et de district** | Accès direct aux données et exports de leur zone, sans risque de modification |
| **Laboratoires** | Délai au laboratoire et non-conformités suivis par site et par type |
| **Convoyeurs** | Vocabulaire aligné entre le web et le mobile |

---

## Repères de versions

- **LSTracker web et serveur** : 2.2.3
- **Application mobile** : 2.2.3 (build 10)

## Prochaines étapes

- Recette par la DISD et la DAP sur l'environnement de démonstration, puis signature du procès-verbal de réception.
- Décisions de la DAP sur les données à reprendre avant la migration de la production (sites sans circuit ou partagés, échantillons sans collecte, années de collecte aberrantes).
- Migration de la production.

*Pour toute question : équipe LSTracker, I-TECH Côte d'Ivoire.*
