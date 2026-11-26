# Dossier pour la DAP et la DISD : données à trancher avant la migration de la production (2.2.3)

La version 2.2.3 rattache chaque échantillon à un circuit et à une zone géographique selon des règles explicites. Cinq situations de données restent à trancher par la DAP avant la migration de la production (points 1 à 4 et 6), une vérification technique est à faire (point 5) et la DISD reprend une partie des comptes (point 7). Pour chacune, ce dossier donne le constat, l'effet visible dans l'application, les options et la requête SQL qui produit la liste nominative.

Les chiffres cités viennent de la base de test (2 995 échantillons, régions PORO et HAUT-SASSANDRA). Les listes de décision se produisent sur la base de production, ou sur sa copie restaurée en démonstration.

## Exécuter les requêtes

Les requêtes sont en lecture seule. Ouvrir une session psql dont toutes les transactions sont en lecture seule :

```sh
docker exec -it lst_prod_db sh -c \
  'PGOPTIONS="-c default_transaction_read_only=on" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

(`lst_demo_db` pour la démonstration.) Puis, dans la session :

```sql
SET search_path TO sample_tracker, public;
```

Aucune requête de ce dossier ne modifie la base. Les corrections décidées passent par les écrans d'administration, par le script `scripts/sql/dates_aberrantes.sql` ou par un changeset Liquibase, jamais par une commande saisie à la main.

## 1. Sites actifs sans circuit

**Constat.** Un site actif n'est rattaché à aucun circuit actif. Sur la base de test : 21 sites, dont 2 ont des collectes (CSR D PUBLIC de KASSIRIME, 29 ; SSSU-SAJ KORHOGO, 22) ; les 19 autres n'en ont aucune.

**Effet.** Les échantillons de ces sites figurent sur la ligne « Non rattaché » de la carte « Circuits ». Sur le mobile, un site sans circuit n'est proposé à aucun convoyeur.

**Options.** Rattacher le site au circuit qui le dessert (Administration › Circuits) ; le désactiver s'il n'est plus desservi ; le laisser en l'état, ses échantillons restant « Non rattaché ».

```sql
SELECT r.name AS region, d.name AS district, s.id AS site_id, s.name AS site, s.dhis_code,
       (SELECT count(*) FROM sample_retrieving sr JOIN sample x ON x.sample_retrieving_id = sr.id
         WHERE sr.site_id = s.id) AS echantillons_collectes
FROM site s
LEFT JOIN district d ON d.id = s.district_id
LEFT JOIN region r ON r.id = d.region_id
WHERE s.is_active
  AND NOT EXISTS (SELECT 1 FROM circuit_site cs JOIN circuit c ON c.id = cs.circuit_id
                  WHERE cs.site_id = s.id AND cs.is_active AND c.is_active)
ORDER BY echantillons_collectes DESC, r.name, d.name, s.name;
```

## 2. Sites rattachés à plusieurs circuits

**Constat.** Un site actif appartient à plusieurs circuits actifs. Sur la base de test : 17 sites. En tête, le CHR PUBLIC DE KORHOGO (15 circuits) et le CHR PUBLIC DE DALOA (12 circuits), suivis du CSUS CAT PUBLIC de KORHOGO (7) : ces trois structures n'ont aucune collecte, ce sont des laboratoires de destination inscrits comme membres des circuits (le script `scripts/sql/labos_dans_les_axes.sql` les recense). Parmi les sites qui collectent : HG PUBLIC de ZOUKOUGBEU (3 circuits, 253 échantillons), HG PUBLIC de SINEMANTIALI (4 ; 78), HG PUBLIC de ISSIA (5 ; 48), HG PUBLIC de M'BENGUE (5 ; 21).

**Effet.** Les collectes ne portent pas de trajet (aucune des 3 263 collectes de la base de test), donc le circuit d'un échantillon se déduit de son site. Quand le site appartient à plusieurs circuits, l'échantillon ne peut être attribué à aucun d'eux : il figure sur la ligne « Sites partagés entre plusieurs circuits ». Chaque échantillon reste compté une seule fois.

**Options.** Pour chaque site, désigner le circuit qui le dessert réellement et retirer les autres rattachements ; retirer des circuits les laboratoires qui n'y collectent pas ; ou confirmer le partage, les échantillons restant sur la ligne « Sites partagés ».

```sql
SELECT r.name AS region, d.name AS district, s.id AS site_id, s.name AS site,
       count(*) AS nb_circuits,
       string_agg(c.circuit_number, ', ' ORDER BY c.circuit_number) AS circuits,
       (SELECT count(*) FROM sample_retrieving sr JOIN sample x ON x.sample_retrieving_id = sr.id
         WHERE sr.site_id = s.id) AS echantillons_collectes
