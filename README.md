# LSTracker — Lab Sample Tracker

Application web et backend pour le suivi des échantillons médicaux en transport entre sites de collecte et laboratoires d'analyse (PNLS / PNLT / PNLP — Côte d'Ivoire).

Stack : **Spring Boot 3.2** · **Java 17** · **PostgreSQL 14** · **Thymeleaf** · **JasperReports**. App mobile compagnon : [LSTracker Mobile](https://github.com/ITECH-CI/LSTracker_mobile) (Flutter).

---

## Documentation détaillée

| Document | Quand le lire |
|---|---|
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Runbook complet de déploiement initial (DEMO puis PROD) |
| [docs/NGINX.md](docs/NGINX.md) | Reverse proxy nginx (host) : migration apache2, vhosts, certs SSL |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Procédures d'opérations courantes (backups, updates, rotation secrets, monitoring, troubleshooting) |
| [scripts/](scripts/) | Scripts d'automatisation : `backup-db.sh`, `restore-db.sh`, `rotate-secrets.sh`, `check-cert-expiry.sh` |

---

## Sommaire

1. [Architecture](#architecture)
2. [Prérequis](#prérequis)
3. [Installation locale (développement)](#installation-locale-développement)
4. [Déploiement serveur — vue d'ensemble](#déploiement-serveur--vue-densemble)
5. [Déploiement PRODUCTION](#déploiement-production)
6. [Déploiement DEMO](#déploiement-demo)
7. [Pipeline CI/CD (GitHub Actions + ghcr.io)](#pipeline-cicd-github-actions--ghcrio)
8. [Opérations courantes](#opérations-courantes)
9. [Sauvegardes / restauration](#sauvegardes--restauration)
10. [Mise à jour en production](#mise-à-jour-en-production)
11. [Tuning et dimensionnement](#tuning-et-dimensionnement)
12. [Dépannage](#dépannage)

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  Serveur Linux (96 GiB RAM, 24 cores)                        │
│                                                              │
│  ┌─────────────────────┐    ┌─────────────────────┐         │
│  │  PROD  (port 9200)  │    │  DEMO  (port 9201)  │         │
│  │                     │    │                     │         │
│  │  ┌──────────────┐   │    │  ┌──────────────┐   │         │
│  │  │ Spring Boot  │   │    │  │ Spring Boot  │   │         │
│  │  │  18 G / 7 c  │   │    │  │  5 G / 2 c   │   │         │
│  │  └──────┬───────┘   │    │  └──────┬───────┘   │         │
│  │         │           │    │         │           │         │
│  │  ┌──────▼───────┐   │    │  ┌──────▼───────┐   │         │
│  │  │  Postgres 14 │   │    │  │  Postgres 14 │   │         │
│  │  │  18 G / 6 c  │   │    │  │  5 G / 2 c   │   │         │
│  │  └──────────────┘   │    │  └──────────────┘   │         │
│  │  Network: lst_prod  │    │  Network: lst_demo  │         │
│  └─────────────────────┘    └─────────────────────┘         │
│                                                              │
│  Total conteneurs : 46 GiB RAM + 17 cores                   │
│  Réservé à l'host : ~50 GiB RAM + 7 cores pour l'OS et      │
│  les services (sshd, monitoring, backups, etc.).            │
└──────────────────────────────────────────────────────────────┘
```

Les deux environnements sont **isolés** : networks Docker distincts, bases de données distinctes, secrets JWT distincts.

---

## Prérequis

### Serveur de déploiement
- Linux x86_64 (testé : Ubuntu 22.04 LTS, Debian 12)
- Docker Engine ≥ 24 (recommandé : 26+)
- Docker Compose plugin v2 (`docker compose`, pas `docker-compose`)
- 96 GiB RAM, 24 cores (cf. [tuning](#tuning-et-dimensionnement) pour autres tailles)
- ≥ 100 GiB SSD pour les volumes DB + logs

### Développement local
- JDK 17 (`eclipse-temurin-17` recommandé)
- Maven 3.9+
- Docker Desktop (pour la DB locale uniquement)
- Un IDE Java (IntelliJ IDEA, VS Code + Extension Pack for Java)

---

## Installation locale (développement)

### 1. Cloner et configurer

```bash
git clone <repo-url> labSampleTracker
cd labSampleTracker
cp .env.example .env
```

Édite `.env` et remplace les valeurs de `POSTGRES_PASSWORD` et `JWT_SECRET`.

### 2. Lancer la base de données locale uniquement

```bash
docker compose up -d tracker_db
```

(Le `docker-compose.yml` à la racine est dédié au développement local — il expose le port `5435` sur l'hôte.)

### 3. Lancer l'app Spring Boot depuis l'IDE ou le terminal

```bash
./mvnw spring-boot:run
```

L'app écoute sur `http://localhost:9200`.
Liquibase joue automatiquement les migrations au démarrage.

### 4. Compte admin par défaut

Cherche dans `src/main/resources/db/changelog/changes/sql/` les seeds d'utilisateurs initiaux.

---

## Déploiement serveur — vue d'ensemble

Une seule machine héberge **prod et demo**, chacune dans son propre `docker compose project` (`lst_prod` et `lst_demo`), avec :

- son réseau Docker isolé
- ses volumes nommés
- son fichier `.env` séparé
- ses ports publics distincts (`9200` pour prod, `9201` pour demo)

L'image Docker de l'app est **construite par GitHub Actions** et publiée sur **GitHub Container Registry** (`ghcr.io`). Le serveur fait juste `docker pull` puis `docker compose up`.

Fichiers de configuration :

| Fichier | Rôle | Committé ? |
|---|---|---|
| `Dockerfile` | Build multi-stage de l'image runtime | ✅ |
| `docker-compose.prod.yml` | Stack production (app + DB) | ✅ |
| `docker-compose.demo.yml` | Stack démo (app + DB) | ✅ |
| `docker-compose.yml` | Dev local (DB seule en général) | ✅ |
| `.env.prod` | Secrets prod | ❌ (sur le serveur uniquement) |
| `.env.demo` | Secrets demo | ❌ (sur le serveur uniquement) |
| `.env.prod.example` | Template à recopier en .env.prod | ✅ |
| `.env.demo.example` | Template à recopier en .env.demo | ✅ |
| `config/postgres/postgresql.prod.conf` | Tuning Postgres prod | ✅ |
| `config/postgres/postgresql.demo.conf` | Tuning Postgres demo | ✅ |
| `.github/workflows/release.yml` | CI : build + push image | ✅ |

---

## Déploiement PRODUCTION

### Première mise en place

```bash
# Sur le serveur :
mkdir -p /opt/lstracker && cd /opt/lstracker
git clone <repo-url> .                  # ou copier seulement les fichiers utiles

# Préparer la config secrète
cp .env.prod.example .env.prod
nano .env.prod                          # remplir POSTGRES_PASSWORD, JWT_SECRET, APP_IMAGE

# Authentifier Docker auprès de ghcr.io (une fois par serveur)
# Crée un PAT GitHub (classic) avec scope `read:packages`
echo "$GHCR_PAT" | docker login ghcr.io -u <ton-user> --password-stdin

# Pull l'image et démarrer
docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d

# Vérifier
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f tracker_app
curl http://localhost:9200/actuator/health   # devrait répondre {"status":"UP"}
```

### Pourquoi `--env-file` et pas un `.env` au nom auto-détecté ?

Docker Compose lit automatiquement le fichier `.env` à côté du `docker-compose.yml`. On utilise `--env-file` explicitement pour pouvoir avoir **deux fichiers** sans collision (`.env.prod` ET `.env.demo`).

---

## Déploiement DEMO

Même principe, fichier différent :

```bash
cp .env.demo.example .env.demo
nano .env.demo                          # mots de passe différents de la prod !

docker compose --env-file .env.demo -f docker-compose.demo.yml pull
docker compose --env-file .env.demo -f docker-compose.demo.yml up -d

curl http://localhost:9201/actuator/health
```

> ⚠️ Le **JWT_SECRET de la demo doit être différent de la prod**. Sinon un token volé en demo serait valide en prod.

---

## Pipeline CI/CD (GitHub Actions + ghcr.io)

### Comment ça marche

1. Tu développes localement, tu commit/push sur `main`.
2. Quand tu es prêt à releaser, tu **tag** la version :
   ```bash
   git tag v2.2.0
   git push origin v2.2.0
   ```
3. GitHub Actions (`.github/workflows/release.yml`) déclenche :
   - Checkout du code
   - Build multi-stage du `Dockerfile` (Maven + JRE)
   - Push sur `ghcr.io/<owner>/labsampletracker` avec plusieurs tags :
     - `ghcr.io/<owner>/labsampletracker:2.2.0`
     - `ghcr.io/<owner>/labsampletracker:2.2`
     - `ghcr.io/<owner>/labsampletracker:2`
     - `ghcr.io/<owner>/labsampletracker:latest`
4. Sur le serveur, tu mets à jour `.env.prod` avec `APP_IMAGE=ghcr.io/<owner>/labsampletracker:2.2.0` puis tu redéploies (cf. [Mise à jour en production](#mise-à-jour-en-production)).

### Coût

- **Gratuit** pour les repos GitHub publics (illimité storage + bandwidth ghcr.io).
- Pour repos privés : 500 MiB storage + 1 GiB transfer/mois sur compte perso (largement suffisant ici, l'image fait ~250 MiB et n'est tirée que ~1× par déploiement).

### Visibilité du package

Après le premier push, va sur GitHub → ton profil/org → Packages → labsampletracker → Package settings → Change visibility (privé ou public selon ton besoin).

---

## Opérations courantes

### Voir l'état

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.demo.yml ps

# Stats temps réel (RAM/CPU)
docker stats lst_prod_db lst_prod_app lst_demo_db lst_demo_app
```

### Logs

```bash
# Tail
docker compose -f docker-compose.prod.yml logs -f tracker_app
docker compose -f docker-compose.prod.yml logs -f --tail=200 tracker_app

# Logs DB
docker compose -f docker-compose.prod.yml logs -f tracker_db

# Logs persistés (montés sur l'hôte)
tail -f /opt/lstracker/logs/prod/tracker-app.log
```

### Redémarrer une stack

```bash
# Soft (juste l'app, sans recréer la DB)
docker compose -f docker-compose.prod.yml restart tracker_app

# Full (arrêt complet + redémarrage)
docker compose --env-file .env.prod -f docker-compose.prod.yml down
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

### Shell dans un conteneur

```bash
# Shell dans l'app
docker exec -it lst_prod_app sh

# Shell psql dans la DB
docker exec -it lst_prod_db psql -U appuser_prod -d sample_tracker_prod
```

---

## Sauvegardes / restauration

### Backup quotidien (cron sur l'hôte)

Créer `/opt/lstracker/backup.sh` :

```bash
#!/bin/bash
set -euo pipefail

BACKUP_DIR="/var/backups/lstracker"
mkdir -p "$BACKUP_DIR"
DATE=$(date +%F_%H-%M)

# PROD
docker exec lst_prod_db pg_dump -U "$POSTGRES_USER_PROD" -Fc "$POSTGRES_DB_PROD" \
  > "$BACKUP_DIR/prod_${DATE}.dump"

# DEMO (optionnel)
docker exec lst_demo_db pg_dump -U "$POSTGRES_USER_DEMO" -Fc "$POSTGRES_DB_DEMO" \
  > "$BACKUP_DIR/demo_${DATE}.dump"

# Rotation : garder 30 jours
find "$BACKUP_DIR" -name "*.dump" -mtime +30 -delete
```

Crontab : `0 2 * * * /opt/lstracker/backup.sh >> /var/log/lstracker-backup.log 2>&1`

### Restauration

```bash
# Stopper l'app pour éviter les écritures pendant la restauration
docker compose -f docker-compose.prod.yml stop tracker_app

# Restaurer
docker exec -i lst_prod_db pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  --clean --if-exists < /var/backups/lstracker/prod_2026-05-26_02-00.dump

# Redémarrer
docker compose -f docker-compose.prod.yml start tracker_app
```

---

## Mise à jour en production

Procédure type pour passer en `v2.3.0` :

```bash
# 1. Backup avant tout (par sécurité)
/opt/lstracker/backup.sh

# 2. Mettre à jour le tag dans .env.prod
sed -i 's|APP_IMAGE=.*|APP_IMAGE=ghcr.io/itech-ci/labsampletracker:2.3.0|' .env.prod

# 3. Pull la nouvelle image
docker compose --env-file .env.prod -f docker-compose.prod.yml pull tracker_app

# 4. Recréer le conteneur app (Liquibase joue les nouvelles migrations au boot)
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d tracker_app

# 5. Suivre les logs
docker compose -f docker-compose.prod.yml logs -f --tail=100 tracker_app

# 6. Health check
curl http://localhost:9200/actuator/health
```

### Rollback si problème

```bash
# Revenir à l'ancien tag dans .env.prod
sed -i 's|APP_IMAGE=.*|APP_IMAGE=ghcr.io/itech-ci/labsampletracker:2.2.0|' .env.prod
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d tracker_app
```

> ⚠️ Si la nouvelle version contient des migrations Liquibase **non rétro-compatibles**, un rollback nécessite aussi de restaurer un backup DB pré-update.

---

## Tuning et dimensionnement

### Dimensionnement actuel (serveur 96 GiB / 24 cores)

| Service | RAM limit | RAM réservée | CPU limit |
|---|---|---|---|
| PROD Postgres | 24 GiB | 12 GiB | 8 |
| PROD App | 22 GiB | 16 GiB | 8 |
| DEMO Postgres | 6 GiB | 2 GiB | 2 |
| DEMO App | 6 GiB | 3 GiB | 2 |
| **Total max conteneurs** | **58 GiB** | **33 GiB** | **20** |
| **Disponible OS / services** | **≥ 38 GiB** | | **≥ 4** |

### Si tu redimensionnes

Les `limits.memory` et `limits.cpus` sont dans `docker-compose.prod.yml` (et `.demo.yml`). Si tu changes ces valeurs :

- **JVM** : `MaxRAMPercentage=75` calcule automatiquement le heap max à partir de la `limits.memory` du conteneur. Pas besoin de toucher `-Xmx`.
- **Postgres** : édite `config/postgres/postgresql.prod.conf`. La règle générale : `shared_buffers = 25% RAM`, `effective_cache_size = 50-75% RAM`, `work_mem = RAM / (max_connections × ~4)`.

Outil utile : [pgtune.leopard.in.ua](https://pgtune.leopard.in.ua/) (génère un postgresql.conf à partir des specs hardware).

---

## Dépannage

### `docker compose pull` échoue avec "denied"

Tu n'es pas authentifié sur ghcr.io. Fais :
```bash
echo "$GHCR_PAT" | docker login ghcr.io -u <ton-user> --password-stdin
```

### L'app démarre puis crashe avec OutOfMemoryError

La JVM a dépassé sa limite. Vérifie `JAVA_OPTS` et `deploy.resources.limits.memory` sont cohérents (`MaxRAMPercentage=75` doit laisser de la marge pour la native memory : metaspace, threads, etc.).

### Postgres très lent

Connecte-toi : `docker exec -it lst_prod_db psql -U <user> -d <db>` puis :
```sql
SELECT * FROM pg_stat_activity WHERE state != 'idle';
SELECT query, calls, total_exec_time, mean_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC LIMIT 10;
```

Si `pg_stat_statements` n'est pas disponible, l'extension peut être activée dans `postgresql.prod.conf` via `shared_preload_libraries = 'pg_stat_statements'` (nécessite restart Postgres).

### Le healthcheck Docker reste "starting" longtemps

Normal au premier boot : `start_period: 90s` laisse à Spring Boot le temps d'initialiser Liquibase + JPA. Si > 3 min, regarde les logs : `docker compose -f docker-compose.prod.yml logs tracker_app`.

### Conflit de port `9200`

Un autre service utilise déjà ce port. Soit tu le libères, soit tu changes `PROD_PORT=9200` dans `.env.prod` vers une autre valeur.

---

## Annexes

- [CHANGELOG.md](./CHANGELOG.md) — historique des versions
- [TransportDNO/README.md](../TransportDNO/README.md) — app mobile compagnon

## Licence

I-TECH Côte d'Ivoire — usage interne.
