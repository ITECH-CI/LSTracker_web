# Test de montée en charge — LSTracker

Réponse au cahier des charges, chapitre XI : « tests de montée en charge
représentatifs avant mise en production ». Test du 24/09/2026.

## Méthode

- **Script** : `scripts/charge/test_charge.py` (Python, bibliothèque standard,
  rien à installer). Paliers d'utilisateurs simultanés, une minute chacun ;
  temps de réponse par appel (médiane, 95ᵉ et 99ᵉ centiles), erreurs, débit.
- **Profil** : 70 % d'utilisateurs web (connexion, tableau de bord complet —
  ses 11 appels sur une période de deux ans —, liste des échantillons, pause de
  lecture de 1 à 3 s) et 30 % d'utilisateurs mobiles (connexion, métadonnées,
  synchronisation des dernières 24 h toutes les 5 à 10 s).
- **Volume** : copie de la base gonflée à **598 602 échantillons**, soit
  environ 13 ans d'activité au rythme actuel de la production (46 392).
- **Environnement** : poste de développement, PostgreSQL 14 en conteneur avec
  ses réglages **par défaut** (128 Mo de cache, sans parallélisme), application
  et base sur la même machine. Les chiffres sont donc **pessimistes** par
  rapport à la production réglée (`docs/REGLAGES.md`).

Relancer sur la démonstration (jamais sur la production en service), avec un
compte ADMIN de test :

```bash
LST_LOGIN=... LST_PASSWORD=... \
  python3 scripts/charge/test_charge.py --url https://<adresse-de-la-demo> --paliers 10,25,50 --duree 60
```

## Défauts trouvés et corrigés

| Constat | Cause | Correction |
|---|---|---|
| Répartition par site ou par district bloquée **plusieurs minutes** (normalement 40 ms), dès 10 utilisateurs ; à 50 utilisateurs, pool de connexions épuisé et **effondrement** (34 erreurs, 4,7 requêtes/s). | Après 5 exécutions, PostgreSQL passe à un plan « générique » qui ignore les valeurs des filtres optionnels (`:region IS NULL OR …`) ; la requête abandonnée par le navigateur continue et garde sa connexion. | À l'ouverture de chaque connexion : plans calculés avec les valeurs réelles (`plan_cache_mode = force_custom_plan`) et durée maximale de 2 minutes par requête (`statement_timeout`, variable `DB_STATEMENT_TIMEOUT`). |
| Sous charge : « could not resize shared memory segment … No space left on device ». | Docker ne donne que 64 Mo de mémoire partagée (`/dev/shm`) à un conteneur ; les requêtes parallèles de PostgreSQL l'épuisent. La production (4 processus parallèles par requête) aurait échoué. | `shm_size` : 1 Go en production, 256 Mo en démonstration (compose). |

## Résultats après correction

**0 erreur à tous les paliers.** À 50 utilisateurs simultanés : 46,5 requêtes
par seconde (contre 4,7 avant correction), tableau de bord sous 1 s en médiane
et sous 3,5 s au 99ᵉ centile.

### 10 utilisateurs simultanés (60 s)

1301 requêtes, 21.7 par seconde, 0 erreur(s)

| Appel | Requêtes | Erreurs | Médiane (ms) | 95e centile (ms) | 99e centile (ms) | Max (ms) |
|---|---:|---:|---:|---:|---:|---:|
| sample/data (liste, 25 lignes) | 103 | 0 | 1481 | 1814 | 1822 | 1826 |
| web : connexion | 7 | 0 | 469 | 629 | 629 | 629 |
| dashboard/data/funnel-previous | 104 | 0 | 236 | 276 | 291 | 316 |
| mobile : connexion | 3 | 0 | 132 | 187 | 187 | 187 |
| dashboard/data/funnel | 105 | 0 | 143 | 185 | 227 | 269 |
| dashboard/data/top-performers | 103 | 0 | 64 | 85 | 94 | 103 |
| api_v2/meta/full | 25 | 0 | 14 | 71 | 71 | 71 |
| dashboard/data/coverage | 104 | 0 | 32 | 50 | 63 | 63 |
| dashboard/data/by-district | 103 | 0 | 30 | 40 | 46 | 65 |
| dashboard/data/by-region | 103 | 0 | 30 | 40 | 53 | 55 |
| dashboard/sample_status_by_sample_type | 103 | 0 | 30 | 40 | 41 | 41 |
| dashboard/data/by-site | 103 | 0 | 31 | 39 | 56 | 56 |
| dashboard/data/series | 104 | 0 | 17 | 28 | 38 | 54 |
| api_v2/sync/samples/pull (24 h) | 25 | 0 | 6 | 23 | 24 | 24 |
| dashboard/data/step-durations | 103 | 0 | 8 | 11 | 13 | 14 |
| dashboard/data/type-breakdown | 103 | 0 | 5 | 8 | 9 | 9 |