FROM circuit_site cs
JOIN circuit c ON c.id = cs.circuit_id
JOIN site s ON s.id = cs.site_id
LEFT JOIN district d ON d.id = s.district_id
LEFT JOIN region r ON r.id = d.region_id
WHERE cs.is_active AND c.is_active AND s.is_active
GROUP BY r.name, d.name, s.id, s.name
HAVING count(*) > 1
ORDER BY nb_circuits DESC, s.name;
```

## 3. Échantillons sans collecte

**Constat.** Un échantillon n'est rattaché à aucune collecte. Sur la base de test : 2 échantillons, 3600 (CV) et 3601 (BS), enregistrés en septembre 2025, sans numéro d'échantillon ni de laboratoire, au statut « en cours d'acheminement ». Leur site demandeur est renseigné (CHR PUBLIC DE DALOA et CSR DM PUBLIC de BANDIAHI, district DALOA).

**Effet.** Ils sont comptés dans la zone de leur site demandeur (HAUT-SASSANDRA, DALOA) et visibles des superviseurs de cette zone. Sans collecte, ils n'ont ni convoyeur ni date d'enlèvement. Un échantillon qui n'aurait ni collecte ni site demandeur figurerait sur la ligne « Zone non renseignée » des comptes nationaux.

**Contrainte de base.** Depuis la 2.2.3, la contrainte `ck_sample_collection_or_requester` refuse tout nouvel échantillon sans collecte ni site demandeur ; elle n'est pas encore validée sur les lignes existantes. Sa validation, par un changeset distinct, suppose que la seconde requête ci-dessous renvoie 0 (c'est le cas sur la base de test).

**Options.** Conserver ces échantillons, rattachés par leur site demandeur ; les rattacher à leur collecte réelle si elle est retrouvée ; les supprimer s'il s'agit de saisies de test ou de doublons.

```sql
SELECT x.id, x.sample_identifier, x.lab_number, st.name AS type,
       COALESCE(ss.description, ss.status) AS statut,
       x.collection_date, x.created_at,
       s.name AS site_demandeur, d.name AS district, r.name AS region,
       l.lab_name AS labo_destination,
       CASE WHEN x.requester_site_id IS NULL THEN 'sans zone' ELSE 'zone du site demandeur' END AS rattachement
FROM sample x
LEFT JOIN sample_type st ON st.id = x.sample_type_id
LEFT JOIN sample_status ss ON ss.id = x.sample_status_id
LEFT JOIN site s ON s.id = x.requester_site_id
LEFT JOIN district d ON d.id = s.district_id
LEFT JOIN region r ON r.id = d.region_id
LEFT JOIN lab l ON l.id = x.destination_lab_id
WHERE x.sample_retrieving_id IS NULL
ORDER BY x.id;

