-- Retain legacy closes and identify them separately; never treat them as StashGamma observations.
ALTER TABLE market_prices ADD COLUMN IF NOT EXISTS source varchar(32) NOT NULL DEFAULT 'LEGACY';
^^^
ALTER TABLE market_prices DROP CONSTRAINT IF EXISTS uk_market_price_company_date;
^^^
CREATE UNIQUE INDEX IF NOT EXISTS uk_market_price_company_date_source
    ON market_prices (company_id, price_date, source);
^^^
