# Repository verification

Checks executed during documentation and public-repository cleanup, October 2, 2026. These validate the current working checkout, which includes pre-existing, uncommitted feature work. No commit or push was performed.

| Check | Result |
| --- | --- |
| Backend `./gradlew test build` | Passed: 113 tests, zero failures/errors/skips; artifacts built |
| Frontend `npm run lint` | Passed |
| Frontend `npm test` | Passed: 38 tests across 9 files |
| Frontend `npm exec tsc -- --noEmit` | Passed |
| Frontend `npm run build` | Blocked by Turbopack worker-port permission error, including retry |
| Frontend `npm run build -- --webpack` | Passed outside the restricted environment; routes compiled and pages generated |
| Compose configuration | Base and realtime overlay parsed successfully |
| Spark fixture tests through `spark-submit` | 4 executed: 3 passed, 1 failed |
| Relative Markdown links | Checked against files in the checkout |
| Source/test preservation | No Java, TypeScript, Python, or SQL files changed by this cleanup |

## Spark failure

`test_returns_rolling_metrics_warmup_gap_and_anomaly` expects seven-place agreement for a rolling VWAP. The actual value is rounded to six decimal places; the difference is approximately 2.86 × 10⁻⁷. This existing assertion failed. Application calculations and tests were left unchanged because this work is limited to repository cleanup.

The initial direct Python invocation could not import the image's bundled PySpark; the documented command now uses `spark-submit`. Fixture tests are synthetic and do not establish live-provider operation.

## Repository review

Removed internal agent instructions and retained useful engineering guidance in public development/contribution documentation. Existing screenshots were retained. Examples contain placeholders or identified development-only defaults, and real environment files remain ignored.

Token/private-key pattern scans found no matches in current tracked text files or the eight Git commits inspected. No real environment/credential files are tracked. This is a scoped review, not a guarantee against every possible secret format. Local IDE files and empty private tooling directories remain ignored.

The complete working diff includes pre-existing feature changes. Default whitespace checking flags their CRLF lines; treating CRLF as line endings resolves these warnings. Cleanup documentation/configuration changes introduce no whitespace errors.

Historical [market verification](market-data-verification.md) and [assistant verification](ai-assistant-verification.md) are retained as dated subsystem records. No fresh browser, live-model, or entitled live-feed verification was performed during this cleanup.