-- Lignes qui empêcheraient la validation de la contrainte (attendu : 0)
SELECT count(*) AS sans_collecte_ni_site_demandeur
FROM sample WHERE sample_retrieving_id IS NULL AND requester_site_id IS NULL;
```

## 4. Échantillons à l'année de collecte aberrante

**Constat.** La date de collecte est antérieure au 1ᵉʳ janvier 2024 ou future. Il s'agit de fautes de frappe sur l'année, saisies avant le contrôle des dates. Sur la base de test : 23 échantillons (année 0024 : 7 ; 0204 : 5 ; 0219 : 1 ; 2021 : 10), tous enregistrés en 2024. Sur la base de production, l'audit avait relevé 423 échantillons sur 46 392 portant une date aberrante.

**Effet.** Ces échantillons sortent de toutes les périodes du tableau de bord et des rapports.

**Options.** Appliquer la correction proposée (même jour, même mois, même heure, année la plus proche de la date d'enregistrement) ; corriger à la main les cas que la DAP juge douteux ; ou les laisser hors des périodes. La correction se fait par `scripts/sql/dates_aberrantes.sql`, qui garde les valeurs d'origine et couvre aussi les autres dates d'étape : audit seul par défaut, correction avec `-v corriger=1` une fois l'audit examiné.

```sql
SELECT x.id, x.sample_identifier, x.lab_number,
       to_char(x.collection_date, 'YYYY-MM-DD HH24:MI') AS collecte_saisie,
       to_char(x.created_at, 'YYYY-MM-DD HH24:MI') AS enregistre_le,
       extract(year FROM x.collection_date)::int AS annee_saisie,
       (SELECT y FROM generate_series(extract(year FROM x.created_at)::int - 1,
                                      extract(year FROM x.created_at)::int + 1) AS y
         WHERE x.collection_date + make_interval(years => y - extract(year FROM x.collection_date)::int) <= localtimestamp
         ORDER BY abs(extract(epoch FROM (x.collection_date
                   + make_interval(years => y - extract(year FROM x.collection_date)::int) - x.created_at)))
         LIMIT 1) AS annee_proposee,
       COALESCE(sc.name, sd.name) AS site, r.name AS region
FROM sample x
LEFT JOIN sample_retrieving sr ON sr.id = x.sample_retrieving_id
LEFT JOIN site sc ON sc.id = sr.site_id
LEFT JOIN site sd ON sd.id = x.requester_site_id
LEFT JOIN district d ON d.id = COALESCE(sc.district_id, sd.district_id)
LEFT JOIN region r ON r.id = d.region_id
WHERE x.collection_date < TIMESTAMP '2024-01-01' OR x.collection_date > localtimestamp
ORDER BY annee_saisie, x.id;

-- Synthèse par année saisie
SELECT extract(year FROM collection_date)::int AS annee_saisie, count(*) AS echantillons
FROM sample
WHERE collection_date < TIMESTAMP '2024-01-01' OR collection_date > localtimestamp
GROUP BY 1 ORDER BY 1;
```

## 5. Vérification avant migration : région de test « Pkom Region »

**Constat.** La base de test contient une région de test, « Pkom Region », avec un district « Pkom DIs », sans site, laboratoire, rattachement de circuit, affectation d'utilisateur ni échantillon. Elle ne doit pas exister en production.

**À faire avant la migration.** Exécuter la requête sur la base de production :
- aucune ligne : rien à faire ;
- une ligne dont tous les compteurs sont à 0 : désactiver le district puis la région depuis Administration › Districts et Administration › Régions ;
- un compteur non nul : ne pas migrer en l'état ; identifier les données rattachées et les soumettre à l'administration centrale.

```sql
SELECT r.id AS region_id, r.name AS region, r.is_active,
       (SELECT count(*) FROM district d WHERE d.region_id = r.id) AS districts,
       (SELECT string_agg(d.name || CASE WHEN d.is_active THEN '' ELSE ' (inactif)' END, ', ')
          FROM district d WHERE d.region_id = r.id) AS noms_districts,
       (SELECT count(*) FROM site s JOIN district d ON d.id = s.district_id WHERE d.region_id = r.id) AS sites,
       (SELECT count(*) FROM lab l JOIN district d ON d.id = l.district_id WHERE d.region_id = r.id) AS labos,
       (SELECT count(*) FROM circuit_site cs JOIN site s ON s.id = cs.site_id
          JOIN district d ON d.id = s.district_id WHERE d.region_id = r.id) AS rattachements_circuit,
       (SELECT count(*) FROM app_user_has_region h WHERE h.region_id = r.id)
       + (SELECT count(*) FROM app_user_has_district h JOIN district d ON d.id = h.district_id
            WHERE d.region_id = r.id)
       + (SELECT count(*) FROM app_user_has_site h JOIN site s ON s.id = h.site_id
            JOIN district d ON d.id = s.district_id WHERE d.region_id = r.id) AS affectations_utilisateurs,
       (SELECT count(*) FROM sample x
          LEFT JOIN sample_retrieving sr ON sr.id = x.sample_retrieving_id
          JOIN site s ON s.id = COALESCE(sr.site_id, x.requester_site_id)
          JOIN district d ON d.id = s.district_id WHERE d.region_id = r.id) AS echantillons
