# Recette de la version 2.2.3 : liste de contrôle

Liste de contrôle manuelle de la version 2.2.3 (web 2.2.3, mobile 2.2.3 build 10), à dérouler sur l'environnement de démonstration avant la signature du procès-verbal de réception. Chaque scénario indique le compte à utiliser, les étapes et le résultat attendu ; la colonne « Constat » est remplie pendant la recette (conforme, non conforme avec description).

Les chiffres cités en exemple sont ceux de la base de développement (2 995 échantillons, régions PORO et HAUT-SASSANDRA) ; sur la démonstration, seuls les comportements comptent, pas les valeurs.

## Comptes de test

Les comptes ci-dessous existent dans la base de développement. Sur la démonstration, l'administration centrale les crée avec les mêmes caractéristiques avant la recette. Les mots de passe sont remis à part, jamais dans ce document.

| Compte | Profil | Affectation | Sert à vérifier |
|---|---|---|---|
| administrateur national (compte ADMIN existant) | Administrateur national | National | Administration, vue nationale, référence des totaux |
| administrateur système (compte SUPER_ADMIN, à créer si absent) | Administrateur système | National | Attribution du rôle Administrateur système |
| `testmanager` | Pilotage national (consultation) | National | Vue nationale sans écriture |
| `testregion` | Superviseur régional | Région HAUT-SASSANDRA | Cloisonnement par région |
| `testdistrict` | Superviseur de district | District ZOUKOUGBEU (HAUT-SASSANDRA) | Cloisonnement par district |
| `testeur` | Utilisateur (consultation), niveau district | Aucune zone affectée | Compte sans affectation exploitable |
| `Rider2` | Convoyeur | 5 circuits | Mobile convoyeur, libellés |
| `recette_blocage` (à créer) | Utilisateur (consultation) | Une région quelconque | Messages avant blocage, mot de passe temporaire |

Avant de commencer : navigateur en navigation privée (aucun cookie de LSTracker), application mobile 2.2.3 (build 10) installée.

## 1. Préalable : version et retour après connexion

| | |
|---|---|
| Compte | `testmanager` |
| Étapes | Ouvrir une fenêtre de navigation privée ; saisir l'adresse de la démonstration ; se connecter. |
| Attendu | L'en-tête affiche la version 2.2.3. Après connexion, l'utilisateur arrive sur le tableau de bord, jamais sur l'icône du site (adresse en `/favicon-….ico?continue`) ni sur une image ou un fichier. |
| Constat | |

## 2. Profils

### 2.1 Superviseur régional : consultation limitée à sa région

| | |
|---|---|
| Compte | `testregion` |
| Étapes | 1. Ouvrir le tableau de bord. 2. Dérouler les listes Région, District, Site, Laboratoire. 3. Ouvrir Échantillons, filtrer sans critère, exporter en CSV. 4. Saisir dans la barre d'adresse la fiche `sample/update/<id>` d'un échantillon de PORO (identifiant relevé avec le compte administrateur). 5. Générer un rapport PDF de région. 6. Tenter d'ouvrir `/appuser`, `/region`, `/site` par la barre d'adresse. |
| Attendu | 1. Les chiffres ne portent que sur HAUT-SASSANDRA. 2. Les listes ne proposent que « Tout » et les zones et laboratoires de HAUT-SASSANDRA ; PORO n'apparaît nulle part. 3. La liste ne contient que des échantillons de HAUT-SASSANDRA ; l'en-tête du CSV mentionne le périmètre restreint. 4. Accès refusé (403), même réponse que pour un identifiant inexistant. 5. Le rapport ne propose et ne contient que HAUT-SASSANDRA. 6. Aucun menu Administration ; accès refusé à toute écriture d'administration. |
| Constat | |

### 2.2 Superviseur de district : consultation limitée à son district

| | |
|---|---|
| Compte | `testdistrict` |
| Étapes | Mêmes étapes que 2.1. Ouvrir en plus la liste des échantillons et chercher les échantillons 3600 et 3601 (district DALOA). |
| Attendu | Chiffres, listes de filtre, liste des échantillons, export et rapports limités au district ZOUKOUGBEU. Les échantillons 3600 et 3601 n'apparaissent pas. Aucune écriture d'administration. |
| Constat | |

### 2.3 Superviseurs sur l'application mobile

| | |
|---|---|
| Compte | `testregion`, puis `testdistrict` |
| Étapes | Se connecter sur l'application mobile. |
| Attendu | La connexion aboutit et ouvre le tableau de bord de consultation : pas de message « Rôle non reconnu », pas de plantage, aucune action de collecte ni de dépôt. |
| Constat | |

### 2.4 Pilotage national : tout le pays, sans écriture

