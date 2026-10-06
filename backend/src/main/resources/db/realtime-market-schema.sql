-- Additive durable aggregates only. Source/feed/synthetic identity never merges feeds.
CREATE TABLE IF NOT EXISTS realtime_market_bar (
    symbol varchar(10) NOT NULL, provider varchar(32) NOT NULL, feed varchar(64) NOT NULL,
    synthetic boolean NOT NULL DEFAULT false, interval varchar(2) NOT NULL CHECK (interval IN ('1m','5m')),
    window_start timestamptz NOT NULL, window_end timestamptz NOT NULL,
    open numeric(24,10) NOT NULL, high numeric(24,10) NOT NULL, low numeric(24,10) NOT NULL,
    close numeric(24,10) NOT NULL, volume numeric(28,6) NOT NULL, vwap numeric(24,10),
    notional numeric(38,16) NOT NULL, trade_count bigint NOT NULL, last_event_time timestamptz NOT NULL, quality varchar(64) NOT NULL,
    PRIMARY KEY (symbol, provider, feed, synthetic, interval, window_start),
    CHECK (window_end > window_start AND volume >= 0 AND trade_count > 0)
);
^^^
CREATE TABLE IF NOT EXISTS realtime_market_analytics (
    symbol varchar(10) NOT NULL, provider varchar(32) NOT NULL, feed varchar(64) NOT NULL,
    synthetic boolean NOT NULL DEFAULT false, window_start timestamptz NOT NULL, window_end timestamptz NOT NULL,
    return_1m_pct numeric(24,10), return_5m_pct numeric(24,10), rolling_volume numeric(28,6),
    rolling_vwap numeric(24,10), volatility_pct numeric(24,10), volume_ratio numeric(24,10),
    anomaly boolean NOT NULL DEFAULT false, quality varchar(64) NOT NULL,
    PRIMARY KEY (symbol, provider, feed, synthetic, window_start)
);
^^^
CREATE INDEX IF NOT EXISTS realtime_anomaly_recent ON realtime_market_analytics
    (symbol, provider, feed, synthetic, window_start DESC) WHERE anomaly = true;
^^^
CREATE TABLE IF NOT EXISTS realtime_reference_state (
    symbol varchar(10) NOT NULL, provider varchar(32) NOT NULL, feed varchar(64) NOT NULL,
    synthetic boolean NOT NULL DEFAULT false, event_time timestamptz NOT NULL, event_id varchar(128) NOT NULL,
    price numeric(24,10) NOT NULL CHECK (price > 0),
    PRIMARY KEY (symbol, provider, feed, synthetic)
);
^^^
ALTER TABLE realtime_market_bar ADD COLUMN IF NOT EXISTS notional numeric(38,16) NOT NULL;
^^^