FROM region r
WHERE r.name ILIKE '%pkom%';
```

## 6. Non-conformités sans motif de rejet

**Constat.** Un échantillon au statut « non conforme » n'a aucun motif pris dans le référentiel des types de rejet : la raison n'existe qu'en commentaire libre, quand elle existe. Sur la base de test : 62 non-conformités sur 64, dont 30 sur 31 dans HAUT-SASSANDRA et 32 sur 33 dans PORO. Le commentaire est presque toujours renseigné (« échantillon hémolysé », « fiche sans échantillon », « échantillon insuffisant »…), avec des graphies variables pour une même raison. Le commentaire le plus fréquent, « échantillon non parvenu » et ses variantes (environ 18 échantillons), ne correspond à aucun motif du référentiel.

**Effet.** La répartition des non-conformités par motif (ligne « Détails » du tableau par zone) et tout indicateur par motif sont vides ou presque, alors que les non-conformités sont bien comptées.

**Options.** Établir la correspondance entre les commentaires libres et les motifs du référentiel (requête 6.3), puis l'appliquer en gardant le commentaire d'origine ; décider si « échantillon non parvenu » relève d'une non-conformité, d'un motif à créer ou d'un autre traitement (échantillon perdu) ; laisser « Autres » pour les commentaires sans correspondance. La correspondance retenue s'applique par un script relu ou un changeset Liquibase, pas par une saisie à la main.

```sql
-- 6.1 Par région
SELECT COALESCE(r.name, 'Zone non renseignée') AS region,
       count(*) AS non_conformes,
       count(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM sample_rejection sj
                          WHERE sj.sample_id = x.id AND sj.sample_rejection_type_id IS NOT NULL)) AS sans_motif
FROM sample x
JOIN sample_status ss ON ss.id = x.sample_status_id
LEFT JOIN sample_retrieving sr ON sr.id = x.sample_retrieving_id
LEFT JOIN site s ON s.id = COALESCE(sr.site_id, x.requester_site_id)
LEFT JOIN district d ON d.id = s.district_id
LEFT JOIN region r ON r.id = d.region_id
WHERE ss.status = 'NON_CONFORM'
GROUP BY r.name
ORDER BY sans_motif DESC, region;

-- 6.2 Par site
SELECT COALESCE(r.name, 'Zone non renseignée') AS region, d.name AS district, s.name AS site,
       count(*) AS non_conformes,
       count(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM sample_rejection sj
                          WHERE sj.sample_id = x.id AND sj.sample_rejection_type_id IS NOT NULL)) AS sans_motif
FROM sample x
JOIN sample_status ss ON ss.id = x.sample_status_id
LEFT JOIN sample_retrieving sr ON sr.id = x.sample_retrieving_id
LEFT JOIN site s ON s.id = COALESCE(sr.site_id, x.requester_site_id)
LEFT JOIN district d ON d.id = s.district_id
LEFT JOIN region r ON r.id = d.region_id
WHERE ss.status = 'NON_CONFORM'
GROUP BY r.name, d.name, s.name
ORDER BY sans_motif DESC, region, district, site;

-- 6.3 Commentaires libres des non-conformités sans motif, pour établir la correspondance
SELECT COALESCE(NULLIF(lower(trim(sj.comment)), ''), '(aucun commentaire)') AS commentaire,
       count(DISTINCT x.id) AS echantillons
FROM sample x
JOIN sample_status ss ON ss.id = x.sample_status_id
LEFT JOIN sample_rejection sj ON sj.sample_id = x.id
WHERE ss.status = 'NON_CONFORM'
  AND NOT EXISTS (SELECT 1 FROM sample_rejection t
                  WHERE t.sample_id = x.id AND t.sample_rejection_type_id IS NOT NULL)
GROUP BY 1
ORDER BY echantillons DESC, commentaire;

-- 6.4 Liste nominative
SELECT x.id, x.lab_number, st.name AS type,
       to_char(x.rejection_date, 'YYYY-MM-DD') AS date_rejet,
       s.name AS site, r.name AS region, l.lab_name AS labo_destination,
       string_agg(DISTINCT NULLIF(trim(sj.comment), ''), ' / ') AS commentaire
