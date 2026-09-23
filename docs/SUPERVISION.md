# Supervision — LSTracker

Réponse au cahier de spécifications, chapitre IX.2 : supervision des serveurs
d'application et de base de données, alertes en cas d'anomalie, suivi de la
disponibilité, de la charge et des temps de réponse.

---

## Ce que l'application expose

Tous les points de gestion sont servis sur un **port interne dédié**
(`MANAGEMENT_PORT`, 9300 par défaut), qui n'est **jamais publié** par les
composes ni par nginx. Le port public (9200) répond 404 sur `/actuator/*`.

| Point | Contenu |
|---|---|
| `:9300/actuator/health` | État détaillé : base de données, espace disque, vivacité. Utilisé par le healthcheck du conteneur. |
| `:9300/actuator/prometheus` | Métriques au format Prometheus. |

Métriques utiles, toutes étiquetées `application="lstracker"` et
`environment` (`APP_ENV` : `demo`, `prod`) :

| Métrique | Sens |
|---|---|
| `http_server_requests_seconds_*` | Temps de réponse par URL et par code HTTP (histogramme : p95, p99). |
| `hikaricp_connections_*` | Pool de connexions à la base (actives, en attente). |
| `jvm_memory_*`, `process_*` | Mémoire, processeur, durée de fonctionnement. |
| `lstracker_oedatarepo_sync_samples_total{outcome}` | Échantillons examinés, mis à jour, en erreur par la synchronisation OpenELIS. |
| `lstracker_oedatarepo_sync_last_run_seconds` | Fin du dernier lot de synchronisation (epoch). |

---

## La pile de supervision

`docker-compose.monitoring.yml`, fourni dans le bundle de déploiement :

| Service | Rôle | Accès |
|---|---|---|
| Prometheus | Collecte toutes les 30 s, conserve 30 jours, évalue les alertes | `127.0.0.1:9090` |
| Alertmanager | Regroupe et envoie les alertes | `127.0.0.1:9093` |
| node-exporter | Processeur, mémoire, disque, réseau du serveur | interne |
| postgres-exporter (démo, prod) | État et charge des bases | interne |

Les interfaces ne sont liées qu'à `127.0.0.1` : on les consulte par tunnel SSH.

```bash
ssh -L 9090:127.0.0.1:9090 -L 9093:127.0.0.1:9093 itech@serveur
# puis http://localhost:9090 (Prometheus) et http://localhost:9093 (alertes)
```

### Alertes prévues (`config/monitoring/alerts.yml`)

| Alerte | Déclenchement | Gravité |
|---|---|---|
| ApplicationIndisponible | Application injoignable 2 min | critique |
| BaseDeDonneesIndisponible | PostgreSQL injoignable 2 min | critique |
| EspaceDisqueFaible | Moins de 10 % d'espace disque 10 min | critique |
| SanteApplicationDegradee | Plus de 5 % de réponses 5xx sur 10 min | alerte |
| TempsDeReponseEleve | p95 des temps de réponse > 2 s pendant 10 min | alerte |
| PoolDeConnexionsSature | Requêtes en attente de connexion 5 min | alerte |
| MemoireJvmSaturee | Mémoire JVM > 90 % pendant 10 min | alerte |
| MemoireServeurFaible | Moins de 10 % de mémoire libre 10 min | alerte |
| ChargeProcesseurElevee | Processeur > 90 % pendant 15 min | alerte |
| SynchroOpenElisArretee | Aucun lot de synchronisation depuis 2 h | alerte |
| ErreursSynchroOpenElis | Plus de 50 erreurs de synchronisation en 1 h | alerte |

Les seuils sont des valeurs de départ, à ajuster après quelques semaines
d'observation.

---

## Mise en place (une fois)

1. **Compte PostgreSQL de supervision**, en lecture seule, dans chaque base
   (démo et prod) :

   ```bash
   docker exec -it lst_prod_db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
     "CREATE ROLE lst_monitoring LOGIN PASSWORD '<mot de passe>'; GRANT pg_monitor TO lst_monitoring;"
   ```

2. **Fichier d'environnement** :

   ```bash
   cp .env.monitoring.example .env.monitoring && chmod 600 .env.monitoring
   # renseigner PG_DEMO_DSN et PG_PROD_DSN avec le compte ci-dessus
   ```

3. **Destinataires des alertes** : compléter `config/monitoring/alertmanager.yml`
   (serveur SMTP, adresses). Sans cela, les alertes restent visibles dans
   l'interface d'Alertmanager mais ne sont pas envoyées.

4. **Démarrage**, après les stacks démo et prod (la supervision rejoint leurs
   réseaux Docker) :

   ```bash
   docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml up -d
   ```

5. **Vérification** : dans Prometheus, *Status → Targets* doit montrer toutes
   les cibles `UP`. Une stack non déployée (ex. la prod pas encore migrée)
   apparaît `DOWN` et déclenche `ApplicationIndisponible` : retirer sa cible de
   `config/monitoring/prometheus.yml` tant qu'elle n'existe pas.

---

## Points d'attention

- **Healthcheck du conteneur** : il interroge désormais le port 9300. Un
  compose d'une version antérieure (healthcheck sur 9200) marquerait le
  conteneur *unhealthy* avec cette image : toujours déployer l'image avec les
  composes **du même bundle**.
- **Sauvegardes** (IX.2, troisième point) : couvertes par
  `scripts/backup-db.sh` et `scripts/restore-db.sh`, voir `docs/OPERATIONS.md`.
