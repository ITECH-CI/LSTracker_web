-- Schéma applicatif. Nom fixé (référencé en dur dans le code Java et dans
-- le JDBC URL via currentSchema=sample_tracker).
CREATE SCHEMA IF NOT EXISTS sample_tracker;

-- Configure le search_path par défaut sur la DATABASE courante (peu importe
-- son nom : sample_tracker, sample_tracker_demo, sample_tracker_prod, ...).
-- L'ancienne version "ALTER DATABASE sample_tracker SET search_path ..."
-- échouait quand POSTGRES_DB ≠ "sample_tracker" (cas demo/prod isolées).
DO $$
BEGIN
  EXECUTE format(
    'ALTER DATABASE %I SET search_path TO sample_tracker, public',
    current_database()
  );
END$$;