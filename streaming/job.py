"""Separate Spark Structured Streaming application; no provider APIs or credentials."""
import json
import logging
import os
import time
import uuid
from datetime import timezone
from decimal import Decimal
import psycopg
from psycopg.rows import dict_row
from kafka import KafkaProducer
from pyspark.sql import SparkSession, functions as F
from pyspark.sql.streaming import StreamingQueryListener
from analytics import EVENT_SCHEMA, IDENTITY, aggregate, normalize_trades, rolling_metrics

log = logging.getLogger("equitylens.streaming")
BAR_COLUMNS = [*IDENTITY, "interval", "window_start", "window_end", "open", "high", "low", "close", "volume", "vwap", "notional", "trade_count", "last_event_time", "quality"]
METRIC_COLUMNS = [*IDENTITY, "window_start", "window_end", "return_1m_pct", "return_5m_pct", "rolling_volume", "rolling_vwap", "volatility_pct", "volume_ratio", "anomaly", "quality"]


def upsert(connection, table, columns, rows, key):
    # All table/column names are application constants, never user input.
    placeholders = ','.join(['%s'] * len(columns))
    update = ','.join(f'{c}=EXCLUDED.{c}' for c in columns if c not in key)
    sql = f"INSERT INTO {table} ({','.join(columns)}) VALUES ({placeholders}) ON CONFLICT ({','.join(key)}) DO UPDATE SET {update}"
    with connection.cursor() as cursor:
        cursor.executemany(sql, [[row[c] for c in columns] for row in rows])


def wire(value):
    if isinstance(value, Decimal):
        return float(value)
    if hasattr(value, 'isoformat'):
        return value.replace(tzinfo=timezone.utc).isoformat()
    raise TypeError(type(value).__name__)


class Sink:
    def __init__(self, spark, interval):
        self.spark, self.interval = spark, interval
        self.producer = None

    def __call__(self, batch, batch_id):
        batch.persist()
        try:
            # maxOffsetsPerTrigger bounds driver materialization. Aggregates only, never ticks.
            rows = [r.asDict() for r in batch.collect()]
            if not rows:
                return
            with psycopg.connect(os.environ['STREAMING_DATABASE_URL'], row_factory=dict_row, connect_timeout=10, options='-c statement_timeout=30000 -c lock_timeout=5000') as conn:
                upsert(conn, 'realtime_market_bar', BAR_COLUMNS, rows, [*IDENTITY, 'interval', 'window_start'])
                history = []
                if self.interval == '1m':
                    identities = {tuple(r[k] for k in IDENTITY) for r in rows}
                    for identity in identities:
                        relevant = [r for r in rows if tuple(r[k] for k in IDENTITY) == identity]
                        starts = [r['window_start'].replace(tzinfo=timezone.utc) for r in relevant]
                        # At most 21 grid-aligned rows per emitted minute, even when a replay spans years.
                        history.extend(conn.execute("""SELECT DISTINCT b.* FROM unnest(%s::timestamptz[]) AS requested(start)
                            CROSS JOIN LATERAL (
                                SELECT * FROM realtime_market_bar WHERE symbol=%s AND provider=%s AND feed=%s
                                AND synthetic=%s AND interval='1m'
                                AND window_start BETWEEN requested.start - interval '20 minutes' AND requested.start
                                ORDER BY window_start DESC LIMIT 21
                            ) b ORDER BY b.window_start""", (starts, *identity)).fetchall())
                # Commit bars first; failed metric/publish batch is retried from the checkpoint.
                conn.commit()
                if history:
                    # Explicit schema preserves all-null optional fields on sparse histories.
                    schema = 'symbol string, provider string, feed string, synthetic boolean, interval string, window_start timestamp, window_end timestamp, open decimal(24,10), high decimal(24,10), low decimal(24,10), close decimal(24,10), volume decimal(28,6), vwap decimal(24,10), notional decimal(38,16), trade_count long, last_event_time timestamp, quality string'
                    frame = self.spark.createDataFrame([tuple(r[c] for c in BAR_COLUMNS) for r in history], schema)
                    metrics = rolling_metrics(frame)
                    requested = {(tuple(r[k] for k in IDENTITY), r['window_start'].replace(tzinfo=None)) for r in rows}
                    output = [r.asDict() for r in metrics.select(*METRIC_COLUMNS).collect()
                              if (tuple(r[k] for k in IDENTITY), r['window_start'].replace(tzinfo=None)) in requested]
                    upsert(conn, 'realtime_market_analytics', METRIC_COLUMNS, output, [*IDENTITY, 'window_start'])
                    conn.commit()
                    self.publish(output)
                log.info('batch=%s interval=%s aggregates=%s persisted', batch_id, self.interval, len(rows))
        except Exception:
            # Avoid exception text that could contain database URLs or credentials.
            log.error('batch=%s interval=%s failed; checkpoint not committed; retry on restart', batch_id, self.interval)
            raise RuntimeError('Streaming sink failed; inspect database/Kafka availability') from None
        finally:
            batch.unpersist()

    def publish(self, rows):
        if self.producer is None:
            self.producer = KafkaProducer(bootstrap_servers=os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092'),
                                         acks='all', enable_idempotence=True, max_block_ms=10000,
                                         key_serializer=lambda s: s.encode(), value_serializer=lambda v: json.dumps(v, default=wire).encode())
        for row in rows:
            identity = '|'.join(str(row[k]) for k in [*IDENTITY, 'window_start'])
            self.producer.send('equitylens.analytics.market.v1', key=row['symbol'], value={
                'schemaVersion': 1, 'eventType': 'MARKET_ANALYTICS',
                'analyticsKind': 'REFERENCE_STATE' if 'price' in row else 'ROLLING_TRADE_METRICS',
                'eventId': str(uuid.uuid5(uuid.NAMESPACE_URL, identity)), **row}).get(timeout=20)
        self.producer.flush(timeout=20)


