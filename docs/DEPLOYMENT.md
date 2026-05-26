# Runbook de déploiement — LSTracker Web

> Procédure complète : déploiement initial DEMO → validation → déploiement PROD sur le même serveur (96 GiB RAM / 24 cores).

## Sommaire

1. [Vue d'ensemble](#vue-densemble)
2. [Provisioning du serveur](#provisioning-du-serveur)
3. [Phase 1 — Déploiement DEMO](#phase-1--déploiement-demo)
4. [Phase 2 — Validation DEMO](#phase-2--validation-demo)
5. [Phase 3 — Déploiement PROD](#phase-3--déploiement-prod)
6. [Phase 4 — Validation PROD](#phase-4--validation-prod)
7. [Rollback](#rollback)
8. [Checklist condensée](#checklist-condensée)

---

## Vue d'ensemble

```
Serveur Linux (96 GiB RAM, 24 cores)
├── DEMO   : ports 9201 (app) + 5436 (db, 127.0.0.1 only)
└── PROD   : ports 9200 (app) + 5435 (db, 127.0.0.1 only)

Networks Docker distincts → isolation totale demo/prod.
Bases de données séparées, secrets JWT séparés.
```

| Élément | DEMO | PROD |
|---|---|---|
| Compose file | `docker-compose.demo.yml` | `docker-compose.prod.yml` |
| Env file | `.env.demo` | `.env.prod` |
| App container | `lst_demo_app` | `lst_prod_app` |
| DB container | `lst_demo_db` | `lst_prod_db` |
| Network | `lst_demo_net` | `lst_prod_net` |
| Image Docker | `ghcr.io/itech-ci/labsampletracker:<tag>` | `ghcr.io/itech-ci/labsampletracker:<tag>` |
| Port app | 9201 | 9200 |
| Port DB (loopback) | 5436 | 5435 |

---

## Provisioning du serveur

À faire **une seule fois** avant le premier déploiement.

### 1. Système de base

```bash
# OS : Ubuntu 22.04 LTS (ou Debian 12)
# Connexion SSH avec un user non-root sudoer

sudo apt update && sudo apt upgrade -y
sudo apt install -y curl ca-certificates gnupg lsb-release ufw
```

### 2. Docker Engine + Compose plugin

```bash
# Méthode officielle Docker (cf. https://docs.docker.com/engine/install/ubuntu/)
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Ajouter le user au groupe docker (évite sudo à chaque commande)
sudo usermod -aG docker $USER
# Logout/login pour activer le groupe, ou :
newgrp docker

# Vérifier
docker --version            # Docker version 26.x+
docker compose version      # Docker Compose version v2.x+
```

### 3. Firewall

```bash
# Autoriser SSH + ports applicatifs publics seulement
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 9200/tcp   # PROD app
sudo ufw allow 9201/tcp   # DEMO app
# Postgres n'est PAS exposé publiquement (bind 127.0.0.1 dans compose).
sudo ufw enable
sudo ufw status
```

### 4. Login GitHub Container Registry

Pour pull les images privées depuis `ghcr.io`. **Même si le repo est public**, un login évite le rate-limit anonyme.

```bash
# Sur ton poste : créer un Personal Access Token (classic) avec scope read:packages
# https://github.com/settings/tokens/new?scopes=read:packages

# Sur le serveur :
echo "ghp_xxxxxxxx" | docker login ghcr.io -u <ton-github-username> --password-stdin
# Login Succeeded
```

### 5. Préparer l'arborescence

```bash
sudo mkdir -p /opt/lstracker
sudo chown $USER:$USER /opt/lstracker
cd /opt/lstracker

# Cloner le repo (les fichiers Docker / compose / scripts y sont)
git clone https://github.com/ITECH-CI/LSTracker_web.git .

# Vérifier
ls -la
# Tu dois voir : Dockerfile, docker-compose.{demo,prod}.yml, .env.{demo,prod}.example,
#                config/, scripts/, docs/
```

### 6. Configurer les dossiers de logs et backups

```bash
mkdir -p /opt/lstracker/logs/demo /opt/lstracker/logs/prod
mkdir -p /opt/lstracker/backups/{demo,prod}
```

---

## Phase 1 — Déploiement DEMO

### 1.1 Préparer `.env.demo`

```bash
cd /opt/lstracker
cp .env.demo.example .env.demo

# Générer les secrets propres (1 par 1)
echo "POSTGRES_PASSWORD=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)"
echo "JWT_SECRET=$(openssl rand -hex 64)"

# Éditer .env.demo et remplacer les CHANGE_ME_* par les valeurs générées
nano .env.demo
```

Vérifier qu'**aucune valeur ne contient encore `CHANGE_ME`** :

```bash
grep CHANGE_ME .env.demo
# Aucune sortie attendue → OK
```

Verrouiller les permissions :

```bash
chmod 600 .env.demo
ls -la .env.demo   # -rw------- attendu
```

### 1.2 Choisir le tag d'image

Lister les tags disponibles sur ghcr.io :

```bash
# Via gh CLI (depuis ton poste, pas le serveur)
gh api /orgs/ITECH-CI/packages/container/labsampletracker/versions \
  --jq '.[] | .metadata.container.tags[]' | sort -u
```

Ou directement sur https://github.com/orgs/ITECH-CI/packages.

Mettre le bon tag dans `.env.demo` :

```bash
sed -i 's|^APP_IMAGE=.*|APP_IMAGE=ghcr.io/itech-ci/labsampletracker:2.2.0|' .env.demo
grep APP_IMAGE .env.demo
```

### 1.3 Démarrer DEMO

```bash
cd /opt/lstracker

# Pull explicite (vérifie l'accès ghcr.io)
docker compose --env-file .env.demo -f docker-compose.demo.yml pull

# Démarrer (DB d'abord, puis app via depends_on healthy)
docker compose --env-file .env.demo -f docker-compose.demo.yml up -d

# Suivre le démarrage
docker logs lst_demo_db -f --tail 50
# Ctrl+C quand tu vois "database system is ready to accept connections"

docker logs lst_demo_app -f --tail 50
# Ctrl+C quand tu vois "Started LabSampleTrackerApplication"
```

### 1.4 Vérifications post-démarrage DEMO

```bash
# Healthcheck interne (loopback uniquement — l'app n'est PAS exposée publiquement
# directement, nginx prend le relais cf. étape 1.5)
curl -fsS http://127.0.0.1:9201/actuator/health
# {"status":"UP"}

# Le port n'est PAS exposé publiquement (Docker bind 127.0.0.1:9201)
ss -tlnp | grep :9201
# Doit afficher 127.0.0.1:9201, pas 0.0.0.0

# DB accessible (loopback only)
docker exec -it lst_demo_db psql -U $POSTGRES_USER -d $POSTGRES_DB -c "SELECT version();"

# Resource usage
docker stats --no-stream lst_demo_app lst_demo_db
```

### 1.5 Configurer nginx pour exposer la DEMO en HTTPS

> Voir **[NGINX.md](NGINX.md)** pour la procédure complète (vhosts, certs, migration apache2).

Résumé pour la DEMO :

```bash
# 1. DNS : créer A record lstracker-demo.itech-civ.org → IP serveur

# 2. Placer le cert wildcard *.itech-civ.org sur le serveur
sudo mkdir -p /etc/ssl/itech-civ
# (uploader wildcard.itech-civ.org.fullchain.pem et .key)

# 3. Déployer le vhost demo
sudo cp config/nginx/lstracker-demo.conf /etc/nginx/sites-available/
sudo ln -sf /etc/nginx/sites-available/lstracker-demo.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 4. Tester
curl -fsS https://lstracker-demo.itech-civ.org/actuator/health   # doit retourner 403 (bloqué public)
curl -fsS https://lstracker-demo.itech-civ.org/                  # doit charger la page login
```

---

## Phase 2 — Validation DEMO

> **Bloque ici jusqu'à ce que la demo soit jugée stable.** N'enchaîne PAS sur la prod sans cette validation.

### 2.1 Tests fonctionnels manuels

Sur le frontend `http://<ip-publique>:9201` :

- [ ] Login avec un compte admin créé via SQL ou seed
- [ ] Navigation principale (dashboard, listes, formulaires)
- [ ] Création d'un échantillon → tracking → réception lab
- [ ] Génération de rapport (Jasper)
- [ ] Logout + refresh token cycle

### 2.2 Tests de charge légers (optionnel mais recommandé)

```bash
# Installer ab si besoin
sudo apt install -y apache2-utils

# 500 requêtes, 20 concurrentes, sur /actuator/health
ab -n 500 -c 20 http://localhost:9201/actuator/health

# Vérifier latence p95, taux d'erreurs
# Si ça crashe ou si la latence explose → investiguer avant prod
```

### 2.3 Vérifications sécurité

```bash
# Postgres n'est PAS exposé publiquement
nmap -p 5436 <ip-publique-serveur>
# 5436/tcp filtered ou closed attendu (pas open)

# JWT secret n'est pas un fallback hardcodé
docker exec lst_demo_app env | grep JWT_SECRET | wc -c
# > 130 attendu (128 hex chars + "JWT_SECRET=")
```

### 2.4 Période d'observation

Laisser tourner la DEMO **au moins 48-72h** avec usage réel par les futurs utilisateurs.

Surveiller :

```bash
# Erreurs dans les logs
docker logs lst_demo_app --since 24h | grep -iE 'error|exception|fatal' | tail -30

# Stabilité mémoire (ne devrait pas grimper sans cesse)
docker stats --no-stream lst_demo_app
```

Si rien d'anormal après cette période → passer à la **Phase 3**.

---

## Phase 3 — Déploiement PROD

> **Prérequis :** Phase 2 validée.

### 3.1 Préparer `.env.prod`

```bash
cd /opt/lstracker
cp .env.prod.example .env.prod

# Générer des secrets DIFFÉRENTS de la demo
echo "POSTGRES_PASSWORD=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)"
echo "JWT_SECRET=$(openssl rand -hex 64)"

nano .env.prod
# Remplacer les CHANGE_ME_* + APP_IMAGE
# IMPORTANT : POSTGRES_DB doit être différent (sample_tracker_prod) de la demo (sample_tracker_demo)
# IMPORTANT : POSTGRES_USER doit être différent (appuser_prod) de la demo (appuser_demo)

grep CHANGE_ME .env.prod   # Aucune sortie attendue
chmod 600 .env.prod
```

### 3.2 Démarrer PROD

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d

# Suivre
docker logs lst_prod_db -f --tail 50
# Attendre "ready to accept connections" puis Ctrl+C

docker logs lst_prod_app -f --tail 100
# Attendre "Started LabSampleTrackerApplication"
```

### 3.3 Vérifier PROD

```bash
curl -fsS http://127.0.0.1:9200/actuator/health

# Le port n'est PAS exposé publiquement (Docker bind 127.0.0.1:9200)
ss -tlnp | grep :9200
# Doit afficher 127.0.0.1:9200, pas 0.0.0.0

# Vérifier qu'on est bien sur la DB PROD (pas demo)
docker exec lst_prod_db psql -U $POSTGRES_USER -d $POSTGRES_DB -c "SELECT current_database();"
# Doit retourner sample_tracker_prod (ou ton nom)

# Containers tournent côte à côte sans conflit ?
docker ps --filter "name=lst_"
# Doit montrer : lst_demo_app, lst_demo_db, lst_prod_app, lst_prod_db
```

### 3.4 Configurer nginx pour exposer la PROD en HTTPS

> Voir **[NGINX.md](NGINX.md)** pour le détail, en particulier la section [Migration apache2 → nginx](NGINX.md#migration-apache2--nginx) si apache2 servait déjà la prod.

```bash
# 1. Placer le cert lstracker.org sur le serveur (cf. NGINX.md §Prérequis)
sudo mkdir -p /etc/ssl/lstracker
# (uploader lstracker.org.fullchain.pem et .key, permissions 644/600)

# 2. Déployer le vhost prod
sudo cp config/nginx/lstracker-prod.conf /etc/nginx/sites-available/
# Adapter les chemins des certs dans /etc/nginx/sites-available/lstracker-prod.conf
sudo ln -sf /etc/nginx/sites-available/lstracker-prod.conf /etc/nginx/sites-enabled/

# 3. Test syntaxe + reload
sudo nginx -t && sudo systemctl reload nginx

# 4. Tester
curl -fsS https://lstracker.org/
curl -sI -k https://lstracker.org/actuator/health   # doit être 403 publiquement
```

### 3.5 Importer les données de production (si migration depuis ancien serveur)

Si tu migres depuis une ancienne installation :

```bash
# Sur l'ancien serveur : dump
pg_dump -h localhost -U appuser sample_tracker > /tmp/prod-dump-$(date +%Y%m%d).sql

# Transférer sur le nouveau serveur
scp ancien:/tmp/prod-dump-*.sql /opt/lstracker/backups/prod/

# Sur le nouveau serveur : restaurer
docker exec -i lst_prod_db psql -U appuser_prod -d sample_tracker_prod \
  < /opt/lstracker/backups/prod/prod-dump-YYYYMMDD.sql

# Vérifier
docker exec lst_prod_db psql -U appuser_prod -d sample_tracker_prod \
  -c "SELECT count(*) FROM sample_tracker.app_user;"
```

---

## Phase 4 — Validation PROD

### 4.1 Smoke test immédiat

- [ ] Login avec un compte réel
- [ ] Navigation principale OK
- [ ] Un compte peut effectuer une action de bout en bout
- [ ] Aucune erreur 500 dans les logs

```bash
docker logs lst_prod_app --tail 100 | grep -iE 'error|exception' | head -20
```

### 4.2 Backups automatisés DB

Configurer un cron quotidien :

```bash
crontab -e
```

Ajouter :

```
# Backup DB PROD chaque jour à 03h00, retention 30 jours
0 3 * * * /opt/lstracker/scripts/backup-db.sh prod >> /opt/lstracker/logs/backup.log 2>&1
```

(le script `backup-db.sh` est dans le repo — cf. `docs/OPERATIONS.md`)

### 4.3 Monitoring minimal

```bash
# Healthcheck externe via uptime monitor (UptimeRobot, Healthchecks.io)
# URL à monitorer : http://<ip-publique>:9200/actuator/health
# Alerte si != UP pendant 2 minutes
```

### 4.4 Communication aux utilisateurs

- [ ] Annoncer l'URL prod
- [ ] Donner les credentials aux admins
- [ ] Documenter l'URL demo séparée pour tests futurs

---

## Rollback

### Rollback DEMO

```bash
cd /opt/lstracker
docker compose --env-file .env.demo -f docker-compose.demo.yml down
# Garder le volume Postgres si rollback temporaire :
# docker volume ls | grep demo
# Re-up plus tard avec une image précédente :
sed -i 's|^APP_IMAGE=.*|APP_IMAGE=ghcr.io/itech-ci/labsampletracker:2.1.0|' .env.demo
docker compose --env-file .env.demo -f docker-compose.demo.yml up -d
```

### Rollback PROD vers version précédente

```bash
cd /opt/lstracker

# 1. Backup DB d'urgence
docker exec lst_prod_db pg_dump -U appuser_prod sample_tracker_prod \
  > /opt/lstracker/backups/prod/rollback-$(date +%Y%m%d-%H%M).sql

# 2. Repasser à la version précédente
sed -i 's|^APP_IMAGE=.*|APP_IMAGE=ghcr.io/itech-ci/labsampletracker:<version-precedente>|' .env.prod
docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d tracker_app

# 3. Si schéma DB incompatible : restaurer dump pré-upgrade
# docker exec -i lst_prod_db psql -U appuser_prod -d sample_tracker_prod < backup-pre-upgrade.sql
```

### Rollback rotation secrets

Le script `rotate-secrets.sh` rollback automatiquement en cas d'échec. Manuel :

```bash
ls -la .env.prod.backup.*
cp .env.prod.backup.YYYYMMDD-HHMMSS .env.prod

# Remettre l'ancien password en BD (récupérer l'ancien depuis la backup)
OLD_PWD=$(grep '^POSTGRES_PASSWORD=' .env.prod.backup.YYYYMMDD-HHMMSS | cut -d= -f2-)
CURRENT_PWD=$(grep '^POSTGRES_PASSWORD=' .env.prod | cut -d= -f2-)
# Wait — restaurer depuis le backup change déjà la valeur dans le fichier.
# Donc à ce stade .env.prod contient l'ancien et c'est ce qu'on veut.
# Reste à le remettre dans Postgres :
docker exec -e PGPASSWORD="$CURRENT_PWD" lst_prod_db \
  psql -U $POSTGRES_USER -d $POSTGRES_DB \
  -c "ALTER USER \"$POSTGRES_USER\" WITH PASSWORD '$OLD_PWD';"

docker compose --env-file .env.prod -f docker-compose.prod.yml up -d tracker_app
```

---

## Checklist condensée

### Provisioning (1 fois)

- [ ] Docker + Compose plugin installés
- [ ] Firewall configuré (ports 9200, 9201, SSH only)
- [ ] `docker login ghcr.io` réussi
- [ ] Repo cloné dans `/opt/lstracker`
- [ ] Dossiers `logs/`, `backups/` créés

### Phase 1 — DEMO

- [ ] `.env.demo` rempli, **aucun CHANGE_ME restant**
- [ ] `chmod 600 .env.demo`
- [ ] `APP_IMAGE` pointe vers un tag existant sur ghcr.io
- [ ] `docker compose ... up -d` réussi
- [ ] Logs DB et app sans erreur
- [ ] `/actuator/health` retourne UP

### Phase 2 — Validation DEMO (48-72h min)

- [ ] Login + navigation principale OK
- [ ] Création/lecture/modification d'échantillon
- [ ] Rapports Jasper OK
- [ ] Postgres pas exposé publiquement (nmap)
- [ ] Pas d'erreurs récurrentes dans `docker logs`
- [ ] Mémoire stable (pas de leak)

### Phase 3 — PROD

- [ ] `.env.prod` rempli avec secrets **différents** de demo
- [ ] DB name + user **différents** de demo
- [ ] `chmod 600 .env.prod`
- [ ] `docker compose ... up -d` réussi
- [ ] `/actuator/health` UP
- [ ] (Si migration) dump importé, count records OK

### Phase 4 — Post-PROD

- [ ] Smoke test fonctionnel
- [ ] Backup cron quotidien actif
- [ ] Monitoring externe configuré
- [ ] URL communiquée aux users
