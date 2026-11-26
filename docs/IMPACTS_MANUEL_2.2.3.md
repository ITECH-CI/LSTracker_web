# Manuel utilisateur : passages à mettre à jour pour la version 2.2.3

Ce document liste, chapitre par chapitre, les passages du manuel utilisateur destiné aux administrateurs et aux techniciens (livrable n° 5) que la version 2.2.3 rend inexacts, avec la formulation à retenir. La numérotation est celle du manuel livré. Le fichier Word est mis à jour hors de ce dépôt.

Thèmes concernés : profils et administration centralisée, filtres, tableau par zone géographique, circuits, « en cours d'acheminement », « Étapes de l'échantillon », retrait du comparatif de période, des rangs et de la coloration du TAT, connexion et mots de passe.

## Reformulation imposée par l'administration centralisée

Décision du projet : l'administration est centralisée. Seuls l'administrateur national et l'administrateur système créent les comptes et administrent le maillage et les référentiels ; les régions et les districts adressent leurs demandes à l'administration centrale. **Il n'existe pas d'administrateur régional.** Le niveau régional est tenu par un **superviseur régional, en consultation** : il consulte et exporte les données de sa ou ses régions, sans aucun accès à l'administration.

Toute occurrence d'« administrateur régional » dans le manuel est à remplacer :

| Chapitre | Texte actuel | Texte à retenir |
|---|---|---|
| 1.2 Public visé | « Administrateur / superviseur régional : chapitres 2, 3, 4, 5, 7, 8 et 9 (dans la limite de son périmètre) » | « Superviseur régional : chapitres 2, 3, 7, 8 et 9 (dans la limite de sa région) » |
| 2.4 Profils et habilitations | Ligne « Administrateur régional : administration du maillage et des utilisateurs de son périmètre » | Ligne « Superviseur régional : consultation et export des données de sa région ; refusé : toute administration, accès hors région » (voir la matrice ci-dessous) |
| 4. Gestion des utilisateurs | « Profils concernés : administrateur national (tous les comptes) et administrateur régional (comptes de son périmètre, hors profils nationaux) » | « Profils concernés : administrateur national et administrateur système. Les régions et les districts adressent leurs demandes de création ou de modification de compte à l'administration centrale. » |

La même reformulation s'applique aux spécifications d'architecture et au rapport final, qui décrivent un administrateur régional administrant le maillage et les utilisateurs de son périmètre.

## 1. Introduction

- **1.1 Objet du manuel** : « version 2.2 » devient « version 2.2.3 ».
- **1.2 Public visé** : voir la reformulation ci-dessus. Ajouter la ligne « Pilotage national (consultation) : chapitres 2, 3, 7, 8 et 9 ». La ligne « Superviseur de district ou de laboratoire » reste.
- **1.3 Ce qui change** : la puce « Nouveaux indicateurs : comparatif de période, classements, délais par segment, taux de couverture, export horodaté » devient « Nouveaux indicateurs et filtres : filtres par type de laboratoire et par type d'échantillon, indicateurs par circuit, répartition par zone géographique, durée médiane par étape, taux de couverture, export horodaté ». La puce « Habilitations par profil et par périmètre » ajoute « administration centralisée ; superviseurs régionaux et de district en consultation ».

## 2. Présentation générale

- **2.2 Organisation territoriale** : « les circuits sont les itinéraires d'acheminement… » devient « un circuit est la tournée d'un convoyeur : il relie des sites, des hubs et des laboratoires. Un site peut appartenir à plusieurs circuits ; le rattachement d'un site à un circuit est paramétré par l'administrateur. » Le mot « circuit » est réservé à cette tournée.
- **2.3 Cycle de vie de l'échantillon** : « suit un parcours horodaté » devient « franchit des étapes horodatées (« Étapes de l'échantillon ») ». Dans le tableau :
  - ajouter l'état « En cours d'acheminement » : échantillon collecté, pas encore déposé au laboratoire ;
  - « Au hub : transit par un point de regroupement » devient « Au hub : passage par un point de regroupement » ;
  - « Résultat livré » devient deux lignes : « Résultats récupérés au laboratoire » (le convoyeur récupère les résultats) et « Résultats rendus aux sites » (remise au site).
