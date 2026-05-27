# LSTracker — Installation rapide

> Voici les ~5 commandes pour démarrer une instance (demo ou prod). Si tu veux le runbook complet avec validation, migration de données, rollback : [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Prérequis

- Linux x86_64 (Ubuntu 22.04+ recommandé)
- Docker 24+ et Docker Compose v2

```bash
docker --version
docker compose version
```

Si Docker n'est pas là :

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
```

Si Docker est déjà là mais que ton user n'est pas dans le groupe `docker`
(symptôme : `docker ps` demande `sudo`), ajoute-le maintenant — sinon les
scripts `scripts/*.sh` du bundle échoueront avec "permission denied while
trying to connect to the Docker daemon socket" :

```bash
sudo usermod -aG docker $USER
newgrp docker
docker ps   # doit marcher sans sudo
```

## Récupérer le bundle de déploiement

Toutes les releases sont sur https://github.com/ITECH-CI/LSTracker_web/releases. Choisir la version voulue et l'installer :

```bash
VERSION=2.2.0
URL=https://github.com/ITECH-CI/LSTracker_web/releases/download/v${VERSION}

# Dossier de déploiement (au choix, ex: /opt/lstracker)
sudo mkdir -p /opt/lstracker
sudo chown $USER:$USER /opt/lstracker
cd /opt/lstracker

# Télécharger bundle + checksum
curl -fsSLO ${URL}/lstracker-deploy-${VERSION}.tar.gz
curl -fsSLO ${URL}/lstracker-deploy-${VERSION}.tar.gz.sha256

# Vérifier l'intégrité
sha256sum -c lstracker-deploy-${VERSION}.tar.gz.sha256
# lstracker-deploy-2.2.0.tar.gz: OK

# Extraire et entrer dans le dossier
tar -xzf lstracker-deploy-${VERSION}.tar.gz
cd lstracker-deploy-${VERSION}

# Vérifier
cat VERSION
ls -la
```

Le dossier contient : composes, `.env.example`, configs nginx & postgres, scripts ops, docs.

## Démarrer la DEMO

```bash
# 1. Préparer .env.demo
cp .env.demo.example .env.demo

# Générer les secrets
echo "POSTGRES_PASSWORD=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)"
echo "JWT_SECRET=$(openssl rand -hex 64)"

# Éditer .env.demo et remplacer toutes les valeurs CHANGE_ME_*
nano .env.demo

# Vérifier qu'il n'y a plus aucun CHANGE_ME
grep CHANGE_ME .env.demo && echo "ENCORE DU CHANGE_ME" || echo "OK"

chmod 600 .env.demo

# 2. Démarrer (l'image est pull automatiquement depuis ghcr.io/itech-ci/)
#    Avec exposition publique temporaire sur :9201 (avant migration nginx)
docker compose --env-file .env.demo \
               -f docker-compose.demo.yml \
               -f docker-compose.demo.public.yml \
               up -d

# 3. Vérifier
docker ps --filter "name=lst_demo_"
docker logs lst_demo_app -f --tail 50
# Attendre "Started LabSampleTrackerApplication" puis Ctrl+C

curl -fsS http://127.0.0.1:9201/actuator/health
# {"status":"UP"}
```

L'app demo est ensuite accessible publiquement sur **http://<ip-serveur>:9201**.

> ⚠️ L'override `docker-compose.demo.public.yml` est pour la **phase de validation** uniquement. En production, utiliser nginx + HTTPS (cf. [docs/NGINX.md](docs/NGINX.md)) et lancer sans ce fichier.

## Démarrer la PROD

```bash
cp .env.prod.example .env.prod
nano .env.prod   # secrets DIFFÉRENTS de la demo
chmod 600 .env.prod

docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
docker logs lst_prod_app -f --tail 100
```

La prod est bindée sur `127.0.0.1:9200` — accessible uniquement via nginx (cf. [docs/NGINX.md](docs/NGINX.md)).

## Documentation complète

| Document | Contenu |
|---|---|
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Runbook complet (provisioning, validation, migration de données) |
| [docs/NGINX.md](docs/NGINX.md) | Reverse proxy nginx (vhosts, certs, migration apache2 → nginx) |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Backups, restauration, mises à jour, rotation secrets, troubleshooting |
| [scripts/](scripts/) | `backup-db.sh`, `restore-db.sh`, `rotate-secrets.sh`, `check-cert-expiry.sh` |
| [VERSION](VERSION) | Métadonnées du bundle (version, date build, image, commit) |

## Mettre à jour vers une nouvelle version

```bash
NEW_VERSION=2.3.0
URL=https://github.com/ITECH-CI/LSTracker_web/releases/download/v${NEW_VERSION}

cd /opt/lstracker
curl -fsSLO ${URL}/lstracker-deploy-${NEW_VERSION}.tar.gz
curl -fsSLO ${URL}/lstracker-deploy-${NEW_VERSION}.tar.gz.sha256
sha256sum -c lstracker-deploy-${NEW_VERSION}.tar.gz.sha256
tar -xzf lstracker-deploy-${NEW_VERSION}.tar.gz

# Reprendre les .env existants
cp lstracker-deploy-2.2.0/.env.{demo,prod} lstracker-deploy-${NEW_VERSION}/

# Appliquer (tester sur demo d'abord)
cd lstracker-deploy-${NEW_VERSION}
docker compose --env-file .env.demo -f docker-compose.demo.yml up -d
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

Détail dans [docs/OPERATIONS.md](docs/OPERATIONS.md) §"Mise à jour de version".
