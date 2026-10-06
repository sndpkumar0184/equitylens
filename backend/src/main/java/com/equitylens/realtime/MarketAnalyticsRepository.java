package com.equitylens.realtime;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MarketAnalyticsRepository {
    private final JdbcTemplate jdbc;
    public MarketAnalyticsRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> reference(String symbol,String provider,String feed,boolean synthetic) {
        return jdbc.queryForList("select price, event_time as \"eventTime\" from realtime_reference_state where symbol=? and provider=? and feed=? and synthetic=?",
            symbol,provider,feed,synthetic);
    }
    public List<Map<String,Object>> bars(String symbol,String interval,int minutes,int limit,String provider,String feed,boolean synthetic) {
        return jdbc.queryForList("""
            select symbol, provider, feed, synthetic, interval, window_start as "windowStart", window_end as "windowEnd",
              open, high, low, close, volume, vwap, trade_count as "tradeCount", quality, last_event_time as "lastEventTime"
            from realtime_market_bar where symbol=? and interval=? and provider=? and feed=? and synthetic=?
              and window_start >= current_timestamp - (? * interval '1 minute')
            order by window_start desc limit ?
            """,symbol,interval,provider,feed,synthetic,minutes,limit);
    }
    public List<Map<String,Object>> metrics(String symbol,int minutes,int limit,String provider,String feed,boolean synthetic,boolean anomalies) {
        return jdbc.queryForList("""
            select symbol, provider, feed, synthetic, window_start as "windowStart", window_end as "windowEnd",
              return_1m_pct as "return1mPct", return_5m_pct as "return5mPct", rolling_volume as "rollingVolume",
              rolling_vwap as "rollingVwap", volatility_pct as "volatilityPct", volume_ratio as "volumeRatio", anomaly, quality
            from realtime_market_analytics where symbol=? and provider=? and feed=? and synthetic=?
              and window_start >= current_timestamp - (? * interval '1 minute')
            """ + (anomalies?" and anomaly=true":"") + " order by window_start desc limit ?",symbol,provider,feed,synthetic,minutes,limit);
    }
}