| | |
|---|---|
| Compte | `testmanager` |
| Étapes | Ouvrir le tableau de bord, puis Administration › Utilisateurs, Régions, Circuits, Connexions, Journal d'activité ; ouvrir `/coherence` par la barre d'adresse. |
| Attendu | Chiffres nationaux, identiques à ceux du compte administrateur pour les mêmes filtres. Les écrans d'administration s'ouvrent en consultation : aucun bouton de création, de modification, de désactivation ou de suppression. Les journaux des connexions et d'activité se consultent et s'exportent. Le contrôle de cohérence s'ouvre et se lance. |
| Constat | |

### 2.5 Libellés des profils et affectation obligatoire

| | |
|---|---|
| Compte | administrateur national, puis administrateur système |
| Étapes | 1. Ouvrir Administration › Utilisateurs et la liste des profils du formulaire de création. 2. Créer un compte au profil Superviseur régional sans région, enregistrer. 3. Même essai pour Superviseur de district sans district. 4. Créer un superviseur régional avec une région ; ouvrir « Affecter les zones ». 5. Avec l'administrateur national, tenter d'attribuer le profil Administrateur système à un compte ; refaire avec l'administrateur système. |
| Attendu | 1. Libellés : « Administrateur national », « Administrateur système », « Pilotage national (consultation) », « Superviseur régional », « Superviseur de district », « Technicien ou superviseur de laboratoire », « Convoyeur », « Utilisateur (consultation) ». 2. Refus : « Un superviseur régional doit être affecté à au moins une région. » 3. Refus : « Un superviseur de district doit être affecté à au moins un district. » 4. Le niveau est fixé à Région, le type à Autre ; l'écran d'affectation s'intitule « Affecter les zones ». 5. Refus pour l'administrateur national ; accepté pour l'administrateur système. Aucun bouton de suppression de compte : un compte se désactive. |
| Constat | |

### 2.6 Compte sans affectation exploitable

| | |
|---|---|
| Compte | `testeur` |
| Étapes | Ouvrir le tableau de bord, la liste des échantillons, l'export CSV. |
| Attendu | Aucune donnée : cartes à zéro, liste vide, export sans ligne. Le compte ne voit jamais les données nationales par défaut. |
| Constat | |

## 3. Filtres du tableau de bord

### 3.1 Option « Tout »

| | |
|---|---|
| Compte | administrateur national |
| Étapes | Ouvrir le tableau de bord et dérouler chaque liste de filtre. |
| Attendu | Chaque liste commence par « Tout », sélectionné à l'ouverture. Revenir à « Tout » sur une liste rétablit les chiffres sans filtre. |
| Constat | |

### 3.2 Type de laboratoire

| | |
|---|---|
| Compte | administrateur national, puis `testregion` |
| Étapes | 1. Choisir « Plateforme de biologie moléculaire » dans Type de labo. 2. Dérouler la liste Laboratoire. 3. Exporter le tableau de répartition. 4. Ouvrir la liste des échantillons et appliquer le même type de labo. |
| Attendu | 1. Les cartes, graphiques et tableaux se recalculent. Seuls les types de laboratoire paramétrés sont proposés, avec leur libellé (sur la base de test : « Laboratoire de district », « CAT », « Plateforme de biologie moléculaire »). 2. Seuls les laboratoires de ce type sont proposés (et, pour `testregion`, seulement ceux de son périmètre). 3. L'en-tête de l'export mentionne le filtre. 4. La liste applique le filtre sur le laboratoire de destination. |
| Constat | |

### 3.3 Type d'échantillon

| | |
|---|---|
| Compte | administrateur national |
| Étapes | Choisir « CV » dans Type d'échantillon ; parcourir cartes, graphiques, tableau de répartition, carte Circuits ; exporter. |
| Attendu | Tous les blocs n'affichent que les échantillons CV ; l'en-tête de l'export mentionne le type. Le filtre s'ajoute au périmètre de l'utilisateur sans le remplacer. |
| Constat | |

### 3.4 Contrôle de cohérence avec les nouveaux filtres

| | |
|---|---|
| Compte | administrateur national |
| Étapes | Vérifier qu'aucun menu ne mène au contrôle de cohérence ; ouvrir `/coherence` par la barre d'adresse ; lancer le contrôle sur l'année, sans filtre, puis avec un type de labo, puis avec un type d'échantillon, puis avec la région HAUT-SASSANDRA. |
| Attendu | Verdict au vert dans chaque cas, carte Circuits comprise (somme des lignes égale au total collecté). |
| Constat | |

## 4. Présentation du tableau de bord

### 4.1 Plus de comparaison avec la période précédente

