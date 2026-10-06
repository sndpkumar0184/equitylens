"""Deterministic Spark expressions shared by production and fixture tests."""
from pyspark.sql import functions as F, types as T, Window

DECIMAL = T.DecimalType(24, 10)
EVENT_SCHEMA = T.StructType([
    T.StructField("schemaVersion", T.IntegerType()),
    *[T.StructField(k, T.StringType()) for k in
      ("eventId", "eventType", "symbol", "eventTime", "receivedAt", "provider", "feed", "exchange")],
    T.StructField("sourceMetadata", T.StructType([T.StructField("synthetic", T.BooleanType())])),
    T.StructField("data", T.StructType([T.StructField("price", DECIMAL),
                                       T.StructField("quantity", T.DecimalType(28, 6))]))
])
IDENTITY = ["symbol", "provider", "feed", "synthetic"]


def normalize_trades(parsed, allow_synthetic=False):
    df = parsed.withColumn("event_time", F.try_to_timestamp("eventTime")) \
        .withColumn("synthetic", F.coalesce("sourceMetadata.synthetic", F.lit(False))) \
        .withColumn("price", F.col("data.price")) .withColumn("quantity", F.col("data.quantity"))
    valid = ((F.col("schemaVersion") == 1) & (F.col("eventType") == "TRADE") &
             F.col("symbol").rlike("^[A-Z0-9][A-Z0-9.-]{0,9}$") &
             F.col("eventId").isNotNull() & (F.length("eventId") > 0) &
             F.col("provider").isNotNull() & F.col("feed").isNotNull() &
             F.col("event_time").isNotNull() & F.try_to_timestamp("receivedAt").isNotNull() &
             (F.col("price") > 0) & (F.col("quantity") > 0) & ((F.col("provider") != "SYNTHETIC") | F.col("synthetic")))
    if not allow_synthetic:
        valid = valid & ~F.col("synthetic")
    return df.withColumn("valid", F.coalesce(valid, F.lit(False)))


def aggregate(trades, interval):
    # Lexicographic event-time + ID is the deterministic tie breaker for equal timestamps.
    ordering = F.struct(F.col("event_time"), F.col("eventTime"), F.col("eventId"))
    result = trades.groupBy(*IDENTITY, F.window("event_time", interval)).agg(
        F.min_by("price", ordering).alias("open"), F.max("price").alias("high"),
        F.min("price").alias("low"), F.max_by("price", ordering).alias("close"),
        F.sum("quantity").alias("volume"), F.sum(F.col("price") * F.col("quantity")).alias("notional"),
        F.count("eventId").alias("trade_count"), F.max("event_time").alias("last_event_time"))
    return result.withColumn("vwap", F.col("notional").cast("decimal(30,10)") / F.col("volume").cast("decimal(28,6)")) \
        .withColumn("interval", F.lit("1m" if interval == "1 minute" else "5m")) \
        .withColumn("window_start", F.col("window.start")) \
        .withColumn("window_end", F.col("window.end")) \
        .withColumn("quality", F.lit("OBSERVED_TRADES_UNCORRECTED")) .drop("window")


def rolling_metrics(bars):
    order = Window.partitionBy(*IDENTITY).orderBy("window_start")
    last5 = order.rowsBetween(-4, 0)
    baseline = order.rowsBetween(-20, -1)
    df = bars.withColumn("previous_close", F.lag("close", 1).over(order)) \
        .withColumn("previous_time", F.lag("window_start", 1).over(order)) \
        .withColumn("close_5m", F.lag("close", 5).over(order)) \
        .withColumn("time_5m", F.lag("window_start", 5).over(order))
    adjacent = F.col("window_start").cast("long") - F.col("previous_time").cast("long") == 60
    full5 = ((F.count("close").over(last5) == 5) &
             (F.col("window_start").cast("long") - F.min("window_start").over(last5).cast("long") == 240))
    full6 = F.col("window_start").cast("long") - F.col("time_5m").cast("long") == 300
    full20 = ((F.count("close").over(baseline) == 20) &
              (F.col("window_start").cast("long") - F.min("window_start").over(baseline).cast("long") == 1200))
    df = df.withColumn("log_return", F.when(adjacent, F.log(F.col("close") / F.col("previous_close"))))
    df = df.withColumn("return_1m_pct", F.when(adjacent, 100 * (F.col("close") / F.col("previous_close") - 1))) \
        .withColumn("return_5m_pct", F.when(full6, 100 * (F.col("close") / F.col("close_5m") - 1))) \
        .withColumn("rolling_volume", F.when(full5, F.sum("volume").over(last5))) \
        .withColumn("rolling_vwap", F.when(full5, F.sum("notional").over(last5).cast("decimal(30,10)") / F.sum("volume").over(last5).cast("decimal(28,6)"))) \
        .withColumn("volatility_pct", F.when(full6 & (F.count("log_return").over(last5) == 5), 100 * F.stddev_samp("log_return").over(last5))) \
        .withColumn("volume_ratio", F.when(full20 & (F.avg("volume").over(baseline) > 0), F.col("volume").cast("decimal(28,6)") / F.avg("volume").over(baseline).cast("decimal(28,6)")))
    return df.withColumn("anomaly", F.coalesce(F.col("volume_ratio") >= 3, F.lit(False)))