### 25 utilisateurs simultanés (60 s)

2432 requêtes, 40.5 par seconde, 0 erreur(s)

| Appel | Requêtes | Erreurs | Médiane (ms) | 95e centile (ms) | 99e centile (ms) | Max (ms) |
|---|---:|---:|---:|---:|---:|---:|
| sample/data (liste, 25 lignes) | 190 | 0 | 2675 | 3631 | 3828 | 3981 |
| dashboard/data/funnel-previous | 195 | 0 | 497 | 746 | 883 | 904 |
| web : connexion | 19 | 0 | 399 | 673 | 772 | 772 |
| dashboard/data/funnel | 195 | 0 | 320 | 470 | 499 | 616 |
| dashboard/data/top-performers | 192 | 0 | 160 | 250 | 294 | 387 |
| mobile : connexion | 6 | 0 | 138 | 191 | 191 | 191 |
| dashboard/sample_status_by_sample_type | 191 | 0 | 84 | 144 | 161 | 190 |
| dashboard/data/coverage | 193 | 0 | 79 | 139 | 176 | 193 |
| dashboard/data/by-site | 190 | 0 | 79 | 130 | 155 | 282 |
| dashboard/data/by-district | 191 | 0 | 77 | 130 | 154 | 294 |
| dashboard/data/by-region | 191 | 0 | 73 | 116 | 141 | 160 |
| api_v2/meta/full | 51 | 0 | 20 | 64 | 91 | 91 |
| dashboard/data/series | 194 | 0 | 38 | 58 | 78 | 105 |
| dashboard/data/step-durations | 191 | 0 | 18 | 34 | 40 | 50 |
| dashboard/data/type-breakdown | 192 | 0 | 9 | 19 | 27 | 37 |
| api_v2/sync/samples/pull (24 h) | 51 | 0 | 10 | 17 | 29 | 29 |

### 50 utilisateurs simultanés (60 s)

2792 requêtes, 46.5 par seconde, 0 erreur(s)

| Appel | Requêtes | Erreurs | Médiane (ms) | 95e centile (ms) | 99e centile (ms) | Max (ms) |
|---|---:|---:|---:|---:|---:|---:|
| sample/data (liste, 25 lignes) | 205 | 0 | 4547 | 5374 | 5701 | 6323 |
| dashboard/data/funnel | 219 | 0 | 710 | 2996 | 3487 | 3653 |
| dashboard/data/funnel-previous | 217 | 0 | 978 | 2952 | 3449 | 4229 |
| api_v2/meta/full | 118 | 0 | 322 | 1887 | 2628 | 2877 |
| dashboard/data/coverage | 211 | 0 | 180 | 1400 | 2255 | 2590 |
| web : connexion | 35 | 0 | 625 | 1287 | 1566 | 1566 |
| api_v2/sync/samples/pull (24 h) | 118 | 0 | 211 | 1246 | 1993 | 2990 |
| dashboard/data/top-performers | 207 | 0 | 267 | 1171 | 1658 | 2933 |
| dashboard/data/series | 211 | 0 | 96 | 808 | 1469 | 2391 |
| dashboard/data/type-breakdown | 208 | 0 | 84 | 647 | 1465 | 2757 |
| mobile : connexion | 15 | 0 | 187 | 498 | 516 | 516 |
| dashboard/data/by-site | 205 | 0 | 132 | 452 | 2956 | 3426 |
| dashboard/data/by-region | 206 | 0 | 105 | 412 | 1667 | 2592 |
| dashboard/data/by-district | 205 | 0 | 122 | 400 | 1048 | 2718 |
| dashboard/sample_status_by_sample_type | 206 | 0 | 92 | 229 | 904 | 1256 |
| dashboard/data/step-durations | 206 | 0 | 34 | 185 | 621 | 800 |


## Point d'amélioration restant

- **Liste des échantillons** : 1,5 s en médiane à 10 utilisateurs, 4,5 s à 50.
  L'affichage d'une page est rapide (index utilisé) ; le coût vient du
  **comptage total** refait à chaque affichage (0,56 s pour 600 000
  échantillons, sans filtre). Pistes : comptage allégé (sans les jointures
  inutiles), ou nombre approximatif au-delà d'un seuil. Au volume actuel de la
  production (46 000 échantillons), ce coût est environ dix fois moindre.
- `spring.jpa.open-in-view` est actif : chaque requête garde sa connexion
  jusqu'à la fin du rendu de la page. Le désactiver soulagerait le pool, mais
  demande de vérifier chaque écran (chargements différés) : à planifier.
