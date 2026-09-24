# Réglages retenus — LSTracker

Réponse au cahier des charges, chapitre VII.2 : réglage de PostgreSQL et des
paramètres applicatifs selon la charge attendue, et **documentation des
paramètres retenus** pour l'exploitation et la reproductibilité.

Tous les réglages sont dans le dépôt, donc versionnés et livrés avec le bundle :

| Fichier | Contenu |
|---|---|
| `config/postgres/postgresql.prod.conf` | PostgreSQL de production |
| `config/postgres/postgresql.demo.conf` | PostgreSQL de démonstration (pré-production) |
| `docker-compose.prod.yml`, `docker-compose.demo.yml` | Limites processeur et mémoire des conteneurs, options de la JVM |
| `src/main/resources/application.properties` | Pool de connexions, lots, délais (surchargeables par `.env`) |

---

## 1. Dimensionnement : à vérifier avant la migration de la production

Les valeurs de production supposent un serveur confortable. Les conteneurs
réservent et plafonnent la mémoire :

| Conteneur | Processeur (plafond) | Mémoire réservée | Mémoire plafond |
|---|---|---|---|
| Base de production | 6 | 10 Gio | 18 Gio |
| Application de production | 7 | 12 Gio | 18 Gio |
| Base de démonstration | 2 | 2 Gio | 5 Gio |
| Application de démonstration | 2 | 3 Gio | 5 Gio |

La somme des réservations (27 Gio) doit tenir dans la mémoire du serveur, en
laissant de la place au système, à nginx, à la supervision et aux autres
bases hébergées. **Avant la migration**, relever la taille réelle :

```bash
nproc            # nombre de cœurs
free -h          # mémoire totale et disponible
df -h /var/lib/docker
docker stats --no-stream
```

Si le serveur a moins de 48 Gio de mémoire, réduire dans l'ordre : le plafond
de l'application (la JVM prend 75 % de sa limite), puis `shared_buffers` et
`effective_cache_size` selon les règles du tableau ci-dessous.

---

## 2. PostgreSQL

Règles de calcul usuelles, appliquées à la mémoire **allouée à la base**
(plafond du conteneur), pas à celle du serveur.

| Paramètre | Production | Démonstration | Règle et raison |
|---|---|---|---|
| `max_connections` | 200 | 50 | Au moins le pool de l'application (`DB_POOL_MAX`) × nombre d'instances, plus les connexions d'exploitation (sauvegarde, supervision). |
| `shared_buffers` | 6 GB | 1536 MB | Environ 25 à 35 % de la mémoire de la base. |
| `effective_cache_size` | 16 GB | 4 GB | Environ 75 % de la mémoire de la base : estimation du cache disponible, utilisée par le planificateur. |
| `work_mem` | 32 MB | 16 MB | Mémoire par tri ou agrégat ; les requêtes du tableau de bord (médianes, regroupements) en profitent. Multipliée par les opérations simultanées : rester modéré. |
| `maintenance_work_mem` | 1 GB | 256 MB | Création d'index, `VACUUM`, restauration plus rapides. |
| `wal_buffers` | 16 MB | 8 MB | Valeur usuelle. |
| `checkpoint_timeout` / `checkpoint_completion_target` | 15 min / 0,9 | idem | Points de contrôle étalés : pas de pic d'écriture. |
| `max_wal_size` / `min_wal_size` | 4 GB / 1 GB | 1 GB / 256 MB | Moins de points de contrôle forcés lors des synchronisations en masse. |
| `wal_compression` | on | on | Moins d'écritures disque. |
| `random_page_cost` | 1,1 | 1,1 | Disques SSD : l'accès aléatoire coûte presque autant que l'accès séquentiel ; favorise les index. |
| `effective_io_concurrency` | 200 | 100 | SSD. |
| `max_parallel_workers_per_gather` | 4 | 2 | Parallélisme des agrégats du tableau de bord ; au plus la moitié des cœurs alloués. |
| `max_worker_processes` / `max_parallel_workers` | 8 / 8 | 4 / 4 | Selon les cœurs alloués. |
| `autovacuum_max_workers` / `autovacuum_naptime` | 4 / 30 s | 2 / 60 s | Table `sample` très mise à jour : nettoyage fréquent. |
| `log_min_duration_statement` | 1000 ms | 500 ms | Journal des requêtes lentes (dans `pg_log`). |
| `log_lock_waits`, `log_temp_files`, `log_autovacuum_min_duration` | activés | — | Diagnostic en production. |
| Mémoire partagée du conteneur (`shm_size`) | 1 Go | 256 Mo | Requêtes parallèles ; les 64 Mo par défaut de Docker s'épuisent sous charge (voir `docs/TEST_CHARGE.md`). |
| `password_encryption` | scram-sha-256 | idem | Sécurité. |
| `timezone` | Africa/Abidjan | idem | Dates cohérentes avec l'application. |