class ReferenceSink:
    def __init__(self, spark):
        self.publisher = Sink(spark, 'reference')
    def __call__(self, batch, batch_id):
        try:
            latest = batch.groupBy(*IDENTITY).agg(F.max_by(F.struct('event_time', 'eventId', 'data.price'),
                F.struct('event_time', 'eventTime', 'eventId')).alias('latest')).collect()
            if not latest:
                return
            with psycopg.connect(os.environ['STREAMING_DATABASE_URL'], connect_timeout=10, options='-c statement_timeout=30000 -c lock_timeout=5000') as conn:
                for row in latest:
                    conn.execute("""INSERT INTO realtime_reference_state
                        (symbol,provider,feed,synthetic,event_time,event_id,price) VALUES (%s,%s,%s,%s,%s,%s,%s)
                        ON CONFLICT (symbol,provider,feed,synthetic) DO UPDATE SET event_time=EXCLUDED.event_time,
                        event_id=EXCLUDED.event_id,price=EXCLUDED.price
                        WHERE (EXCLUDED.event_time,EXCLUDED.event_id) >= (realtime_reference_state.event_time,realtime_reference_state.event_id)""",
                        (*[row[k] for k in IDENTITY], row.latest.event_time, row.latest.eventId, row.latest.price))
            self.publisher.publish([{**{k: row[k] for k in IDENTITY}, 'window_start': row.latest.event_time,
                                     'price': row.latest.price} for row in latest])
            log.info('reference batch=%s latest_states=%s persisted', batch_id, len(latest))
        except Exception:
            log.error('reference batch=%s failed; checkpoint not committed', batch_id)
            raise RuntimeError('Reference sink failed; inspect database/Kafka availability') from None



class Progress(StreamingQueryListener):
    def onQueryStarted(self, event):
        log.info('query started name=%s id=%s', event.name, event.id)
    def onQueryProgress(self, event):
        progress = json.loads(event.progress.json)
        log.info('spark_progress=%s', json.dumps({k: progress.get(k) for k in
                 ('name', 'batchId', 'numInputRows', 'inputRowsPerSecond', 'processedRowsPerSecond', 'eventTime', 'stateOperators')}))
    def onQueryTerminated(self, event):
        log.error('query terminated id=%s failure=%s', event.id, bool(event.exception))
    def onQueryIdle(self, event):
        pass


