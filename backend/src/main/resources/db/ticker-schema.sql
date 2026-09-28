-- Idempotent PostgreSQL upgrade for databases created by Hibernate before ticker-based lookup.
-- A CIK identifies an issuer; multiple share-class tickers can legitimately share it.
DO $$
DECLARE constraint_name text;
BEGIN
    IF to_regclass('companies') IS NOT NULL THEN
        FOR constraint_name IN
            SELECT c.conname FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
            WHERE c.conrelid = 'companies'::regclass AND c.contype = 'u'
                AND cardinality(c.conkey) = 1 AND a.attname = 'cik'
        LOOP
            EXECUTE format('ALTER TABLE companies DROP CONSTRAINT %I', constraint_name);
        END LOOP;
        -- Match findByTickerIgnoreCase and protect against concurrent case-variant inserts.
        CREATE UNIQUE INDEX IF NOT EXISTS companies_ticker_upper_unique ON companies (upper(ticker));
        ALTER TABLE companies ADD COLUMN IF NOT EXISTS financial_import_count bigint;
    END IF;
END $$;

^^^
