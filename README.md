# LSTracker — Lab Sample Tracker

Application web et backend pour le suivi des échantillons médicaux en transport entre sites de collecte et laboratoires d'analyse (PNLS / PNLT / PNLP — Côte d'Ivoire).

**Stack :** Spring Boot 3.2 · Java 17 · PostgreSQL 14 · Thymeleaf · JasperReports
**App mobile compagnon :** [LSTracker Mobile](https://github.com/ITECH-CI/LSTracker_mobile) (Flutter)

---

## Pour les ops — déployer une release

Tout déploiement (demo ou prod) part d'un **bundle attaché à une GitHub Release**. Pas besoin de cloner ce repo.

```bash
VERSION=2.2.0
URL=https://github.com/ITECH-CI/LSTracker_web/releases/download/v${VERSION}

curl -fsSLO ${URL}/lstracker-deploy-${VERSION}.tar.gz
curl -fsSLO ${URL}/lstracker-deploy-${VERSION}.tar.gz.sha256
sha256sum -c lstracker-deploy-${VERSION}.tar.gz.sha256
tar -xzf lstracker-deploy-${VERSION}.tar.gz
cd lstracker-deploy-${VERSION}
cat INSTALL.md
```

Le bundle contient : composes (demo + prod), `.env.example`, configs nginx & postgres, scripts ops, docs.

**Releases :** https://github.com/ITECH-CI/LSTracker_web/releases

---

## Pour les dev — publier une release

```bash
# Modifier le code, tester en local
mvn spring-boot:run

# Quand prêt : tagger et push
git tag -a v2.3.0 -m "Release v2.3.0 — feature X"
git push origin v2.3.0
```

GitHub Actions s'occupe du reste :
1. Build l'image Docker → `ghcr.io/itech-ci/labsampletracker:2.3.0` (publique)
2. Génère `lstracker-deploy-2.3.0.tar.gz` → attaché à la GitHub Release

Workflow : [.github/workflows/release.yml](.github/workflows/release.yml)

---

## Documentation

| Document | Quand le lire |
|---|---|
| Bundle [`INSTALL.md`](INSTALL.md) | 4-5 commandes pour démarrer demo ou prod sur un serveur |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Runbook complet : provisioning → demo → validation → prod |
| [docs/NGINX.md](docs/NGINX.md) | Reverse proxy nginx (vhosts, certs, migration apache2) |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Backups, restauration, mise à jour, rotation secrets, troubleshooting |
| [scripts/](scripts/) | `backup-db.sh`, `restore-db.sh`, `rotate-secrets.sh`, `check-cert-expiry.sh` |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | Workflow PR depuis un fork, branch protection, conventions commit |

---

## Architecture serveur (déploiement type 96 GiB / 24 cores)

```
┌──────────────────────────────────────────────────────────────┐
│  Internet                                                    │
│       │                                                      │
│       │ HTTPS                                                │
│       ▼                                                      │
│  ┌───────────────────────────────────────┐                  │
│  │   nginx (host)  :80  :443             │                  │
│  └────┬──────────────────────────┬───────┘                  │
│       │ HTTP loopback             │ HTTP loopback           │
│  ┌────▼─────────────────┐    ┌────▼─────────────────┐      │
│  │  PROD  127.0.0.1:9200│    │  DEMO  127.0.0.1:9201│      │
│  │   lstracker.org      │    │  lstracker-demo.     │      │
│  │                      │    │     itech-civ.org    │      │
│  │  ┌──────────────┐    │    │  ┌──────────────┐    │      │
│  │  │ Spring Boot  │    │    │  │ Spring Boot  │    │      │
│  │  │  18 G / 7 c  │    │    │  │  5 G / 2 c   │    │      │
│  │  └──────┬───────┘    │    │  └──────┬───────┘    │      │
│  │         │            │    │         │            │      │
│  │  ┌──────▼───────┐    │    │  ┌──────▼───────┐    │      │
│  │  │  Postgres 14 │    │    │  │  Postgres 14 │    │      │
│  │  │  18 G / 6 c  │    │    │  │  5 G / 2 c   │    │      │
│  │  └──────────────┘    │    │  └──────────────┘    │      │
│  │  Network: lst_prod   │    │  Network: lst_demo   │      │
│  └──────────────────────┘    └──────────────────────┘      │
│                                                              │
│  Total conteneurs : 46 GiB / 17 cores                       │
│  Réservé à l'host : 50 GiB / 7 cores (OS, monitoring, etc.) │
└──────────────────────────────────────────────────────────────┘
```

Networks Docker distincts, bases distinctes, secrets JWT distincts → isolation totale.

**TLS :**
- **PROD** : termination chez le fournisseur de domaine (CDN). nginx reçoit du HTTP avec `X-Forwarded-Proto: https`.
- **DEMO** : termination locale nginx avec cert wildcard `*.itech-civ.org`.

---

## Développement local

Pour bosser sur le code (modifier l'app, tester avant tag) :

```bash
git clone https://github.com/ITECH-CI/LSTracker_web.git
cd LSTracker_web

# Configurer .env (dev)
cp .env.example .env
# Modifier POSTGRES_PASSWORD et JWT_SECRET (cf. .env.example)

# Démarrer la DB locale via Docker Compose dev
docker compose --env-file .env -f docker-compose.yml up -d tracker_db

# Lancer l'app via Maven (hot reload)
./mvnw spring-boot:run

# Ou tout via Docker (build local de l'image)
docker compose --env-file .env -f docker-compose.yml up -d
```

L'app écoute par défaut sur http://localhost:8050 (configurable via `SERVER_PORT` dans `.env`).

---

## Workflow CI/CD

| Trigger | Action |
|---|---|
| Push de tag `v*` | Build image Docker → ghcr.io + génération bundle → GitHub Release |
| Workflow dispatch manuel | Build image + bundle en artifact (sans Release) |

L'image `ghcr.io/itech-ci/labsampletracker` est **publique** → `docker pull` ne nécessite aucune authentification.

---

## Sécurité

- **Pas de secrets en clair dans le code** — `application.properties` n'a aucun fallback de password ou JWT secret. L'app refuse de démarrer si `JWT_SECRET` n'est pas défini.
- **Postgres en loopback** : les ports DB (5435/5436) bindent `127.0.0.1` uniquement.
- **Actuator restreint** : `/actuator/health` accessible depuis l'host pour les healthchecks Docker, bloqué publiquement par nginx.
- **JWT** : secret 256 bits minimum (généré via `openssl rand -hex 64`).
- **Rotation des secrets** : script `scripts/rotate-secrets.sh` avec rollback automatique.

---

## Liens utiles

- **Releases :** https://github.com/ITECH-CI/LSTracker_web/releases
- **Images Docker :** https://github.com/ITECH-CI/packages
- **App mobile :** https://github.com/ITECH-CI/LSTracker_mobile
