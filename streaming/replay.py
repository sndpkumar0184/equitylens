"""Explicit synthetic-only canonical replay. No live credentials; fixed application topic."""
import argparse
import json
import os
import time
import uuid
from datetime import datetime, timezone, timedelta
from pathlib import Path
from kafka import KafkaProducer


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--allow-synthetic', action='store_true', required=True)
    parser.add_argument('--fixture', default=str(Path(__file__).parent / 'fixtures/trades.jsonl'))
    parser.add_argument('--current-time', action='store_true', help='Shift fixture into the last hour, preserving duplicates and relative ordering')
    parser.add_argument('--delay', type=float, default=0)
    args = parser.parse_args()
    rows = [json.loads(line) for line in Path(args.fixture).read_text().splitlines() if line.strip()]
    if any(r.get('schemaVersion') != 1 or r.get('eventType') != 'TRADE' or
           r.get('provider') != 'SYNTHETIC' or r.get('sourceMetadata', {}).get('synthetic') is not True for r in rows):
        raise ValueError('Replay requires v1 explicitly SYNTHETIC trades')
    shift = timedelta(0)
    if args.current_time:
        shift = datetime.now(timezone.utc).replace(second=0, microsecond=0) - timedelta(minutes=40) - min(datetime.fromisoformat(r['eventTime']) for r in rows).replace(second=0, microsecond=0)
    producer = KafkaProducer(bootstrap_servers=os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092'),
                             acks='all', enable_idempotence=True, key_serializer=lambda s: s.encode(),
                             value_serializer=lambda r: json.dumps(r).encode())
    for row in rows:
        row['eventTime'] = (datetime.fromisoformat(row['eventTime']) + shift).isoformat()
        if args.current_time:
            row['eventId'] = str(uuid.uuid5(uuid.NAMESPACE_URL, row['eventId'] + '|' + row['eventTime']))
        row['receivedAt'] = datetime.now(timezone.utc).isoformat()
        producer.send('equitylens.market.trade.v1', key=row['symbol'], value=row).get(timeout=20)
        if args.delay:
            time.sleep(args.delay)
    producer.flush(timeout=20)
    producer.close()
    print(f'Published {len(rows)} explicitly synthetic canonical trades')


if __name__ == '__main__':
    main()
