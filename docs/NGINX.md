# Reverse proxy nginx — LSTracker

> nginx tourne **sur l'host** (pas Docker). Il expose la prod et la demo via les vhosts fournis dans le bundle (`config/nginx/`). Inclut la procédure de migration depuis apache2 sans interruption longue.

## Architecture TLS

```
   ┌────────────────────────────────────┐    ┌────────────────────────┐
   │  Client (lstracker.org)            │    │ Client (demo.itech-civ)│
   └────────┬───────────────────────────┘    └────────────┬───────────┘
            │ HTTPS                                       │ HTTPS direct
            ▼                                             │
   ┌──────────────────┐                                   │
   │ CDN/proxy externe│  (TLS terminé par le fournisseur) │
   │ (fournisseur DNS)│                                   │
   └────────┬─────────┘                                   │
            │ HTTP (origin)                               │
            │ X-Forwarded-Proto: https                    │
            └──────────────────┬──────────────────────────┘
                               │
                       ┌───────▼──────┐
                       │   nginx      │  (sur l'host Ubuntu)
                       │  :80, :443   │  TLS terminé ici pour DEMO
                       └──┬────────┬──┘
                          │        │
                          │ HTTP   │ HTTP
                          ▼        ▼
                   127.0.0.1:9200  127.0.0.1:9201
                   (lst_prod_app)  (lst_demo_app)
```

**Points clés :**

- **PROD** (lstracker.org) : TLS géré par le CDN du fournisseur de domaine. nginx reçoit du HTTP sur :80. Le header `X-Forwarded-Proto: https` envoyé par le CDN est propagé tel quel à Spring → URLs générées en `https://`.
- **DEMO** (lstracker-demo.itech-civ.org) : TLS terminé localement par nginx avec le cert wildcard `*.itech-civ.org`.
- Les containers Docker bindent `127.0.0.1` → inaccessibles sans nginx (sauf override `docker-compose.demo.public.yml` pour la phase de validation).

---

## Prérequis

### DNS

```bash
dig +short lstracker.org A
dig +short www.lstracker.org A
dig +short lstracker-demo.itech-civ.org A
# Tous doivent retourner l'IP du serveur
```

### Cert wildcard *.itech-civ.org

Le cert wildcard est dans `/home/itech/ssl/itech-civ.org/`. Vérifier les noms exacts :

```bash
ls -la /home/itech/ssl/itech-civ.org/
```

Selon le CA, les noms et formats varient. **nginx exige 2 fichiers** :

1. **`ssl_certificate`** : ton certificat **+** la chaîne intermédiaire concaténés (le fameux "fullchain")
2. **`ssl_certificate_key`** : la clé privée

Cas courants reconnus par convention :

