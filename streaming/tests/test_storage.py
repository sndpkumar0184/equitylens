"""Real PostgreSQL + Kafka sink retry verification. Only unique test-source rows are cleaned up."""
import json
import os
import sys
import unittest
import uuid
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import psycopg
from psycopg.rows import dict_row
from pyspark.sql import SparkSession
from analytics import EVENT_SCHEMA, normalize_trades, aggregate
from job import Sink, ReferenceSink

class StorageTest(unittest.TestCase):
    def test_real_sink_idempotence_metrics_indexes_and_feed_isolation(self):
        spark = SparkSession.builder.master('local[2]').appName('EquityLensStorageTest').config('spark.sql.shuffle.partitions','2').getOrCreate()
        spark.sparkContext.setLogLevel('ERROR')
        feed = 'TEST_' + uuid.uuid4().hex.upper()
        lines = []
        for line in Path('/opt/equitylens/fixtures/trades.jsonl').read_text().splitlines():
            row=json.loads(line);row['feed']=feed;lines.append(json.dumps(row))
        trades=normalize_trades(spark.read.schema(EVENT_SCHEMA).json(spark.sparkContext.parallelize(lines)),True).filter('valid').dropDuplicates(['provider','feed','synthetic','eventId'])
        bars=aggregate(trades,'1 minute')
        sink=Sink(spark,'1m')
        five_sink=Sink(spark,'5m')
        reference_sink=ReferenceSink(spark)
        try:
            sink(bars,0);sink(bars,0)
            five=aggregate(trades,'5 minutes');five_sink(five,0);five_sink(five,0)
            reference=json.loads(lines[0]);reference.update(eventType='REFERENCE_PRICE', eventId='reference-latest',eventTime='2026-09-30T14:40:00+00:00',data={'price':123.5})
            refs=spark.read.schema(EVENT_SCHEMA).json(spark.sparkContext.parallelize([json.dumps(reference)]))
            from pyspark.sql import functions as F
            def reference_frame(df): return df.withColumn('event_time',F.to_timestamp('eventTime')).withColumn('synthetic',F.col('sourceMetadata.synthetic'))
            reference_sink(reference_frame(refs),0)
            reference.update(eventId='reference-older',eventTime='2026-09-30T14:39:00+00:00',data={'price':50})
            older=spark.read.schema(EVENT_SCHEMA).json(spark.sparkContext.parallelize([json.dumps(reference)]))
            reference_sink(reference_frame(older),1)
            with psycopg.connect(os.environ['STREAMING_DATABASE_URL'],row_factory=dict_row) as conn:
                count=conn.execute('SELECT count(*) AS n FROM realtime_market_bar WHERE feed=%s',(feed,)).fetchone()['n']
                self.assertEqual(152,count)
                bar=conn.execute("SELECT * FROM realtime_market_bar WHERE feed=%s AND symbol='AAPL' AND interval='5m' AND window_start='2026-09-30 14:00:00+00'",(feed,)).fetchone()
                self.assertEqual((100,106,100,105,150),(bar['open'],bar['high'],bar['low'],bar['close'],bar['volume']))
                reference=conn.execute('SELECT price FROM realtime_reference_state WHERE feed=%s',(feed,)).fetchone()
                self.assertEqual(123.5,reference['price'])
                metric=conn.execute("SELECT * FROM realtime_market_analytics WHERE feed=%s AND symbol='AAPL' AND window_start='2026-09-30 14:25:00+00'",(feed,)).fetchone()
                self.assertAlmostEqual(10,float(metric['volume_ratio']),8);self.assertTrue(metric['anomaly'])
                self.assertEqual(420,metric['rolling_volume']);self.assertAlmostEqual(125.2857142857,float(metric['rolling_vwap']),8)
                mcount=conn.execute('SELECT count(*) AS n FROM realtime_market_analytics WHERE feed=%s',(feed,)).fetchone()['n']
                self.assertEqual(124,mcount)
                indexes={r['indexname'] for r in conn.execute("SELECT indexname FROM pg_indexes WHERE tablename IN ('realtime_market_bar','realtime_market_analytics')").fetchall()}
                self.assertTrue({'realtime_market_bar_pkey','realtime_market_analytics_pkey','realtime_anomaly_recent'}.issubset(indexes))
        finally:
            if sink.producer is not None:sink.producer.close()
            if reference_sink.publisher.producer is not None:reference_sink.publisher.producer.close()
            with psycopg.connect(os.environ['STREAMING_DATABASE_URL']) as conn:
                conn.execute('DELETE FROM realtime_market_analytics WHERE feed=%s',(feed,))
                conn.execute('DELETE FROM realtime_market_bar WHERE feed=%s',(feed,))
                conn.execute('DELETE FROM realtime_reference_state WHERE feed=%s',(feed,))
            spark.stop()

if __name__=='__main__': unittest.main()