def main():
    # PySpark's Python timestamp conversion also uses the driver's OS timezone.
    os.environ['TZ'] = 'UTC'
    if hasattr(time, 'tzset'):
        time.tzset()
    logging.basicConfig(level=logging.INFO)
    spark = SparkSession.builder.appName('EquityLensMarketAnalytics').config('spark.sql.session.timeZone', 'UTC').getOrCreate()
    spark.sparkContext.setLogLevel('WARN')
    logging.getLogger('py4j').setLevel(logging.WARNING)
    logging.getLogger('kafka').setLevel(logging.WARNING)
    spark.streams.addListener(Progress())
    checkpoint = os.environ.get('SPARK_CHECKPOINT_ROOT', '/var/lib/equitylens/checkpoints')
    watermark = os.environ.get('SPARK_WATERMARK', '2 minutes')
    raw = spark.readStream.format('kafka').option('kafka.bootstrap.servers', os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')) \
        .option('subscribe', 'equitylens.market.trade.v1').option('startingOffsets', 'earliest') \
        .option('failOnDataLoss', 'true').option('maxOffsetsPerTrigger', os.getenv('SPARK_MAX_OFFSETS', '10000')).load()
    parsed = raw.select(F.from_json(F.col('value').cast('string'), EVENT_SCHEMA).alias('e')).select('e.*')
    normalized = normalize_trades(parsed, os.getenv('STREAMING_ALLOW_SYNTHETIC', 'false').lower() == 'true')
    # An independent bounded validation stream reports invalid counts without storing raw ticks.
    def audit(batch, batch_id):
        counts = {r['valid']: r['count'] for r in batch.groupBy('valid').count().collect()}
        log.info('validation batch=%s valid=%s invalid_or_disallowed=%s', batch_id, counts.get(True, 0), counts.get(False, 0))
    queries = [normalized.writeStream.foreachBatch(audit).trigger(processingTime='5 seconds').option('checkpointLocation', checkpoint + '/validation-v1').start()]
    reference_raw = spark.readStream.format('kafka').option('kafka.bootstrap.servers', os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')) \
        .option('subscribe', 'equitylens.market.reference.v1').option('startingOffsets', 'earliest') \
        .option('failOnDataLoss', 'true').option('maxOffsetsPerTrigger', os.getenv('SPARK_MAX_OFFSETS', '10000')).load()
    reference = reference_raw.select(F.from_json(F.col('value').cast('string'), EVENT_SCHEMA).alias('e')).select('e.*') \
        .withColumn('event_time', F.try_to_timestamp('eventTime')) \
        .withColumn('synthetic', F.coalesce('sourceMetadata.synthetic', F.lit(False)))
    reference = reference.filter((F.col('schemaVersion') == 1) & (F.col('eventType') == 'REFERENCE_PRICE') &
        F.col('symbol').rlike('^[A-Z0-9][A-Z0-9.-]{0,9}$') & (F.col('data.price') > 0) &
        F.col('event_time').isNotNull() & F.col('eventId').isNotNull() & F.col('provider').isNotNull() & F.col('feed').isNotNull() & ((F.col('provider') != 'SYNTHETIC') | F.col('synthetic')))
    if os.getenv('STREAMING_ALLOW_SYNTHETIC', 'false').lower() != 'true':
        reference = reference.filter(~F.col('synthetic'))
    queries.append(reference.writeStream.foreachBatch(ReferenceSink(spark))
                   .option('checkpointLocation', checkpoint + '/reference-v1').trigger(processingTime='5 seconds').start())
    trades = normalized.filter('valid').withWatermark('event_time', watermark) \
        .dropDuplicatesWithinWatermark(['provider', 'feed', 'synthetic', 'eventId'])
    for interval, duration in [('1m', '1 minute'), ('5m', '5 minutes')]:
        queries.append(aggregate(trades, duration).writeStream.outputMode('append')
                       .queryName('market-' + interval).foreachBatch(Sink(spark, interval))
                       .option('checkpointLocation', checkpoint + '/' + interval + '-v1')
                       .trigger(processingTime='5 seconds').start())
    try:
        spark.streams.awaitAnyTermination()
    finally:
        for query in queries:
            query.stop()
        spark.stop()


if __name__ == '__main__':
    main()
