import json
import math
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path
from pyspark.sql import SparkSession, functions as F
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from analytics import EVENT_SCHEMA, normalize_trades, aggregate, rolling_metrics


class AnalyticsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spark = SparkSession.builder.master('local[2]').appName('EquityLensFixtureTests') \
            .config('spark.sql.shuffle.partitions', '2').config('spark.sql.session.timeZone', 'UTC').getOrCreate()
        cls.spark.sparkContext.setLogLevel('ERROR')
        cls.lines = Path('/opt/equitylens/fixtures/trades.jsonl').read_text().splitlines()
    @classmethod
    def tearDownClass(cls):
        cls.spark.stop()
    def trades(self):
        return normalize_trades(self.spark.read.schema(EVENT_SCHEMA).json(self.spark.sparkContext.parallelize(self.lines)), True).filter('valid') \
            .dropDuplicates(['provider', 'feed', 'synthetic', 'eventId'])
    def test_ohlcv_vwap_order_duplicates_and_four_symbols(self):
        trades = self.trades()
        minute = aggregate(trades, '1 minute')
        row = minute.filter("symbol='AAPL' AND window_start='2026-09-30 14:00:00'").first()
        self.assertEqual((100, 102, 100, 101, 30, 3), (row.open, row.high, row.low, row.close, row.volume, row.trade_count))
        self.assertAlmostEqual(101, float(row.vwap), 8)
        five = aggregate(trades, '5 minutes').filter("symbol='AAPL' AND window_start='2026-09-30 14:00:00'").first()
        self.assertEqual((100, 106, 100, 105, 150), (five.open, five.high, five.low, five.close, five.volume))
        self.assertAlmostEqual(103, float(five.vwap), 8)
        self.assertEqual({'AAPL','META','AMZN','NVDA'}, {r.symbol for r in minute.select('symbol').distinct().collect()})
        self.assertEqual(31 * 4, minute.count())  # Empty windows aren't invented.
    def test_returns_rolling_metrics_warmup_gap_and_anomaly(self):
        metrics = rolling_metrics(aggregate(self.trades(), '1 minute'))
        row = metrics.filter("symbol='AAPL' AND window_start='2026-09-30 14:25:00'").first()
        self.assertAlmostEqual((126/125-1)*100, float(row.return_1m_pct), 7)
        self.assertAlmostEqual((126/121-1)*100, float(row.return_5m_pct), 7)
        self.assertEqual(420, row.rolling_volume)
        self.assertAlmostEqual((122*30+123*30+124*30+125*30+126*300)/420, float(row.rolling_vwap), 7)
        self.assertAlmostEqual(10, float(row.volume_ratio), 7)
        self.assertTrue(row.anomaly)
        returns = [math.log(x/(x-1)) for x in range(122,127)]
        mean = sum(returns)/5
        expected = 100*math.sqrt(sum((r-mean)**2 for r in returns)/4)
        self.assertAlmostEqual(expected, row.volatility_pct, 8)
        warmup = metrics.filter("symbol='AAPL' AND window_start='2026-09-30 14:00:00'").first()
        self.assertIsNone(warmup.return_1m_pct);self.assertIsNone(warmup.volume_ratio);self.assertIsNone(warmup.rolling_volume)
        gap = metrics.filter("symbol='AAPL' AND window_start='2026-09-30 14:35:00'").first()
        self.assertIsNone(gap.return_1m_pct);self.assertIsNone(gap.return_5m_pct);self.assertIsNone(gap.volatility_pct)
    def test_validation_version_missing_and_synthetic_opt_in(self):
        fixture = self.spark.read.schema(EVENT_SCHEMA).json(self.spark.sparkContext.parallelize(self.lines[:3]))
        self.assertEqual(0, normalize_trades(fixture).filter('valid').count())
        bad = [json.loads(self.lines[0]) for _ in range(5)]
        bad[0]['schemaVersion']=2;bad[1]['data']['price']=None;bad[2]['eventTime']='bad';bad[3]['data']['quantity']=-1;bad[4]['sourceMetadata']={}
        df = self.spark.read.schema(EVENT_SCHEMA).json(self.spark.sparkContext.parallelize([json.dumps(r) for r in bad]))
        self.assertEqual(0, normalize_trades(df,True).filter('valid').count())
    def test_structured_streaming_lateness_duplicate_disorder_and_restart(self):
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'input';source.mkdir(); checkpoint=str(Path(temp)/'checkpoint'); output=str(Path(temp)/'output')
            stream=normalize_trades(self.spark.readStream.schema(EVENT_SCHEMA).json(str(source)),True).filter('valid') \
                .withWatermark('event_time','2 minutes').dropDuplicatesWithinWatermark(['provider','feed','synthetic','eventId'])
            def start():
                return aggregate(stream,'1 minute').writeStream.format('parquet').outputMode('append') \
                    .option('path',output).option('checkpointLocation',checkpoint).start()
            q=start()
            # First 2 minutes, duplicate and out of order from fixture.
            initial=[line for line in self.lines if json.loads(line)['symbol']=='AAPL' and json.loads(line)['eventTime']<'2026-09-30T14:02:00+00:00']
            (source/'1.json').write_text('\n'.join(initial));q.processAllAvailable()
            future=json.loads(initial[0]);future.update(eventId='advance-1',eventTime='2026-09-30T14:05:00+00:00')
            (source/'2.json').write_text(json.dumps(future));q.processAllAvailable();q.stop()
            before=self.spark.read.parquet(output);self.assertEqual(2,before.count())
            self.assertEqual(30,before.filter("window_start='2026-09-30 14:00:00'").first().volume)
            # Late historical event after persisted watermark must not reopen finalized bars.
            late=json.loads(initial[0]);late['eventId']='too-late';late['data']['quantity']=9999
            future.update(eventId='advance-2',eventTime='2026-09-30T14:08:00+00:00')
            (source/'3.json').write_text(json.dumps(late)+'\n'+json.dumps(future))
            q=start();q.processAllAvailable();q.stop()
            after=self.spark.read.parquet(output)
            self.assertEqual(30,after.filter("window_start='2026-09-30 14:00:00'").first().volume)
            self.assertEqual(1,after.filter("window_start='2026-09-30 14:00:00'").count())


if __name__ == '__main__':
    unittest.main()