| | |
|---|---|
| Compte | administrateur national |
| Étapes | Choisir successivement Semaine, Mois, Année ; survoler les cartes et les boutons de période. |
| Attendu | Aucun bandeau « Comparée à », aucune flèche ni pourcentage d'évolution sur les cartes, aucune infobulle qui mentionne la période précédente. |
| Constat | |

### 4.2 Répartition par zone géographique

| | |
|---|---|
| Compte | administrateur national, puis `testregion` |
| Étapes | 1. Faire défiler jusqu'au tableau de répartition ; passer du niveau région au niveau district puis site. 2. Cliquer sur « Détails » d'une ligne. 3. Cliquer sur le lien vers la liste des échantillons de cette ligne. |
| Attendu | 1. Titre « Répartition par zone géographique », niveau courant dans le sous-titre et le fil d'Ariane. Colonnes : « Échantillons collectés », « Échantillons en cours d'acheminement », « Échantillons déposés au laboratoire », « Échantillons non conformes », « Échecs d'analyse », « Résultats rendus aux sites », « TAT médian », « Détails ». Plus de colonnes « Rang » ni « Écart moy. ». 2. Une ligne se déplie : répartition par type d'échantillon, durée médiane par étape, non-conformités par motif. 3. La liste s'ouvre avec les mêmes filtres. Pour l'administrateur, elle donne le même nombre d'échantillons que la ligne. Pour un superviseur, un écart de 2 échantillons est connu et reporté à la version 2.2.4 (`docs/BACKLOG_2.2.4.md`, point 6) : le relever sans le compter comme non-conformité. |
| Constat | |

### 4.3 Carte « Circuits »

| | |
|---|---|
| Compte | administrateur national, puis `testregion` |
| Étapes | Repérer la carte « Circuits » dans les indicateurs de performance ; lire l'infobulle ; additionner les collectes des lignes ; comparer au total « Échantillons collectés ». |
| Attendu | La carte « Convoyeurs les plus actifs » a disparu, la mini-carte « Convoyeurs actifs » est toujours là. Pour chaque circuit : échantillons collectés, déposés, délai médian d'acheminement (collecte au premier dépôt), sites desservis. Les cinq circuits les plus actifs sont suivis, s'il y a lieu, de la ligne « Sites partagés entre plusieurs circuits » (échantillons d'un site membre de plusieurs circuits actifs, par exemple le CHR de Korhogo) et de la ligne « Non rattaché » (site membre d'aucun circuit actif, ou sans site). Toutes lignes comprises, la somme des collectes égale le total collecté quand moins de six circuits sont actifs ; sinon le contrôle de cohérence le vérifie. Pour `testregion`, seuls des circuits de HAUT-SASSANDRA apparaissent. |
| Constat | |

### 4.4 « En cours d'acheminement »

| | |
|---|---|
| Compte | administrateur national ; `Rider2` sur le mobile |
| Étapes | Parcourir cartes, tableau de répartition, infobulles ; exporter le tableau et la liste en CSV ; ouvrir la liste des statuts (Administration › Statuts) ; générer un rapport PDF ; sur le mobile, ouvrir le tableau de bord laboratoire et une fiche échantillon. |
| Attendu | Le mot « transit » n'apparaît plus : « En cours d'acheminement » partout, y compris dans les exports, le libellé du statut, les rapports et le mobile. |
| Constat | |

### 4.5 TAT sans coloration

| | |
|---|---|
| Compte | administrateur national |
| Étapes | Regarder le tableau « Durée médiane par étape » et la colonne TAT de la répartition ; survoler leurs infobulles. |
| Attendu | Valeurs affichées sans couleur verte, orange ou rouge ; aucune infobulle ne décrit de seuil de 3 ou 7 jours. |
| Constat | |

## 5. Libellés

| | |
|---|---|
| Compte | administrateur national ; `Rider2` sur le mobile |
| Étapes | Parcourir le tableau de bord, la liste des échantillons et le mobile. |
| Attendu | Titres : « Étapes de l'échantillon », « Étapes des échantillons par type », « Évolution des échantillons dans le temps », « Échantillons collectés par type », « Durée médiane par étape, en jours (échantillons de la période) », « en cours d'acheminement actuellement », « Résultats récupérés au laboratoire », « Résultats rendus aux sites », « Laboratoires les plus lents (délai au laboratoire) », « Échantillons non conformes par site (taux) ». Le statut des résultats récupérés s'affiche « RESULTATS RECUPERES » (sauf libellé modifié par l'administration). Aucun tiret cadratin ni demi-cadratin dans les textes affichés : valeur absente « - » dans les listes (« Non renseigné » sur les fiches mobiles), pagination « 1 à 25 sur 120 », période des rapports « X au Y ». Sur le mobile, l'action « Récupérer les résultats » remplace « Collecter résultats ». |
| Constat | |

## 6. Connexion et comptes

### 6.1 Messages avant blocage du compte

