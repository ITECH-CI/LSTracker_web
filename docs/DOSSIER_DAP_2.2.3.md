# Dossier pour la DAP : données à trancher avant la migration de la production (2.2.3)

La version 2.2.3 rattache chaque échantillon à un circuit et à une zone géographique selon des règles explicites. Quatre situations de données restent à trancher par la DAP avant la migration de la production, et une vérification technique est à faire. Pour chacune, ce dossier donne le constat, l'effet visible dans l'application, les options et la requête SQL qui produit la liste nominative.

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

**Constat.** La date de collecte est antérieure au 01/01/2024 (mise en service) ou future. Il s'agit de fautes de frappe sur l'année, saisies avant le contrôle des dates. Sur la base de test : 23 échantillons (année 0024 : 7 ; 0204 : 5 ; 0219 : 1 ; 2021 : 10), tous enregistrés en 2024. Sur la base de production, l'audit avait relevé 423 échantillons sur 46 392 portant une date aberrante.

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

## Décisions attendues

| Point | Liste | Base de test | Décision de la DAP |
|---|---|---|---|
| 1. Sites actifs sans circuit | Requête 1 | 21 sites, dont 2 avec collectes | Rattacher, désactiver ou laisser « Non rattaché » |
| 2. Sites rattachés à plusieurs circuits | Requête 2 | 17 sites ; CHR de Korhogo (15 circuits) et CHR de Daloa (12) en tête | Circuit principal par site, retrait des laboratoires, ou partage confirmé |
| 3. Échantillons sans collecte | Requête 3 | 3600 et 3601 | Conserver, rattacher ou supprimer |
| 4. Années de collecte aberrantes | Requête 4 | 23 échantillons | Correction proposée, correction manuelle ou statu quo |
| 5. Région de test | Requête 5 | « Pkom Region », vide | Vérification technique, sans décision de la DAP |
