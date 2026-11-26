# Backlog 2.2.4 : points relevés sur la version 2.2.3

Ces points complètent le périmètre de la version 2.2.4 (cibles paramétrables, indicateurs du cadre de performance de la DAP, rapports selon les modèles de la DAP, transfert d'échantillons). Aucun ne bloque la recette de la 2.2.3. Pour chacun : le constat dans la 2.2.3, ce qui est attendu, et ce qui reste à trancher.

## 1. Fiche échantillon en lecture seule pour les profils de consultation

**Constat.** Les profils de consultation (pilotage national, superviseurs, utilisateur) n'ont que la fenêtre « Voir détail » de la liste des échantillons. La fiche complète (`/sample/update/{id}`) est un formulaire de modification : ouverte par son adresse, elle s'affiche pour un échantillon du périmètre, mais l'enregistrement est refusé (403), ce qui est déroutant.

**Attendu.** Une fiche en lecture seule, avec les mêmes informations que la fiche de modification (identification, site, laboratoires, dates d'étape, rejet, kilométrage, historique), accessible aux profils de consultation dans la limite de leur périmètre. La fiche de modification reste réservée aux administrateurs ; un profil de consultation qui ouvre son adresse est renvoyé vers la fiche en lecture seule.

## 2. Laboratoires proposés à un superviseur

**Constat.** Un superviseur régional ou de district ne voit que les échantillons collectés dans ses zones, sans accès par le laboratoire destinataire. La liste « Laboratoire » de ses filtres n'a pas de règle métier arrêtée : deux lectures sont possibles.

**À trancher par la DAP.**
- **Laboratoires situés dans la zone** : la liste est stable et parle à un responsable de zone, mais un laboratoire hors zone qui reçoit les échantillons de la zone n'y figure pas.
- **Laboratoires qui reçoivent les échantillons de la zone** : la liste suit les flux réels (y compris vers une plateforme d'une autre région), mais elle varie avec les données et la période.

Dans les deux cas, choisir un laboratoire ne doit pas élargir le périmètre : le superviseur ne voit que les échantillons de sa zone reçus par ce laboratoire.

## 3. Lien entre collecte et trajet jamais renseigné

**Constat.** `sample_retrieving.ride_id` n'est renseigné pour aucune collecte (0 sur 3 263 sur la base de test). La carte « Circuits » déduit donc le circuit du site de collecte ; les échantillons d'un site desservi par plusieurs circuits restent sur la ligne « Sites partagés entre plusieurs circuits ».

**Attendu.** Le mobile enregistre le trajet (et donc le circuit) au moment de la collecte et la synchronisation transmet `ride_id`. Le tableau de bord utilise déjà le trajet quand il existe : aucune modification de la carte n'est nécessaire. À vérifier : compatibilité avec les mobiles non mis à jour (collecte sans trajet acceptée, comme aujourd'hui).

## 4. Changement de mot de passe sur le mobile

**Constat.** Après une réinitialisation, `/api_v2/auth/login` renvoie `password_reset: true`, mais le mobile n'en tient pas compte : l'utilisateur peut travailler indéfiniment avec le mot de passe temporaire, alors que le web impose de le changer.

**Attendu.** Quand l'indicateur est vrai, le mobile ouvre un écran de changement de mot de passe avant toute autre action ; un point d'accès de l'API permet ce changement et lève l'indicateur. Le serveur pourra ensuite refuser les appels d'un compte dont l'indicateur reste levé.

## 5. Carte « Circuits » : mention « N premiers circuits sur M »

**Constat.** La carte affiche les cinq circuits les plus actifs, suivis des lignes « Sites partagés entre plusieurs circuits » et « Non rattaché ». Rien n'indique combien de circuits ont eu une activité sur la période : la somme des lignes affichées peut être inférieure au total collecté sans que le lecteur sache pourquoi.

**Attendu.** Sous le titre, une mention « 5 premiers circuits sur M » (M = circuits actifs ayant au moins un échantillon sur la période), et un lien ou un export vers la liste complète.

## 6. Écart entre la liste des échantillons et le tableau de bord

**Constat.** Sur une même période, un superviseur obtient des nombres différents entre la liste des échantillons et certaines cartes du tableau de bord. La liste filtre sur la date de collecte ; chaque carte d'étape du tableau de bord compte les échantillons dont la date de cette étape tombe dans la période (par exemple la date de rendu pour « Résultats rendus aux sites »). Les deux sont justes, mais la différence n'est pas expliquée à l'écran. Le lien d'une ligne du tableau de répartition transmet ses filtres à la liste et donne le même nombre que cette ligne.

**À trancher.** Afficher, sur chaque carte et dans la liste, la date de référence utilisée ; ou proposer dans la liste un choix de date de référence (collecte, dépôt, rendu des résultats) pour retrouver le nombre d'une carte. Le contrôle de cohérence devra couvrir la règle retenue.

## 7. Appel déprécié de Liquibase dans les tests

**Constat.** Le test sur PostgreSQL `SampleWithoutCollectionTest` crée le schéma par `new Liquibase(...).update("")`, API dépréciée de Liquibase 4 et appelée à disparaître.

**Attendu.** Remplacer cet appel par la commande `update` de l'API `CommandScope`, ou faire créer le schéma par le démarrage Spring comme les autres tests sur PostgreSQL. Aucun effet sur l'application, qui applique les changesets par l'intégration Spring Boot.
