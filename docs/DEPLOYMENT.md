# Runbook de déploiement — LSTracker Web

> Workflow simplifié : à chaque release tag `v*`, GitHub Actions build l'image Docker **et** un bundle de déploiement attaché à la GitHub Release. Sur le serveur, un ops télécharge ce bundle (un seul fichier) et lance la stack. Pas de clone git, pas de code source, pas de Maven.

## Sommaire

1. [Vue d'ensemble](#vue-densemble)
2. [Prérequis serveur (une seule fois)](#prérequis-serveur-une-seule-fois)
3. [Récupérer le bundle de release](#récupérer-le-bundle-de-release)
4. [Phase 1 — Déploiement DEMO](#phase-1--déploiement-demo)
5. [Phase 2 — Validation DEMO](#phase-2--validation-demo)
6. [Phase 3 — Déploiement PROD](#phase-3--déploiement-prod)
7. [Phase 4 — Validation PROD](#phase-4--validation-prod)
8. [Rollback](#rollback)
9. [Checklist condensée](#checklist-condensée)

---

## Vue d'ensemble

```
GitHub Actions (sur tag v*)
   │
   ├── Build image Docker        →  ghcr.io/itech-ci/labsampletracker:<version>
   └── Génère bundle             →  lstracker-deploy-<version>.tar.gz
                                     (attaché à la GitHub Release)
                                                │
                                                ▼
                                    ┌──────────────────────┐
                                    │  Serveur de déploiement │
                                    │  curl + tar + docker compose up │
                                    └──────────────────────┘
```

Sur le serveur, deux environnements indépendants côte à côte :

| Élément | DEMO | PROD |
|---|---|---|
| Compose file | `docker-compose.demo.yml` | `docker-compose.prod.yml` |
| Env file | `.env.demo` | `.env.prod` |
| App container | `lst_demo_app` | `lst_prod_app` |
| DB container | `lst_demo_db` | `lst_prod_db` |
| Network | `lst_demo_net` | `lst_prod_net` |
| Port app (loopback) | 127.0.0.1:9201 | 127.0.0.1:9200 |
| Port DB (loopback) | 127.0.0.1:5436 | 127.0.0.1:5435 |
| URL publique (via nginx) | `https://lstracker-demo.itech-civ.org` | `https://lstracker.org` |

Networks Docker distincts → isolation totale demo/prod. Bases de données séparées, secrets JWT séparés.

---

## Prérequis serveur (une seule fois)

Si tu déploies sur un serveur où Docker tourne déjà (cas le plus courant si la prod actuelle tourne sous apache2 + Docker), **saute directement à la section suivante**.

### Installer Docker (si serveur vide)

```bash
# OS : Ubuntu 22.04 LTS ou Debian 12
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker

docker --version          # 24+
docker compose version    # v2+
```

### Firewall (si la demo doit être accessible sans nginx pour validation)

```bash
sudo ufw allow OpenSSH
sudo ufw allow 9201/tcp comment 'LSTracker demo (temporaire pré-nginx)'
sudo ufw enable
```

Pour la **prod via nginx + HTTPS**, voir [NGINX.md](NGINX.md).

---

## Récupérer le bundle de release

Tout commence ici. Choisir la version voulue sur https://github.com/ITECH-CI/LSTracker_web/releases.

```bash
# Variables (adapter la version)
VERSION=2.2.0
URL=https://github.com/ITECH-CI/LSTracker_web/releases/download/v${VERSION}

# Télécharger bundle + checksum
sudo mkdir -p /opt/lstracker
sudo chown $USER:$USER /opt/lstracker
cd /opt/lstracker

curl -fsSLO ${URL}/lstracker-deploy-${VERSION}.tar.gz
curl -fsSLO ${URL}/lstracker-deploy-${VERSION}.tar.gz.sha256

# Vérifier l'intégrité
sha256sum -c lstracker-deploy-${VERSION}.tar.gz.sha256
# lstracker-deploy-2.2.0.tar.gz: OK

# Extraire
tar -xzf lstracker-deploy-${VERSION}.tar.gz
cd lstracker-deploy-${VERSION}

# Vérifier les métadonnées
cat VERSION
ls -la
```

Le dossier contient tout : composes, `.env.example`, configs nginx, scripts ops, docs.

---

## Phase 1 — Déploiement DEMO

### 1.1 Préparer `.env.demo`

```bash
cd /opt/lstracker/lstracker-deploy-2.2.0
cp .env.demo.example .env.demo

# Générer les secrets (à copier dans .env.demo)
echo "POSTGRES_PASSWORD=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)"
echo "JWT_SECRET=$(openssl rand -hex 64)"

nano .env.demo
# Remplacer toutes les valeurs CHANGE_ME_*

# Vérifier qu'il n'y a plus aucun CHANGE_ME
grep CHANGE_ME .env.demo && echo "ENCORE DU CHANGE_ME" || echo "OK"

# Protéger le fichier (secrets)
chmod 600 .env.demo
```

### 1.2 Démarrer DEMO

**Sans nginx (phase de validation initiale)** — l'app sera accessible directement sur le port 9201 :

```bash
docker compose --env-file .env.demo \
               -f docker-compose.demo.yml \
               -f docker-compose.demo.public.yml \
               up -d

docker ps --filter "name=lst_demo_"
docker logs lst_demo_app -f --tail 50
# Attendre "Started LabSampleTrackerApplication" puis Ctrl+C
```

**Avec nginx** (recommandé une fois nginx en place — cf. [NGINX.md](NGINX.md)) :

```bash
docker compose --env-file .env.demo \
               -f docker-compose.demo.yml \
               up -d
# Le port 9201 est bindé sur 127.0.0.1, accessible uniquement via nginx
```

### 1.3 Vérifications post-démarrage

```bash
# Healthcheck
curl -fsS http://127.0.0.1:9201/actuator/health
# {"status":"UP"}

# Avec override public.yml : aussi accessible depuis l'extérieur
curl -fsS http://<ip-serveur>:9201/actuator/health   # depuis ton poste

# DB accessible
docker exec lst_demo_db psql -U appuser -d sample_tracker_demo \
  -c "SELECT version();"
```

---

## Phase 2 — Validation DEMO

> **Bloque ici jusqu'à ce que la demo soit jugée stable.** N'enchaîne PAS sur la prod sans cette validation.

### 2.1 Tests fonctionnels manuels

- [ ] Login avec un compte admin (créé via SQL initial ou seed)
- [ ] Navigation principale (dashboard, listes, formulaires)
- [ ] Création d'un échantillon → tracking → réception lab
- [ ] Génération de rapport (Jasper)
- [ ] Logout + refresh token cycle

### 2.2 Vérifications sécurité

```bash
# Postgres n'est PAS exposé publiquement (loopback only)
ss -tlnp | grep :5436
# 127.0.0.1:5436 attendu, pas 0.0.0.0

# JWT secret n'est pas un fallback hardcodé
docker exec lst_demo_app env | grep JWT_SECRET | wc -c
# > 130 attendu (128 hex chars + "JWT_SECRET=")
```

### 2.3 Période d'observation

Laisser tourner la DEMO **au moins 48-72h** avec usage réel par les futurs utilisateurs.

```bash
docker logs lst_demo_app --since 24h | grep -iE 'error|exception|fatal' | tail -30
docker stats --no-stream lst_demo_app   # mémoire ne doit pas grimper sans cesse
```

Si rien d'anormal → passer à la **Phase 3**.

---

## Phase 3 — Déploiement PROD

> **Prérequis :** Phase 2 validée. Idéalement nginx déjà en place (cf. [NGINX.md](NGINX.md), section migration apache2).

Le bundle déjà téléchargé contient aussi le compose prod. Pas besoin de re-télécharger.

### 3.1 Préparer `.env.prod`

```bash
cd /opt/lstracker/lstracker-deploy-2.2.0
cp .env.prod.example .env.prod

# Générer des secrets DIFFÉRENTS de la demo
echo "POSTGRES_PASSWORD=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)"
echo "JWT_SECRET=$(openssl rand -hex 64)"

nano .env.prod
# Remplacer CHANGE_ME_*. IMPORTANT :
#   - POSTGRES_DB différent de la demo (sample_tracker_prod vs _demo)
#   - POSTGRES_PASSWORD différent de la demo
#   - JWT_SECRET différent de la demo
# Note : POSTGRES_USER reste "appuser" partout (envs isolés par network
# Docker, pas par user). Dumps pg_dump portables entre envs.

grep CHANGE_ME .env.prod   # aucune sortie attendue
chmod 600 .env.prod
```

### 3.2 Démarrer PROD

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d

docker logs lst_prod_app -f --tail 100
# Attendre "Started LabSampleTrackerApplication"
```

### 3.3 Vérifier PROD

```bash
curl -fsS http://127.0.0.1:9200/actuator/health
ss -tlnp | grep :9200    # 127.0.0.1:9200 attendu

# Containers cohabitent ?
docker ps --filter "name=lst_"
# Doit montrer lst_demo_{app,db} ET lst_prod_{app,db}
```

### 3.4 Configurer nginx pour exposer la PROD

Voir **[NGINX.md](NGINX.md)** §Migration apache2 → nginx pour la migration sans interruption.

### 3.5 Importer les données de production (si migration depuis ancien serveur)

```bash
# Sur l'ancien serveur
pg_dump -h localhost -U appuser sample_tracker > /tmp/prod-dump-$(date +%Y%m%d).sql

# Transférer sur le nouveau
scp ancien-serveur:/tmp/prod-dump-*.sql /tmp/

# Restaurer dans le nouveau container
docker exec -i lst_prod_db psql -U appuser -d sample_tracker_prod \
  < /tmp/prod-dump-YYYYMMDD.sql

# Vérifier
docker exec lst_prod_db psql -U appuser -d sample_tracker_prod \
  -c "SELECT count(*) FROM sample_tracker.app_user;"
```

---

## Phase 4 — Validation PROD

### 4.1 Smoke test

- [ ] Login avec un compte réel
- [ ] Action de bout en bout (création + lecture + modification)
- [ ] Aucune erreur 500 dans `docker logs lst_prod_app --tail 100`

### 4.2 Backups automatisés

```bash
crontab -e
```

Ajouter :

```cron
# Backup DB PROD chaque jour à 03h00
0 3 * * * /opt/lstracker/lstracker-deploy-2.2.0/scripts/backup-db.sh prod >> /opt/lstracker/backup.log 2>&1
```

Voir [OPERATIONS.md](OPERATIONS.md) pour le détail.

### 4.3 Monitoring externe

Configurer un uptime monitor sur `https://lstracker.org/` (UptimeRobot, Healthchecks.io). Alerte si DOWN > 2 min.

---

## Mise à jour vers une nouvelle version

C'est là où le workflow bundle brille — pas besoin de modifier le compose, juste de **télécharger le nouveau bundle** et appliquer.

```bash
NEW_VERSION=2.3.0
URL=https://github.com/ITECH-CI/LSTracker_web/releases/download/v${NEW_VERSION}

cd /opt/lstracker
curl -fsSLO ${URL}/lstracker-deploy-${NEW_VERSION}.tar.gz
curl -fsSLO ${URL}/lstracker-deploy-${NEW_VERSION}.tar.gz.sha256
sha256sum -c lstracker-deploy-${NEW_VERSION}.tar.gz.sha256
tar -xzf lstracker-deploy-${NEW_VERSION}.tar.gz

# Backup PRÉ-upgrade obligatoire
cd lstracker-deploy-2.2.0   # ancienne version pour le script
./scripts/backup-db.sh prod

# Copier le .env existant dans le nouveau dossier
cp .env.demo .env.prod /opt/lstracker/lstracker-deploy-${NEW_VERSION}/

# Tester sur DEMO d'abord
cd /opt/lstracker/lstracker-deploy-${NEW_VERSION}
docker compose --env-file .env.demo -f docker-compose.demo.yml up -d
docker logs lst_demo_app -f --tail 50
# Tester pendant 24-72h

# Si OK, appliquer la PROD
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
docker logs lst_prod_app -f --tail 100
curl -fsS http://127.0.0.1:9200/actuator/health
```

Tu peux supprimer les anciens dossiers `lstracker-deploy-X.Y.Z` après quelques jours de stabilité.

---

## Rollback

### Rollback DEMO ou PROD vers version précédente

```bash
cd /opt/lstracker
ls -d lstracker-deploy-*    # voir les versions disponibles localement

# Backup d'urgence avant rollback
cd lstracker-deploy-2.3.0
./scripts/backup-db.sh prod

# Re-démarrer avec l'ancienne version (le .env est compatible si schéma DB inchangé)
cd /opt/lstracker/lstracker-deploy-2.2.0
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
docker logs lst_prod_app -f --tail 100
```

Si **schéma DB incompatible** (migration Liquibase qui a tourné) → restaurer le dump pré-upgrade :

```bash
./scripts/restore-db.sh prod /opt/lstracker/backups/prod/lstracker-prod-PRE-UPGRADE.sql.gz
```

---

## Checklist condensée

### Préparation (1 fois)

- [ ] Docker installé et fonctionnel
- [ ] Accès SSH au serveur

### À chaque déploiement initial

- [ ] Bundle téléchargé depuis la Release voulue
- [ ] Checksum SHA256 vérifié
- [ ] `.env.demo` rempli, **aucun CHANGE_ME**, `chmod 600`
- [ ] `docker compose up -d` réussi, 2 containers UP
- [ ] `/actuator/health` retourne UP
- [ ] Login + navigation fonctionnels

### Avant de passer en PROD

- [ ] DEMO observée 48-72h sans erreur
- [ ] `.env.prod` avec secrets **différents** de demo
- [ ] DB name + user **différents** de demo
- [ ] (Si migration) dump importé, count records OK
- [ ] nginx + HTTPS configurés (cf. NGINX.md)

### Post-PROD

- [ ] Backup cron quotidien actif
- [ ] Monitoring externe (uptime)
- [ ] URL communiquée aux users