| Tu as reçu | Format | Action |
|---|---|---|
| `fullchain.pem` + `privkey.pem` (Let's Encrypt) | PEM | ✅ Rien à faire |
| `<domain>.crt` + `<domain>.ca-bundle` + `<domain>.key` (Sectigo, GoDaddy, etc.) | PEM avec extensions différentes | Construire le fullchain (voir ci-dessous) |
| `cert.pem` + `intermediate.pem` + `key.pem` | PEM séparés | Construire le fullchain |
| `<domain>.pfx` ou `<domain>.p12` | PKCS#12 (un seul fichier chiffré) | Extraire (voir ci-dessous) |

**Important** : `.crt`, `.cer`, `.pem` désignent souvent le **même format** (PEM, en base64 avec entêtes `-----BEGIN CERTIFICATE-----`). Vérifier avec :

```bash
file /home/itech/ssl/itech-civ.org/*
head -1 /home/itech/ssl/itech-civ.org/<fichier>
```

Si tu vois `-----BEGIN CERTIFICATE-----` ou `-----BEGIN PRIVATE KEY-----` → c'est du PEM, peu importe l'extension.

#### Cas typique ITECH-CI : `.crt` + `.ca-bundle` + `.key`

Construire le fullchain :

```bash
# Ordre IMPORTANT : ton cert d'abord, puis la chaîne intermédiaire
cat /home/itech/ssl/itech-civ.org/itech-civ.org.crt \
    /home/itech/ssl/itech-civ.org/itech-civ.org.ca-bundle \
  | sudo tee /home/itech/ssl/itech-civ.org/fullchain.pem > /dev/null

# La clé privée : juste copier/renommer (pas de transformation)
sudo cp /home/itech/ssl/itech-civ.org/itech-civ.org.key \
        /home/itech/ssl/itech-civ.org/privkey.pem
```

Tu peux aussi pointer le vhost directement sur `.crt` et `.key` sans rien créer — il suffit d'ajuster les deux directives dans le vhost. Mais il faut **inclure le `.ca-bundle`** dans le `.crt` (cf. plus loin §"Concaténer dans le `.crt` directement").

#### Cas Let's Encrypt / déjà au bon format

```bash
# Rien à construire — fullchain.pem et privkey.pem sont déjà prêts
ls /home/itech/ssl/itech-civ.org/fullchain.pem
ls /home/itech/ssl/itech-civ.org/privkey.pem
```

#### Cas `.pfx` ou `.p12` (PKCS#12)

```bash
# Extraire le cert (sans la clé privée)
sudo openssl pkcs12 -in /home/itech/ssl/itech-civ.org/itech-civ.org.pfx \
  -clcerts -nokeys -out /home/itech/ssl/itech-civ.org/cert.pem

# Extraire la chaîne intermédiaire
sudo openssl pkcs12 -in /home/itech/ssl/itech-civ.org/itech-civ.org.pfx \
  -cacerts -nokeys -chain -out /home/itech/ssl/itech-civ.org/chain.pem

# Extraire la clé privée (mot de passe demandé)
sudo openssl pkcs12 -in /home/itech/ssl/itech-civ.org/itech-civ.org.pfx \
  -nocerts -nodes -out /home/itech/ssl/itech-civ.org/privkey.pem

# Construire le fullchain
sudo cat /home/itech/ssl/itech-civ.org/cert.pem \
         /home/itech/ssl/itech-civ.org/chain.pem \
  > /home/itech/ssl/itech-civ.org/fullchain.pem
```

#### Alternative : concaténer dans le `.crt` directement

Si tu préfères ne pas créer de fichier `fullchain.pem` et garder les noms d'origine, tu peux concaténer le `.ca-bundle` dans le `.crt` :

```bash
# ⚠️ Backup d'abord
sudo cp /home/itech/ssl/itech-civ.org/itech-civ.org.crt \
        /home/itech/ssl/itech-civ.org/itech-civ.org.crt.bak

# Ajouter le bundle à la fin du .crt
sudo bash -c 'cat /home/itech/ssl/itech-civ.org/itech-civ.org.ca-bundle \
              >> /home/itech/ssl/itech-civ.org/itech-civ.org.crt'
```

Puis pointer le vhost vers le `.crt` et le `.key` directement (cf. §"Adapter les chemins dans le vhost").

### Permissions

```bash
# Cert (lecture par tous OK, ne contient pas de secret)
sudo chmod 644 /home/itech/ssl/itech-civ.org/fullchain.pem
# (ou itech-civ.org.crt si tu n'as pas créé fullchain.pem)

# Clé privée (lecture root uniquement)
sudo chmod 600 /home/itech/ssl/itech-civ.org/privkey.pem
# (ou itech-civ.org.key)

# Owner root
sudo chown root:root /home/itech/ssl/itech-civ.org/*
```

### Vérifier validité

```bash
# Date d'expiration + sujet
openssl x509 -noout -dates -subject -in /home/itech/ssl/itech-civ.org/fullchain.pem
# notBefore=... notAfter=...
# subject=CN = *.itech-civ.org

# Vérifier que la chaîne est complète (≥ 2 certs : le tien + au moins 1 intermédiaire)
openssl crl2pkcs7 -nocrl -certfile /home/itech/ssl/itech-civ.org/fullchain.pem \
  | openssl pkcs7 -print_certs -noout | grep -c '^subject='
# Doit retourner 2 (ou plus). Si "1" → la chaîne intermédiaire n'est pas incluse.

# Vérifier que la clé privée matche bien le cert (les 2 hashes doivent être identiques)
openssl x509 -noout -modulus -in /home/itech/ssl/itech-civ.org/fullchain.pem | openssl md5
openssl rsa  -noout -modulus -in /home/itech/ssl/itech-civ.org/privkey.pem  | openssl md5
```

### Adapter les chemins dans le vhost

Si tes fichiers ne s'appellent **pas** `fullchain.pem` / `privkey.pem`, éditer `/etc/nginx/sites-available/lstracker-demo.conf` :

```nginx
# Exemple avec les noms d'origine .crt + .ca-bundle dans le .crt :
ssl_certificate     /home/itech/ssl/itech-civ.org/itech-civ.org.crt;
ssl_certificate_key /home/itech/ssl/itech-civ.org/itech-civ.org.key;
```

Puis :

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### Cert lstracker.org

**Rien à installer sur le serveur** — le TLS est géré par le CDN du fournisseur. Vérifier juste côté provider que :
- L'origin (= ce serveur) est atteignable en HTTP sur le port 80
- Le CDN envoie `X-Forwarded-Proto: https` (comportement par défaut)
- Le CDN forward le header `Host: lstracker.org`

---

## Migration apache2 → nginx

Pour basculer **sans interruption longue** (~10 secondes de downtime). À faire **après** validation de la demo sur port 9201.

### 1. Installer nginx sans le démarrer

```bash
sudo apt update
sudo apt install -y nginx

# Empêcher nginx de prendre les ports avant qu'on soit prêt
sudo systemctl stop nginx
sudo systemctl disable nginx
```

apache2 continue de servir normalement.

### 2. Déployer les vhosts depuis le bundle

Le bundle extrait contient `config/nginx/lstracker-{prod,demo}.conf`.

```bash
cd /opt/lstracker/lstracker-deploy-2.2.0

# Désactiver le vhost par défaut nginx
sudo rm -f /etc/nginx/sites-enabled/default

# Copier les vhosts
sudo cp config/nginx/lstracker-prod.conf /etc/nginx/sites-available/
sudo cp config/nginx/lstracker-demo.conf /etc/nginx/sites-available/

# Vérifier les chemins du cert dans le vhost demo
ls -la /home/itech/ssl/itech-civ.org/
sudo nano /etc/nginx/sites-available/lstracker-demo.conf
# Ajuster ssl_certificate / ssl_certificate_key si noms différents

# Activer
sudo ln -sf /etc/nginx/sites-available/lstracker-prod.conf /etc/nginx/sites-enabled/
sudo ln -sf /etc/nginx/sites-available/lstracker-demo.conf /etc/nginx/sites-enabled/

# Tester la syntaxe SANS démarrer (apache2 tourne toujours)
sudo nginx -t
# nginx: configuration file test is successful
```

Si erreur → corriger avant de continuer.

### 3. Forcer le bind 127.0.0.1 sur les composes

Si la demo tourne actuellement avec l'override `docker-compose.demo.public.yml` (port 9201 publiquement exposé), il faut le retirer pour que nginx prenne le relais :

```bash
cd /opt/lstracker/lstracker-deploy-2.2.0

# Demo : retirer l'override public
docker compose --env-file .env.demo \
               -f docker-compose.demo.yml \
               up -d --force-recreate tracker_app

# Vérifier que le port est maintenant en loopback
ss -tlnp | grep -E ':9200|:9201'
# Doit montrer 127.0.0.1:9200 et 127.0.0.1:9201 (pas 0.0.0.0)
```

### 4. Bascule effective (downtime ~10s)

```bash
# Stopper apache2
sudo systemctl stop apache2
sudo systemctl disable apache2

# Démarrer nginx
sudo systemctl start nginx
sudo systemctl enable nginx

# Vérifier
sudo systemctl status nginx
```

### 5. Tester immédiatement

```bash
# PROD via CDN (depuis l'extérieur)
curl -fsS https://lstracker.org/
curl -fsSI https://lstracker.org/ | grep -i 'HTTP\|Location'

# DEMO direct HTTPS
curl -fsS https://lstracker-demo.itech-civ.org/
```

### 6. Mettre à jour le firewall

```bash
sudo ufw allow 'Nginx Full'        # 80 + 443
sudo ufw delete allow 9201/tcp     # plus besoin
sudo ufw status
```

### 7. Désinstaller apache2 (après 3-7 jours stable)

```bash
sudo apt purge -y apache2 apache2-utils apache2-bin apache2-data
sudo apt autoremove -y
sudo rm -rf /etc/apache2
```

---

## Installation nginx from scratch (serveur vide)

```bash
sudo apt update
sudo apt install -y nginx
sudo rm -f /etc/nginx/sites-enabled/default

# Déployer les vhosts (cf. section 2 ci-dessus)
sudo cp /opt/lstracker/lstracker-deploy-2.2.0/config/nginx/lstracker-*.conf \
        /etc/nginx/sites-available/
sudo ln -sf /etc/nginx/sites-available/lstracker-prod.conf /etc/nginx/sites-enabled/
sudo ln -sf /etc/nginx/sites-available/lstracker-demo.conf /etc/nginx/sites-enabled/

# Ajuster les chemins du cert demo si besoin
sudo nano /etc/nginx/sites-available/lstracker-demo.conf

sudo nginx -t
sudo systemctl enable --now nginx

sudo ufw allow 'Nginx Full'
```

---

## Tests et validation

### Reachability

```bash
# --- PROD : HTTP (TLS terminé par le CDN) ---
# Depuis le serveur
curl -fsS -H "Host: lstracker.org" http://127.0.0.1/
curl -fsS -H "Host: lstracker.org" -H "X-Forwarded-Proto: https" http://127.0.0.1/

# Depuis l'extérieur (via CDN)
curl -fsS https://lstracker.org/

# --- DEMO : HTTPS direct ---
curl -fsS -H "Host: lstracker-demo.itech-civ.org" https://127.0.0.1/ -k
curl -fsS https://lstracker-demo.itech-civ.org/

# HTTP demo redirige bien en HTTPS
curl -sI http://lstracker-demo.itech-civ.org/ | grep -i location
# Location: https://...
```

### Actuator bloqué publiquement

```bash
# Doit échouer (403)
curl -fsS https://lstracker.org/actuator/health
curl -fsS https://lstracker-demo.itech-civ.org/actuator/health

# Mais accessible depuis l'host
curl -fsS http://127.0.0.1:9200/actuator/health
curl -fsS http://127.0.0.1:9201/actuator/health
```

### TLS demo (validation cert)

```bash
echo | openssl s_client -connect lstracker-demo.itech-civ.org:443 \
   -servername lstracker-demo.itech-civ.org 2>/dev/null \
   | openssl x509 -noout -subject -dates
```

Test complet via SSL Labs : https://www.ssllabs.com/ssltest/analyze.html?d=lstracker-demo.itech-civ.org — cible note **A** ou **A+**.

### Logs nginx

```bash
sudo tail -f /var/log/nginx/lstracker-prod.access.log
sudo tail -f /var/log/nginx/lstracker-demo.access.log
sudo tail -f /var/log/nginx/lstracker-prod.error.log
```

---

## Renouvellement cert wildcard

Seul le cert wildcard `*.itech-civ.org` est à gérer localement. Le cert de `lstracker.org` est renouvelé par le fournisseur de domaine.

### Surveillance auto

```bash
sudo crontab -e
```

```cron
# Alerte si cert wildcard expire < 30 jours
0 6 * * * /opt/lstracker/lstracker-deploy-2.2.0/scripts/check-cert-expiry.sh
```

Configurer l'alerte mail via `ALERT_EMAIL=ops@itech-ci.org` en haut du script (ou en env du cron).

### Procédure de renouvellement

```bash
# 1. Obtenir le nouveau cert wildcard (selon process ITECH-CI)

# 2. Remplacer aux chemins déjà configurés
sudo cp nouveau.fullchain.pem /home/itech/ssl/itech-civ.org/fullchain.pem
sudo cp nouveau.key           /home/itech/ssl/itech-civ.org/privkey.pem
sudo chmod 644 /home/itech/ssl/itech-civ.org/fullchain.pem
sudo chmod 600 /home/itech/ssl/itech-civ.org/privkey.pem

# 3. Reload nginx (pas de restart, connexions en cours préservées)
sudo nginx -t && sudo systemctl reload nginx

# 4. Vérifier
echo | openssl s_client -connect lstracker-demo.itech-civ.org:443 \
   -servername lstracker-demo.itech-civ.org 2>/dev/null \
   | openssl x509 -noout -dates
```

---

## Troubleshooting

### `nginx -t` échoue avec "no such file" sur les certs

```bash
ls -la /home/itech/ssl/itech-civ.org/
```

Ajuster les chemins dans `/etc/nginx/sites-available/lstracker-demo.conf`.

### `502 Bad Gateway`

Le container Docker derrière n'est pas joignable.

```bash
docker ps --filter "name=lst_"
ss -tlnp | grep -E ':9200|:9201'
curl -fsS http://127.0.0.1:9200/actuator/health
docker logs lst_prod_app --tail 50
```

Causes typiques :
- Container down → `docker compose up -d` depuis le bundle
- Healthcheck encore en démarrage (60-90s après `up -d`) → attendre
- Le port n'est pas en loopback (override `public.yml` actif par erreur) → recréer sans l'override

### `SSL: certificate verify failed`

La chaîne intermédiaire n'est pas concaténée au cert :

```bash
cat cert.crt intermediate.crt > /home/itech/ssl/itech-civ.org/fullchain.pem
sudo nginx -t && sudo systemctl reload nginx
```

Vérifier la chaîne :

```bash
openssl crl2pkcs7 -nocrl -certfile /home/itech/ssl/itech-civ.org/fullchain.pem \
  | openssl pkcs7 -print_certs -noout | grep -E 'subject|issuer'
```

Doit montrer au moins 2 certificats (le tien + l'intermédiaire).

### Mixed content / URLs en `http://` au lieu de `https://`

Causes :
1. Spring ne fait pas confiance aux headers proxy → vérifier `server.forward-headers-strategy=NATIVE` dans `application.properties` (déjà configuré dans ce projet).
2. Le CDN n'envoie pas `X-Forwarded-Proto: https` → vérifier la config CDN.
3. nginx ne propage pas le header → vérifier la conf vhost prod.

Test rapide depuis l'extérieur :

```bash
curl -fsI https://lstracker.org/ | grep -i 'location\|set-cookie'
# Toutes les URLs renvoyées doivent être en https://
```

### Login impossible après bascule nginx

Probablement un problème de cookie SameSite/Secure. Vérifier que `server.forward-headers-strategy=NATIVE` est bien actif :

```bash
docker exec lst_prod_app env | grep -i forward
# SERVER_FORWARD_HEADERS_STRATEGY=NATIVE ou via application.properties
```

Si non, ajouter dans le compose env :

```yaml
SERVER_FORWARD_HEADERS_STRATEGY: NATIVE
```

Puis `up -d --force-recreate tracker_app`.