| | |
|---|---|
| Compte | `recette_blocage`, puis un identifiant inexistant (par exemple `inconnu_recette`) |
| Étapes | 1. Saisir cinq fois un mauvais mot de passe sur la page de connexion. 2. Saisir ensuite le bon mot de passe. 3. Avec l'administrateur national, déverrouiller le compte ; se reconnecter. 4. Refaire l'étape 1 avec l'identifiant inexistant. 5. Refaire l'étape 1 depuis l'application mobile avec un autre compte de test. |
| Attendu | 1. Échecs 1 et 2 : « Identifiant ou mot de passe incorrect. » ; échec 3 : « … Il vous reste 2 tentative(s) avant le blocage du compte. » ; échec 4 : « … 1 tentative(s) … » ; échec 5 : « Compte bloqué après 5 tentatives. Contactez l'administration centrale. » 2. Connexion refusée, compte verrouillé. 3. Connexion acceptée, compteur remis à zéro. 4. Mêmes messages, dans le même ordre : rien ne permet de savoir que l'identifiant n'existe pas. 5. Mêmes messages sur le mobile. Chaque tentative figure dans Administration › Connexions. |
| Constat | |

### 6.2 Mot de passe temporaire

| | |
|---|---|
| Compte | administrateur national, puis `recette_blocage` |
| Étapes | 1. L'administrateur réinitialise le mot de passe de `recette_blocage` depuis la liste des utilisateurs. 2. Se connecter avec le mot de passe temporaire sur le web ; essayer d'ouvrir le tableau de bord et la liste des échantillons. 3. Choisir un nouveau mot de passe. 4. Se connecter avec le mot de passe temporaire depuis le mobile (avant l'étape 3, sur un autre compte réinitialisé). |
| Attendu | 1. Aucune erreur 403 ; message de confirmation. 2. La connexion aboutit (le compte n'est pas considéré comme expiré) ; toute page renvoie vers le changement de mot de passe. 3. Navigation normale ; la session en cours reste ouverte, les autres sessions du compte sont fermées. 4. Connexion acceptée ; la réponse de l'API porte l'indicateur `password_reset` (le mobile n'impose pas encore le changement). |
| Constat | |

### 6.3 Actions d'administration en arrière-plan (jeton anti-falsification)

| | |
|---|---|
| Compte | administrateur national |
| Étapes | Depuis Administration › Utilisateurs, désactiver puis réactiver un compte, le verrouiller puis le déverrouiller, réinitialiser son mot de passe. Refaire une action depuis la fiche d'un utilisateur. |
| Attendu | Chaque action aboutit avec son message de confirmation, sans erreur 403. Avec `testmanager`, ces actions ne sont pas proposées. |
| Constat | |

## 7. Échantillons sans collecte et sans zone

| | |
|---|---|
| Compte | administrateur national, `testregion`, `testdistrict` |
| Étapes | 1. Avec l'administrateur, ouvrir les fiches des échantillons 3600 et 3601 (sans collecte ; site demandeur CHR PUBLIC DE DALOA et CSR DM PUBLIC de BANDIAHI, district DALOA). 2. Vérifier leur présence dans la répartition par zone (HAUT-SASSANDRA, district DALOA). 3. Avec `testregion`, les retrouver dans la liste et ouvrir leur détail. 4. Avec `testdistrict`, les chercher. 5. Avec l'administrateur, vider le site demandeur d'un de ces échantillons et enregistrer. 6. Envoyer un `POST /sample` (outil HTTP) avec une session administrateur. |
| Attendu | 1. La fiche s'ouvre ; le site affiché est le site demandeur. 2. Ils sont comptés dans leur zone : les totaux de la répartition égalent ceux des cartes. 3. Visibles, détail accessible. 4. Absents (autre district). 5. Refus : « Échantillon sans collecte ni site demandeur : renseignez la collecte ou le site demandeur pour l'enregistrer. » ; aucun message de succès. 6. Réponse 404 ou 405, rien n'est enregistré. Si la base contient des échantillons sans collecte ni site demandeur, la répartition affiche pour un compte national une ligne « Zone non renseignée », absente pour un superviseur. |
| Constat | |

## 8. Synthèse

| Bloc | Scénarios | Conformes | Non conformes |
|---|---|---|---|
| Préalable | 1 | | |
| Profils | 2.1 à 2.6 | | |
| Filtres | 3.1 à 3.4 | | |
| Présentation | 4.1 à 4.5 | | |
| Libellés | 5 | | |
| Connexion et comptes | 6.1 à 6.3 | | |
| Échantillons sans zone | 7 | | |

Toute non-conformité est décrite (écran, compte, filtres, capture) et reportée à l'équipe de développement avant la signature du procès-verbal.