FROM sample x
JOIN sample_status ss ON ss.id = x.sample_status_id
LEFT JOIN sample_type st ON st.id = x.sample_type_id
LEFT JOIN sample_rejection sj ON sj.sample_id = x.id
LEFT JOIN sample_retrieving sr ON sr.id = x.sample_retrieving_id
LEFT JOIN site s ON s.id = COALESCE(sr.site_id, x.requester_site_id)
LEFT JOIN district d ON d.id = s.district_id
LEFT JOIN region r ON r.id = d.region_id
LEFT JOIN lab l ON l.id = x.destination_lab_id
WHERE ss.status = 'NON_CONFORM'
  AND NOT EXISTS (SELECT 1 FROM sample_rejection t
                  WHERE t.sample_id = x.id AND t.sample_rejection_type_id IS NOT NULL)
GROUP BY x.id, x.lab_number, st.name, x.rejection_date, s.name, r.name, l.lab_name
ORDER BY r.name, s.name, x.id;

-- 6.5 Référentiel des motifs de rejet
SELECT id, rejection_type, is_active FROM sample_rejection_type ORDER BY rejection_type;
```

## 7. Pour la DISD : comptes au profil « Utilisateur » qui sont des convoyeurs

**Constat.** Des comptes de convoyeur ont le rôle USER (« Utilisateur (consultation) ») et le type d'utilisateur CONVOYEUR. Sur la base de test : 17 comptes, tous actifs, contre 2 au rôle CONVOYEUR. Ils fonctionnent, car le serveur et le mobile tiennent compte du type d'utilisateur, mais la liste des comptes les affiche comme des profils de consultation et toute règle fondée sur le rôle les traite comme tels. La même situation existe pour 16 comptes de laboratoire (rôle USER, type BIOLOGISTE).

**À faire lors de la reprise des comptes.** Passer ces comptes au profil « Convoyeur » depuis Administration › Utilisateurs, en vérifiant que leurs circuits restent affectés ; la modification est journalisée. Le même traitement vers « Technicien ou superviseur de laboratoire » est à décider pour les comptes de laboratoire.

```sql
-- 7.1 Comptes USER de type convoyeur
SELECT u.id, u.login, u.first_name, u.last_name, u.user_level, u.is_active,
       to_char(u.last_login, 'YYYY-MM-DD') AS derniere_connexion,
       (SELECT count(*) FROM app_user_has_circuit h WHERE h.app_user_id = u.id) AS circuits,
       (SELECT count(*) FROM sample_retrieving sr WHERE sr.app_user_id = u.id) AS collectes
FROM app_user u
WHERE u.role = 'USER' AND upper(u.user_type) = 'CONVOYEUR'
ORDER BY u.is_active DESC, u.login;

-- 7.2 Synthèse des comptes USER par type d'utilisateur
SELECT COALESCE(user_type, '(vide)') AS type_utilisateur, count(*) AS comptes,
       count(*) FILTER (WHERE is_active) AS actifs
FROM app_user WHERE role = 'USER'
GROUP BY 1 ORDER BY comptes DESC;
```

## Décisions attendues

| Point | Liste | Base de test | Décision attendue |
|---|---|---|---|
| 1. Sites actifs sans circuit | Requête 1 | 21 sites, dont 2 avec collectes | Rattacher, désactiver ou laisser « Non rattaché » |
| 2. Sites rattachés à plusieurs circuits | Requête 2 | 17 sites ; CHR de Korhogo (15 circuits) et CHR de Daloa (12) en tête | Circuit principal par site, retrait des laboratoires, ou partage confirmé |
| 3. Échantillons sans collecte | Requête 3 | 3600 et 3601 | Conserver, rattacher ou supprimer |
| 4. Années de collecte aberrantes | Requête 4 | 23 échantillons | Correction proposée, correction manuelle ou statu quo |
| 5. Région de test | Requête 5 | « Pkom Region », vide | Vérification technique, sans décision de la DAP |
| 6. Non-conformités sans motif | Requêtes 6.1 à 6.5 | 62 sur 64, dont 30 sur 31 dans HAUT-SASSANDRA | DAP : correspondance commentaires et motifs, sort de « échantillon non parvenu » |
| 7. Comptes USER de type convoyeur | Requêtes 7.1 et 7.2 | 17 comptes (et 16 de type BIOLOGISTE) | DISD : passage au profil Convoyeur à la reprise des comptes |
