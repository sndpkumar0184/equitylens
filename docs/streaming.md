# Optional streaming subsystem

Status: under development. Kafka/Spark processing, provider adapters, persistence, and live UI code are present. This does not establish a production deployment or successful operation with every entitled live feed.

## Pipeline

Alpaca and Tiingo WebSocket adapters sit behind a real-time provider interface. Canonical trade, quote, bar, and reference topics use versioned names under `equitylens.market.*.v1`; derived notifications use `equitylens.analytics.market.v1`. Spark consumes trade/reference streams for the implemented analytics path. Publishing quotes/bars does not imply every topic is used to calculate trade metrics.

Spark computes observed trade OHLCV/VWAP for one- and five-minute windows and persists rolling analytics/reference state through parameterized PostgreSQL operations. Watermarks, event identity, and checkpoints bound late-data handling and duplicate processing. Incomplete warm-up windows return missing metrics. Provider/feed identity and synthetic flags keep sources distinct; Tiingo reference updates must not be presented as complete trade-volume data.

Database upserts are replay-safe, but the PostgreSQL sink and Kafka notification are not an atomic transaction. Operational recovery and source-specific entitlement checks need continued validation.

## Development setup

1. Configure the root `.env` and start PostgreSQL using the development guide.
2. Start the backend once to initialize the realtime tables before starting Spark.
3. Copy `.env.realtime.example` to an ignored `.env.realtime` and configure provider credentials and matching database settings. Use placeholders only in shared examples.
4. Start the optional overlay:

```bash
docker compose --env-file .env --env-file .env.realtime \
  -f docker-compose.yml -f docker-compose.realtime.yml up -d
```

The realtime example is a Compose environment file. Do not directly source it into a shell: values such as the watermark contain spaces. Export the required backend settings explicitly, or load them using an environment-file-aware launcher. `REALTIME_ENABLED=true` enables provider ingestion; `REALTIME_LIVE_ENABLED=true` enables derived notifications. Set explicit symbols and choose matching analytics provider/feed. Configure the backend Kafka address as `localhost:9092`; containers use their internal Kafka address.

Set `STREAMING_DATABASE_URL` to the container-network PostgreSQL connection with the actual local credentials. This is server-only configuration. Synthetic replay requires both an explicit replay opt-in and matching synthetic analytics settings; it is never live-market evidence.

The overlay runs a single Kafka broker and Spark `local[2]`, with durable checkpoint storage. It is intended for development, not a multi-node production cluster.

## Kafka Development UI

[Kafbat UI](https://github.com/kafbat/kafka-ui) is a local developer inspection
tool, separate from the EquityLens Next.js UI. The realtime overlay automatically
starts `ghcr.io/kafbat/kafka-ui:v1.5.0` at **http://localhost:8081** with the cluster
**EquityLens Local** preconfigured. Port 8081 is bound only to `127.0.0.1`.
Kafbat uses `kafka:29092` on Compose's existing default network; host applications
use `localhost:9092`. No provider or database credentials are passed to Kafbat.

The UI is read-only, with dynamic configuration disabled and a 384 MiB JVM heap
limit. Kafka health gates its startup; no backend, Spark, or frontend service
depends on Kafbat. It may be stopped independently:

```bash
docker compose -f docker-compose.yml -f docker-compose.realtime.yml stop kafbat-ui
```

Inspection workflow:

1. Start local infrastructure using the overlay command above.
2. Start/verify the configured realtime backend provider. Confirm its ingestion
   state and matching provider/feed in `/api/market/realtime/{symbol}`. A connected
   browser SSE session does not prove a provider is ingesting data.
3. Open http://localhost:8081 and select **EquityLens Local**.
4. Open **Topics**, then `equitylens.market.trade.v1`.
5. Open **Partitions** to inspect partition ID, leader, first/beginning offset,
   next/end offset, and replica status. Development defaults to four partitions,
   replication factor one. End offsets are the next record position, not an
   inclusive last-record offset or a count of currently retained messages.
6. Open **Messages**, choose partitions and an earliest/latest/offset seek, and
   expand a record. Inspect its key and JSON value; use String key decoding and
   JSON/String value decoding. The producer key remains the normalized symbol
   (`AAPL`, `AMZN`, `META`, `NVDA`, etc.).
7. Confirm `schemaVersion`, `eventType`, `symbol`, `eventTime`, `provider`, `feed`
   and `sourceMetadata`. Trade fields are inside `data` (`price`, `quantity`).
   Alpaca/Tiingo protocol payloads and credentials do not belong in these events.
8. Inspect quote/bar/reference topics and `equitylens.analytics.market.v1` in the
   same way. Empty topics are legitimate: provider capabilities and configuration
   determine which events exist. Expired events cannot be viewed after retention.
9. Open **Consumers** to inspect `equitylens-live-<instance-id>` groups, committed
   offsets and per-partition/total lag. These Spring consumers notify SSE clients
   after Spark persists analytics; they do not perform SEC ingestion.
10. Open http://localhost:4040 → **Structured Streaming** for actual Spark query
    progress, batch input, watermark/state and jobs. Spark uses separate durable
    checkpoint offsets under `SPARK_CHECKPOINT_ROOT`, not conventional committed
    Kafka group offsets. Its default generated Kafka identities are not a stable
    long-lived group with meaningful Kafbat lag. Do not interpret absent Spark
    groups or zero new jobs on a caught-up stream as proof it is disconnected.

For markets-closed development, use the existing explicit synthetic replay path
documented in [realtime architecture](realtime-market-intelligence.md). Synthetic
events display `provider=SYNTHETIC` and their actual feed, never ALPACA/TIINGO.
Only enable this in the synthetic development pipeline. The UI does not create
sample messages automatically or change partitioning/checkpoints.

## Tests

The Python tests use deterministic synthetic fixtures and real Spark aggregation. Run them in the Spark development image from the repository root:

```bash
docker compose -f docker-compose.yml -f docker-compose.realtime.yml build spark
docker compose -f docker-compose.yml -f docker-compose.realtime.yml \
  run --rm --no-deps -v "$PWD/streaming/tests:/opt/equitylens/tests:ro" \
  spark /opt/equitylens/tests/test_analytics.py
```

Fixtures are supplied by the image under `/opt/equitylens/fixtures`; this is a container path. Replay utilities in `streaming/replay.py` require `--allow-synthetic`. Test results belong in [verification](verification.md), separately from any live-feed validation.
