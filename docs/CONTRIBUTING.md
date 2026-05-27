# Contribuer à LSTracker Web

Guide pour les contributeurs : workflow PR, branch protection, conventions de commit.

## Sommaire

1. [Workflow PR (depuis un fork)](#workflow-pr-depuis-un-fork)
2. [Workflow direct (collaborateurs ITECH-CI)](#workflow-direct-collaborateurs-itech-ci)
3. [Branch protection sur main](#branch-protection-sur-main)
4. [Conventions de commit](#conventions-de-commit)
5. [Releases](#releases)

---

## Workflow PR (depuis un fork)

Idéal pour les contributeurs externes ou pour valider ses propres changements avant merge.

### 1. Setup initial (une fois)

```bash
# Fork ITECH-CI/LSTracker_web via le bouton "Fork" sur GitHub.
# Tu obtiens https://github.com/<toi>/LSTracker_web

git clone git@github.com:<toi>/LSTracker_web.git
cd LSTracker_web

# Ajouter ITECH-CI comme remote upstream (pour récupérer les updates)
git remote add upstream git@github.com:ITECH-CI/LSTracker_web.git
git remote -v
```

### 2. Pour chaque PR

```bash
# 1. Sync avec main d'ITECH-CI
git checkout main
git pull upstream main
git push origin main

# 2. Créer une branche feature
git checkout -b feat/nom-explicite

# 3. Coder, commit (cf. conventions ci-dessous)
git add ...
git commit -m "feat: ..."

# 4. Push sur ton fork
git push origin feat/nom-explicite

# 5. Ouvrir une PR vers ITECH-CI:main via le lien affiché par git push,
#    ou via gh CLI :
gh pr create --repo ITECH-CI/LSTracker_web \
             --base main \
             --head <toi>:feat/nom-explicite
```

### 3. Pendant la review

```bash
# Si besoin de modifier après les remarques
git add ...
git commit -m "fix: review feedback ..."
git push origin feat/nom-explicite
# La PR se met à jour automatiquement
```

---

## Workflow direct (collaborateurs ITECH-CI)

Pour les membres avec droits write sur `ITECH-CI/LSTracker_web` :

```bash
git clone git@github.com:ITECH-CI/LSTracker_web.git
cd LSTracker_web

git checkout -b feat/nom-explicite
# ... code ...
git push origin feat/nom-explicite

# Ouvrir PR
gh pr create --base main --title "feat: ..." --fill
```

Avantages : pas de fork à maintenir, accès aux secrets CI dans les workflows (utile si on ajoute des tests qui en ont besoin).

---

## Branch protection sur main

À configurer **une fois** par un admin sur https://github.com/ITECH-CI/LSTracker_web/settings/branches.

### Règles recommandées

Créer une **branch protection rule** pour `main` avec :

| Option | Valeur | Pourquoi |
|---|---|---|
| Require a pull request before merging | ✅ | Force le passage par PR |
| Require approvals | ✅ (1 minimum) | Au moins un reviewer |
| Dismiss stale pull request approvals when new commits are pushed | ✅ | Évite les approvals obsolètes |
| Require review from Code Owners | ✅ | Honore le `CODEOWNERS` |
| Require conversation resolution before merging | ✅ | Pas de PR mergée avec commentaires non résolus |
| Require status checks to pass before merging | ⚠️ Optionnel | Si tests CI ajoutés plus tard |
| Require linear history | ✅ | Force squash/rebase, historique propre |
| Restrict who can push to matching branches | ✅ | Empêche les push directs (admin override toujours possible) |
| Do not allow bypassing the above settings | ✅ (en prod sérieuse) | Personne ne peut bypass, même admin. |

### Configuration via gh CLI (alternative)

```bash
gh api -X PUT /repos/ITECH-CI/LSTracker_web/branches/main/protection \
  --input - <<'EOF'
{
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": true,
    "require_last_push_approval": false
  },
  "required_status_checks": null,
  "required_conversation_resolution": true,
  "required_linear_history": true,
  "enforce_admins": false,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF
```

> Note : `enforce_admins: false` permet aux admins (toi) de bypass en cas d'urgence (hotfix critique). Passe à `true` quand l'équipe sera plus large.

---

## Conventions de commit

Style **Conventional Commits** (https://www.conventionalcommits.org), facilite la lecture de l'historique et génère un changelog auto.

### Format

```
<type>(<scope>): <résumé court à l'impératif>

<corps optionnel décrivant le pourquoi>

<footer optionnel>
```

### Types courants

| Type | Quand l'utiliser |
|---|---|
| `feat` | Nouvelle fonctionnalité visible utilisateur |
| `fix` | Correction de bug |
| `docs` | Modification de doc uniquement |
| `refactor` | Réorganisation sans changement de comportement |
| `perf` | Amélioration de performance |
| `test` | Ajout/correction de tests |
| `ci` | Modifications GitHub Actions, Dockerfile, compose |
| `ops` | Scripts d'opération, configs serveur (nginx, postgres) |
| `chore` | Maintenance (dépendances, gitignore, etc.) |

### Exemples

```
feat(auth): support de l'authentification par OTP

fix(api): empêcher la perte de référence sur sample lors d'un rollback Liquibase

docs: réécriture INSTALL.md pour le workflow bundle de release

ci: build multi-arch (amd64 + arm64) pour ghcr.io

ops(nginx): déplacer le cert wildcard vers /home/itech/ssl/

refactor: extraire la logique JWT dans JwtService dédié
```

### Breaking changes

Marquer avec `!` après le scope :

```
feat(api)!: nouveau format de réponse /api_v2/samples

BREAKING CHANGE: le champ `sample.tracking[]` est renommé en `sample.events[]`.
Les clients doivent être mis à jour avant ce déploiement.
```

---

## Releases

Voir [README.md](../README.md) §"Pour les dev — publier une release".

Résumé :

```bash
git tag -a vX.Y.Z -m "Release vX.Y.Z — résumé"
git push origin vX.Y.Z
```

GitHub Actions s'occupe du reste (build image + bundle attaché à la Release).

### Quand bumper quoi ?

Suivre [SemVer](https://semver.org) :

- **MAJOR** (X.0.0) : breaking change (incompatibilité API, format DB, etc.)
- **MINOR** (x.Y.0) : nouvelle fonctionnalité retrocompatible
- **PATCH** (x.y.Z) : bug fix ou doc seulement