Vérifier les valeurs actives :

```bash
docker exec lst_prod_db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT name, setting, unit FROM pg_settings WHERE name IN
   ('max_connections','shared_buffers','effective_cache_size','work_mem','random_page_cost');"
```

---

## 3. Application

| Paramètre | Valeur | Où | Raison |
|---|---|---|---|
| Pool de connexions (`DB_POOL_MAX` / `DB_POOL_MIN`) | prod 50 / 10, démo 15 / 3 | `.env` | Nombre de requêtes simultanées vers la base ; rester sous `max_connections`. |
| Délais du pool | inactivité 5 min, durée de vie 20 min, attente 20 s, fuite signalée après 30 s | `application.properties` | Connexions recyclées ; une connexion oubliée est signalée dans les journaux. |
| Plans d'exécution et durée maximale | `plan_cache_mode = force_custom_plan`, `statement_timeout` 120 s | `application.properties` (`DB_STATEMENT_TIMEOUT`) | Plans calculés avec les valeurs réelles des filtres ; une requête emballée ne bloque pas une connexion plus de 2 minutes (voir `docs/TEST_CHARGE.md`). |
| Écritures par lots Hibernate | 50 | `application.properties` | Synchronisation mobile : insertions groupées. |
| Mémoire de la JVM | 75 % du plafond du conteneur | `JAVA_OPTS` (compose) | La JVM respecte la limite du conteneur ; arrêt propre et redémarrage en cas de mémoire épuisée. |
| Synchronisation OpenELIS | lots de 200, toutes les 30 min, 5 essais par échantillon | `.env` (`OEDATAREPO_*`) | Charge étalée ; un échantillon introuvable n'est pas réinterrogé indéfiniment. |
| Durée du jeton mobile | 30 jours | `.env` (`JWT_EXPIRATION_MINUTES`) | Terrain souvent hors ligne. |
| Ressources statiques | cache navigateur 1 an, empreinte dans l'URL | `MvcConfig` | Pages plus rapides ; un fichier modifié change d'URL. |
| Téléversement | 25 Mo par fichier | `application.properties` | Manuels d'aide PDF. |
| Purges des journaux | chaque nuit (3 h 30 et 3 h 40), 12 mois conservés | propriétés `lstracker.connection-log.purge-cron` et `lstracker.activity-log.purge-cron` | Données personnelles limitées dans le temps. |

---

## 4. Mesures de référence

- Tableau de bord sur 600 000 échantillons simulés : 2,35 s avant la revue
  des requêtes, 0,87 s après (index de jointure, filtres de dates indexables,
  agrégation avant jointure géographique).
- Test de montée en charge : `docs/TEST_CHARGE.md` (0 erreur à 50 utilisateurs
  simultanés sur 600 000 échantillons).
- Requêtes lentes : suivies par `log_min_duration_statement` et par la
  supervision (`docs/SUPERVISION.md`, alerte au-delà de 2 s au 95ᵉ centile).
