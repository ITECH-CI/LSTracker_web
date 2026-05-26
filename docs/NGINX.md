# Reverse proxy nginx — LSTracker

> Configuration nginx sur l'host (pas dans Docker) pour exposer en HTTPS la prod (`lstracker.org`) et la demo (`lstracker-demo.itech-civ.org`). Inclut la procédure de migration depuis apache2.

## Sommaire

1. [Architecture](#architecture)
2. [Prérequis DNS et certificats](#prérequis-dns-et-certificats)
3. [Migration apache2 → nginx](#migration-apache2--nginx)
4. [Installation nginx from scratch](#installation-nginx-from-scratch)
5. [Déploiement des vhosts](#déploiement-des-vhosts)
6. [Tests et validation](#tests-et-validation)
7. [Renouvellement des certificats](#renouvellement-des-certificats)
8. [Troubleshooting](#troubleshooting)

---

## Architecture

```
                          Internet
                              │
                       ┌──────▼──────┐
                       │   nginx     │  (sur l'host Ubuntu)
                       │  :80, :443  │
                       └──┬───────┬──┘
                          │       │
        ┌─────────────────┘       └──────────────────┐
        │                                            │
  Host: lstracker.org                  Host: lstracker-demo.itech-civ.org
   proxy_pass:                          proxy_pass:
   127.0.0.1:9200                       127.0.0.1:9201
        │                                            │
   ┌────▼──────┐                              ┌──────▼─────┐
   │ Docker    │                              │ Docker     │
   │ lst_prod_ │                              │ lst_demo_  │
   │  app      │                              │  app       │
   │  :9200    │                              │  :9201     │
   └───────────┘                              └────────────┘
```

**Points clés :**

- nginx tourne **sur l'host** (apt install), pas dans Docker
- Les containers Docker bindent leurs ports sur `127.0.0.1` uniquement → inaccessibles depuis l'extérieur sans passer par nginx
- Le firewall ouvre seulement `:80` et `:443` (plus de `:9200`/`:9201` publics)
- nginx termine TLS, proxy le reste en HTTP local vers les containers

---

## Prérequis DNS et certificats

### DNS

Avant de toucher au serveur, vérifier que les enregistrements DNS pointent vers l'IP du serveur :

```bash
dig +short lstracker.org A
dig +short www.lstracker.org A
dig +short lstracker-demo.itech-civ.org A
# Tous doivent retourner l'IP de ton serveur
```

Si `lstracker-demo.itech-civ.org` n'existe pas encore : créer un enregistrement A chez le registrar/DNS d'ITECH-CI :

```
Type: A
Name: lstracker-demo
Value: <IP du serveur>
TTL : 3600
```

### Certificats SSL

Tu as deux jeux de certs à placer sur le serveur :

| Cert | Couvre | Chemins suggérés sur le serveur |
|---|---|---|
| **lstracker.org** | `lstracker.org` + `www.lstracker.org` | `/etc/ssl/lstracker/lstracker.org.fullchain.pem` + `.key` |
| **wildcard itech-civ** | `*.itech-civ.org` | `/etc/ssl/itech-civ/wildcard.itech-civ.org.fullchain.pem` + `.key` |

Chaque cert a besoin de **2 fichiers** :

- **`*.fullchain.pem`** : ton certificat **+** la chaîne intermédiaire du CA, concaténés
- **`*.key`** : la clé privée

Permissions à appliquer **impérativement** :

```bash
sudo chown -R root:root /etc/ssl/lstracker /etc/ssl/itech-civ
sudo chmod 644 /etc/ssl/lstracker/*.fullchain.pem
sudo chmod 600 /etc/ssl/lstracker/*.key
sudo chmod 644 /etc/ssl/itech-civ/*.fullchain.pem
sudo chmod 600 /etc/ssl/itech-civ/*.key
```

> Si tu as un cert "cert.crt" + "intermediate.crt" séparés, construire le fullchain :
> ```bash
> cat cert.crt intermediate.crt > lstracker.org.fullchain.pem
> ```

Vérifier la validité :

```bash
openssl x509 -noout -dates -subject -in /etc/ssl/lstracker/lstracker.org.fullchain.pem
openssl x509 -noout -dates -subject -in /etc/ssl/itech-civ/wildcard.itech-civ.org.fullchain.pem
```

---

## Migration apache2 → nginx

À faire si apache2 sert déjà LSTracker en production. Procédure pour **basculer sans interruption longue** (downtime ~10 secondes).

### 1. Préparer nginx (sans le démarrer)

```bash
# Installer nginx mais ne pas l'activer pour l'instant
sudo apt update
sudo apt install -y nginx

# Empêcher nginx de démarrer automatiquement maintenant
sudo systemctl stop nginx
sudo systemctl disable nginx
```

À ce stade, apache2 continue de servir normalement.

### 2. Déployer les vhosts nginx

```bash
cd /opt/lstracker
sudo cp config/nginx/lstracker-prod.conf /etc/nginx/sites-available/
sudo cp config/nginx/lstracker-demo.conf /etc/nginx/sites-available/

# Activer
sudo ln -sf /etc/nginx/sites-available/lstracker-prod.conf /etc/nginx/sites-enabled/
sudo ln -sf /etc/nginx/sites-available/lstracker-demo.conf /etc/nginx/sites-enabled/

# Désactiver le vhost par défaut nginx
sudo rm -f /etc/nginx/sites-enabled/default

# Adapter les chemins des certs (PLACEHOLDERS dans les fichiers .conf)
sudo nano /etc/nginx/sites-available/lstracker-prod.conf
sudo nano /etc/nginx/sites-available/lstracker-demo.conf
```

Tester la syntaxe **sans démarrer** :

```bash
sudo nginx -t
# nginx: configuration file /etc/nginx/nginx.conf test is successful
```

Si erreur : la corriger avant de continuer.

### 3. Modifier les bind ports Docker

Les compose ont déjà été mis à jour pour binder sur `127.0.0.1`. Appliquer :

```bash
cd /opt/lstracker

# Pour la prod
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --force-recreate tracker_app

# Vérifier que le port n'est plus exposé publiquement
ss -tlnp | grep -E ':9200|:9201'
# Doit afficher 127.0.0.1:9200 et 127.0.0.1:9201, pas 0.0.0.0
```

### 4. Bascule effective (downtime ~10s)

```bash
# Arrêter apache2
sudo systemctl stop apache2
sudo systemctl disable apache2

# Démarrer nginx
sudo systemctl start nginx
sudo systemctl enable nginx

# Vérifier
sudo systemctl status nginx
curl -fsS -k https://lstracker.org/actuator/health    # (ignore TLS via -k pour test rapide)
curl -fsS https://lstracker.org/                       # page de login
```

### 5. Mettre à jour le firewall

```bash
sudo ufw allow 'Nginx Full'    # ouvre 80 + 443
sudo ufw delete allow 9200/tcp # plus besoin (Docker bind sur 127.0.0.1)
sudo ufw delete allow 9201/tcp
sudo ufw status
```

### 6. Désinstaller apache2 (après quelques jours stable)

Attendre **3-7 jours** sans incident avant de désinstaller. En attendant :

```bash
# Vérifier qu'apache2 reste bien stoppé
sudo systemctl is-enabled apache2   # disabled attendu
sudo systemctl is-active apache2    # inactive attendu
```

Quand confiance OK :

```bash
sudo apt purge -y apache2 apache2-utils apache2-bin apache2-data
sudo apt autoremove -y
sudo rm -rf /etc/apache2
```

---

## Installation nginx from scratch

Si pas d'apache2 préexistant (nouveau serveur) :

```bash
sudo apt update
sudo apt install -y nginx

# nginx démarre tout seul à l'install ; activer au boot
sudo systemctl enable nginx

# Vérifier
sudo systemctl status nginx
curl -fsS http://localhost/
# Welcome to nginx page

# Désactiver le vhost par défaut
sudo rm -f /etc/nginx/sites-enabled/default
sudo systemctl reload nginx

# Ouvrir le firewall
sudo ufw allow 'Nginx Full'
```

Puis suivre la [section Déploiement des vhosts](#déploiement-des-vhosts).

---

## Déploiement des vhosts

### 1. Copier les fichiers de conf

```bash
cd /opt/lstracker
sudo cp config/nginx/lstracker-prod.conf /etc/nginx/sites-available/
sudo cp config/nginx/lstracker-demo.conf /etc/nginx/sites-available/
```

### 2. Remplacer les placeholders

Éditer chaque vhost et remplacer les chemins de certs :

```bash
sudo sed -i \
  -e 's|/etc/ssl/lstracker/lstracker.org.fullchain.pem|<chemin-reel-cert-prod>|' \
  -e 's|/etc/ssl/lstracker/lstracker.org.key|<chemin-reel-key-prod>|' \
  /etc/nginx/sites-available/lstracker-prod.conf

sudo sed -i \
  -e 's|/etc/ssl/itech-civ/wildcard.itech-civ.org.fullchain.pem|<chemin-reel-cert-demo>|' \
  -e 's|/etc/ssl/itech-civ/wildcard.itech-civ.org.key|<chemin-reel-key-demo>|' \
  /etc/nginx/sites-available/lstracker-demo.conf
```

Ou éditer manuellement avec `sudo nano`.

### 3. Activer les vhosts

```bash
sudo ln -sf /etc/nginx/sites-available/lstracker-prod.conf /etc/nginx/sites-enabled/
sudo ln -sf /etc/nginx/sites-available/lstracker-demo.conf /etc/nginx/sites-enabled/

sudo nginx -t
sudo systemctl reload nginx
```

---

## Tests et validation

### 1. Reachability

```bash
# Depuis le serveur (test bypass DNS)
curl -fsS -H "Host: lstracker.org" https://127.0.0.1/ -k
curl -fsS -H "Host: lstracker-demo.itech-civ.org" https://127.0.0.1/ -k

# Depuis l'extérieur (DNS doit résoudre)
curl -fsS https://lstracker.org/
curl -fsS https://lstracker-demo.itech-civ.org/
```

### 2. Validation TLS

```bash
# Check de chaîne complète
openssl s_client -connect lstracker.org:443 -servername lstracker.org < /dev/null 2>/dev/null | openssl x509 -noout -subject -dates

# Idem demo
openssl s_client -connect lstracker-demo.itech-civ.org:443 -servername lstracker-demo.itech-civ.org < /dev/null 2>/dev/null | openssl x509 -noout -subject -dates
```

Test plus complet avec SSL Labs : https://www.ssllabs.com/ssltest/ — cible note **A** ou **A+**.

### 3. Redirections

```bash
# HTTP → HTTPS
curl -sI http://lstracker.org/ | grep -i location
# Location: https://lstracker.org/

# www → apex
curl -sI -H "Host: www.lstracker.org" https://lstracker.org/ -k | grep -i location
# Location: https://lstracker.org/
```

### 4. Actuator bloqué publiquement

```bash
# Depuis l'extérieur : doit échouer
curl -fsS https://lstracker.org/actuator/health
# 403 Forbidden attendu

# Depuis l'host : doit marcher
curl -fsS http://127.0.0.1:9200/actuator/health
# {"status":"UP"}
```

### 5. Logs nginx

```bash
sudo tail -f /var/log/nginx/lstracker-prod.access.log
sudo tail -f /var/log/nginx/lstracker-demo.access.log
sudo tail -f /var/log/nginx/lstracker-prod.error.log
```

---

## Renouvellement des certificats

Comme tu utilises des certs **non-Let's-Encrypt** (méthode manuelle / autre CA), le renouvellement est à faire à la main avant expiration.

### Surveiller l'expiration

```bash
# Sur le serveur
for cert in /etc/ssl/lstracker/*.fullchain.pem /etc/ssl/itech-civ/*.fullchain.pem; do
  echo "=== $cert ==="
  openssl x509 -noout -dates -subject -in "$cert"
done
```

Mettre en place une alerte (cron + mail) :

```bash
sudo crontab -e
```

Ajouter :

```cron
# Vérifier expiration cert tous les jours à 06h00, alerte si < 30 jours
0 6 * * * /opt/lstracker/scripts/check-cert-expiry.sh
```

(le script est fourni dans `scripts/`).

### Procédure de renouvellement

1. Obtenir le nouveau cert (selon ta méthode CA actuelle)
2. Le placer en remplacement :
   ```bash
   sudo cp nouveau.fullchain.pem /etc/ssl/lstracker/lstracker.org.fullchain.pem
   sudo cp nouveau.key /etc/ssl/lstracker/lstracker.org.key
   sudo chmod 644 /etc/ssl/lstracker/*.fullchain.pem
   sudo chmod 600 /etc/ssl/lstracker/*.key
   ```
3. Recharger nginx (pas besoin de restart) :
   ```bash
   sudo nginx -t && sudo systemctl reload nginx
   ```
4. Vérifier que la nouvelle date d'expiration apparaît :
   ```bash
   echo | openssl s_client -connect lstracker.org:443 -servername lstracker.org 2>/dev/null | openssl x509 -noout -dates
   ```

---

## Troubleshooting

### `nginx -t` échoue avec "no such file" sur les certs

Les chemins dans la conf pointent vers des fichiers absents. Vérifier :

```bash
ls -la /etc/ssl/lstracker/ /etc/ssl/itech-civ/
```

Et adapter les chemins dans `/etc/nginx/sites-available/lstracker-*.conf`.

### `502 Bad Gateway`

Le container Docker derrière n'est pas joignable.

```bash
# Le container tourne ?
docker ps --filter "name=lst_"

# Le port est-il en écoute sur 127.0.0.1 ?
ss -tlnp | grep -E ':9200|:9201'
# Doit montrer 127.0.0.1:9200 et 127.0.0.1:9201

# Test direct
curl -fsS http://127.0.0.1:9200/actuator/health
```

Si le container est UP mais le curl échoue : healthcheck app encore en démarrage (60-90s) ou crash.

### `SSL: error:0A000086:SSL routines::certificate verify failed`

La chaîne intermédiaire n'est pas concaténée au cert. Refaire le fullchain :

```bash
cat cert.crt intermediate.crt > /etc/ssl/lstracker/lstracker.org.fullchain.pem
```

Vérifier la chaîne :

```bash
openssl crl2pkcs7 -nocrl -certfile /etc/ssl/lstracker/lstracker.org.fullchain.pem \
  | openssl pkcs7 -print_certs -noout | grep -E 'subject|issuer'
```

Doit montrer au moins 2 certificats (le tien + l'intermédiaire).

### Cookies de session pas envoyés / login impossible

Spring Boot doit savoir qu'il est derrière un proxy HTTPS. Vérifier dans `application.properties` ou les vars d'env :

```
server.forward-headers-strategy=NATIVE
```

Si manquant, ajouter dans le docker-compose :

```yaml
environment:
  SERVER_FORWARD_HEADERS_STRATEGY: NATIVE
```

Et `up -d` pour recréer le container.

### Mixed content warnings dans le navigateur

L'app génère des URLs en `http://` au lieu de `https://`. Les headers `X-Forwarded-Proto` sont bien envoyés par nginx (cf. conf), donc le problème est probablement `server.forward-headers-strategy` non configuré côté Spring (cf. ci-dessus).