- **2.4 Profils et habilitations** : remplacer la matrice par la suivante.

| Profil | Périmètre | Fonctions autorisées | Fonctions refusées |
|---|---|---|---|
| Administrateur national | National | Administration des comptes, du maillage et des référentiels ; consultation et export de tout le territoire | Attribution du profil Administrateur système |
| Administrateur système | National | Comme l'administrateur national, plus l'attribution du profil Administrateur système ; exploitation technique | Consultation des données de santé au-delà du strict nécessaire |
| Pilotage national (consultation) | National | Consultation et export de tout le territoire ; écrans d'administration en lecture seule | Toute écriture |
| Superviseur régional | Région(s) affectée(s) | Consultation et export des données de sa région | Toute administration ; accès hors région ; saisie d'échantillon |
| Superviseur de district | District(s) affecté(s) | Consultation et export des données de son district | Toute administration ; accès hors district ; saisie d'échantillon |
| Technicien ou superviseur de laboratoire | Laboratoire(s) affecté(s) | Opérations et suivi de ses laboratoires | Administration ; données d'autres laboratoires |
| Convoyeur | Sites de ses circuits, laboratoires des districts où il intervient | Saisie mobile des collectes, dépôts et récupérations de résultats | Administration ; consultation agrégée |
| Utilisateur (consultation) | Son affectation | Consultation | Toute écriture |

  Ajouter sous la matrice : « Un compte dont l'affectation ne donne accès à aucun site ne voit aucune donnée. »

## 3. Prise en main de l'application web

- **3.2 Se connecter** : ajouter « À partir du troisième échec, un message indique le nombre de tentatives restantes. Au cinquième, le compte est bloqué : “Compte bloqué après 5 tentatives. Contactez l'administration centrale.” Seul un administrateur le débloque. » et « Après une réinitialisation, le mot de passe temporaire permet de se connecter ; l'application demande alors de choisir un nouveau mot de passe avant toute autre page. »
- **3.3 Description de l'interface** : « menus Tableau de bord, Échantillons, Rapports, Administration (selon le profil) » ; préciser que le menu Administration n'apparaît pas pour les superviseurs. Le compteur de pagination s'écrit « 1 à 25 sur 33 » (et non « 1–25 sur 33 »).
- **3.4 Le menu Administration** : « Réservé aux administrateurs » devient « Réservé à l'administration centrale (administrateur national, administrateur système). Le profil Pilotage national le consulte en lecture seule. »
- **Figure 1** : capture à refaire si l'en-tête ou la pagination y figurent.

## 4. Gestion des utilisateurs et des habilitations

