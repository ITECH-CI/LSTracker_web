<!--
Merci de remplir ce template pour chaque PR. Ça aide les reviewers à
comprendre le contexte rapidement.
-->

## Description

<!-- Que fait cette PR ? Pourquoi ? -->

## Type de changement

<!-- Cocher les cases pertinentes -->

- [ ] 🐛 Bug fix (correction sans changement de comportement attendu)
- [ ] ✨ Feature (nouvelle fonctionnalité)
- [ ] ♻️ Refactor (réorganisation sans changement de comportement)
- [ ] 📝 Documentation
- [ ] 🔧 Ops / CI / Build / Config
- [ ] ⚠️ Breaking change (rupture compatibilité — détailler ci-dessous)

## Comment tester

<!-- Étapes pour valider manuellement. Exemple :
1. Démarrer la demo avec `docker compose ... up -d`
2. Aller sur /login
3. Vérifier que ...
-->

## Checklist

- [ ] Code testé localement (build OK, tests passent si présents)
- [ ] Documentation mise à jour si comportement modifié (README, docs/, INSTALL.md)
- [ ] Pas de secret hardcodé (clé, password, token)
- [ ] Pas de fichier sensible commit (`.env`, `*.jks`, `key.properties`)
- [ ] Si migration DB : Liquibase changeset ajouté + testé sur demo

## Notes pour le reviewer

<!-- Tout ce qui peut aider : points d'attention, choix d'archi à valider,
     dépendances avec d'autres PR, etc. -->

## Breaking changes (si applicable)

<!-- Détailler :
     - Ce qui casse pour les déploiements existants
     - Étapes de migration nécessaires (config, DB, env vars)
     - Plan de rollback
-->