- **Introduction** : voir la reformulation ci-dessus.
- **4.2 Créer un utilisateur** :
  - étape « Choisir le profil » : lister les libellés affichés (« Administrateur national », « Administrateur système », « Pilotage national (consultation) », « Superviseur régional », « Superviseur de district », « Technicien ou superviseur de laboratoire », « Convoyeur », « Utilisateur (consultation) ») ;
  - étape « Choisir le niveau » : pour un superviseur régional ou de district, le niveau est fixé par le profil et l'affectation d'au moins une région (ou d'un district) est obligatoire ; l'écran s'intitule « Affecter les zones » ;
  - seul l'administrateur système attribue ou retire le profil Administrateur système ;
  - étape « Définir le mot de passe initial » : le changement est imposé à la première connexion.
- **4.3 Modifier, suspendre ou réactiver** : ajouter « réinitialiser le mot de passe : l'utilisateur reçoit un mot de passe temporaire et doit en choisir un nouveau à sa connexion » et « déverrouiller un compte bloqué après cinq échecs ». Préciser qu'un compte ne se supprime pas, il se désactive.
- **4.4 Règles à respecter** : ajouter « Les comptes de consultation régionaux ou de district repris de la version précédente reçoivent le profil Superviseur régional ou Superviseur de district et une affectation de zone ; sans affectation exploitable, ils ne voient aucune donnée. »
- **Figure 2** : capture à refaire (libellés des profils).

## 5. Administration du maillage territorial

- **Introduction** : ajouter « Réservé à l'administration centrale. »
- **5.6 Gérer les circuits et les convoyeurs** : ajouter « Un site peut être rattaché à plusieurs circuits. Le rattachement détermine la carte “Circuits” du tableau de bord (chapitre 7.3) : un site rattaché à un seul circuit actif y est compté dans ce circuit ; un site rattaché à plusieurs circuits est regroupé sur la ligne “Sites partagés entre plusieurs circuits” ; un site sans circuit, sur la ligne “Non rattaché”. Un circuit inactif ne reçoit aucun échantillon. »

## 6. Administration des référentiels

- Tableau des référentiels, ligne Statuts : citer les libellés « En cours d'acheminement » et « RESULTATS RECUPERES », qui remplacent « En transit » et le libellé précédent des résultats récupérés.

## 7. Tableau de bord et indicateurs

- **7.1 Utiliser les filtres** : ajouter
  - « Chaque liste commence par “Tout”, sélectionné par défaut » ;
  - « Type de labo : laboratoire de district, laboratoire régional, CAT, plateforme de biologie moléculaire, hub. Le choix restreint aussi la liste des laboratoires » ;
  - « Type d'échantillon : CV, EID, TB, HPV… » ;
  - « Les listes Région, District, Site et Laboratoire ne proposent que les éléments de votre périmètre ».
- **7.2 Indicateurs du parcours** : titre « Indicateurs des étapes de l'échantillon » ; le bandeau s'intitule « Étapes de l'échantillon ». Dans le tableau : ajouter « En cours d'acheminement : échantillons collectés, pas encore déposés au laboratoire » ; « Résultats livrés » devient « Résultats rendus aux sites » ; ajouter « Résultats récupérés au laboratoire ». Ligne TAT : ajouter « Le TAT s'affiche sans couleur ; les délais cibles par type d'examen viendront dans une version ultérieure. »
- **7.3 Indicateurs de performance** : remplacer le tableau.

| Retirer | Remplacer par |
|---|---|
| Comparatif de période | (retiré, aucune comparaison avec la période précédente n'est affichée) |
| Classement des régions et districts : volume, rang et écart à la moyenne | Répartition par zone géographique : volumes par région, district ou site, sans rang ni écart à la moyenne ; ligne dépliable « Détails » (types d'échantillon, durée médiane par étape, non-conformités par motif, lien vers la liste) |
| Classement des laboratoires | Laboratoires les plus lents (délai au laboratoire) |
| Classement des convoyeurs | Circuits : échantillons collectés, déposés, délai médian d'acheminement (collecte au premier dépôt), sites desservis ; lignes « Sites partagés entre plusieurs circuits » et « Non rattaché ». Le décompte « Convoyeurs actifs » reste affiché |
| Délai par segment | Durée médiane par étape, en jours (échantillons de la période) |
| Taux de non-conformité par site | Échantillons non conformes par site (taux) |
| Volumétrie par type d'échantillon | Échantillons collectés par type |

  Les lignes « Taux de couverture du déploiement » et « Kilométrage moyen » restent. Mentionner aussi « Évolution des échantillons dans le temps » et « Étapes des échantillons par type ».
- **Répartition par zone géographique** (à ajouter en 7.3 ou en section propre) : en-têtes « Échantillons collectés », « Échantillons en cours d'acheminement », « Échantillons déposés au laboratoire », « Échantillons non conformes », « Échecs d'analyse », « Résultats rendus aux sites », « TAT médian », « Détails ». Le niveau courant figure dans le sous-titre et le fil d'Ariane. Pour un compte national, une ligne « Zone non renseignée » peut regrouper les échantillons sans site de collecte ni site demandeur : les totaux égalent alors ceux des cartes.
- **7.4 Entonnoir et export** : « L'entonnoir » devient « Le bandeau “Étapes de l'échantillon” ». L'export mentionne aussi les filtres de type de laboratoire et de type d'échantillon.
- **Captures** : toutes les captures du tableau de bord sont à refaire.

## 8. Suivi des échantillons

- **8.1 Rechercher** : ajouter le critère « Type de labo » (laboratoire de destination). Un échantillon sans collecte est rattaché à la zone de son site demandeur ; à défaut, la liste affiche « Zone non renseignée ».
- **8.2 Consulter la fiche** : les profils de consultation ouvrent le détail par le bouton « Voir détail » ; la modification est réservée aux administrateurs. Pour un échantillon sans collecte, le site affiché est le site demandeur. L'historique nomme l'action « Récupérer les résultats ».
- **8.3 Exporter la liste** : « CSV ou Excel » devient « CSV, lisible directement par Excel ». L'export indique la date, l'auteur, la période, le périmètre et les filtres.

## 9. Rapports

- « Un utilisateur régional » devient « un superviseur régional ». Les rapports affichent « En cours d'acheminement » et une période de la forme « 01/10/2026 au 31/10/2026 ».

## 10. Opérations du technicien de laboratoire (web)

- Aucun changement fonctionnel. Remplacer, le cas échéant, « résultats livrés » par « résultats rendus aux sites ».

## 11. Application mobile

- **11.1 Installer et se connecter** : les profils de consultation (pilotage national, superviseurs) ouvrent le tableau de bord de consultation, sans action de collecte ni de dépôt. Mêmes messages qu'au web avant blocage du compte.
- **11.2 Le tableau de bord mobile** : « Résultats récupérés » devient « Résultats récupérés au laboratoire » ; le compteur « En transit » du tableau de bord laboratoire devient « En cours d'acheminement ». **Figure 4** à refaire.
- **11.4 Déposer, réceptionner, récupérer les résultats** : l'action s'intitule « Récupérer les résultats ». Sur les fiches, une valeur absente s'affiche « Non renseigné » ; le statut s'affiche en français.

## 12. Exploitation technique

- **12.2 Déployer une nouvelle version** : contrôler que l'en-tête affiche « 2.2.3 ». Les changements de schéma (rôles, libellés de statut, contrainte sur les échantillons) s'appliquent automatiquement au démarrage.

## 13. Dépannage et assistance

- **13.1 Problèmes fréquents** :
  - « Connexion refusée » : ajouter la cause « compte bloqué après cinq échecs » et la solution « contacter l'administration centrale pour le déblocage » ;
  - « Menu Administration absent » : la solution devient « normal pour les profils de consultation ; adresser les demandes de création ou de modification à l'administration centrale » ;
  - « Tableau de bord vide » : ajouter la cause « compte sans affectation exploitable » et la solution « faire compléter l'affectation par l'administration centrale » ; « cliquer sur Effacer » devient « remettre les filtres sur Tout » ;
  - ajouter « Page de changement de mot de passe à chaque clic : mot de passe temporaire après réinitialisation ; choisir un nouveau mot de passe ».

## Annexes

- **Annexe A, glossaire** : « Circuit » devient « Tournée d'un convoyeur, reliant sites, hubs et laboratoires ; un site peut appartenir à plusieurs circuits ». « Entonnoir » devient « Étapes de l'échantillon : volumes aux étapes successives ». Ajouter « En cours d'acheminement », « Administration centralisée », « Superviseur régional », « Superviseur de district », « Zone non renseignée », « Sites partagés entre plusieurs circuits », « Non rattaché ». TAT : préciser « affiché sans coloration ».
- **Annexe B, règles de contrôle** : ligne « Compte valide » : ajouter « superviseur : au moins une zone de son niveau ». Ajouter « Échantillon : collecte ou site demandeur obligatoire » et « Connexion : blocage après cinq échecs consécutifs ».
- **Annexe C, aide-mémoire** : « Comptes créés avec profil et périmètre minimaux » ajoute « superviseurs affectés à leur zone ». Contrôles hebdomadaires : ajouter « comptes bloqués à débloquer ou à désactiver ».
