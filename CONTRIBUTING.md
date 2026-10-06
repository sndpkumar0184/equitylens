# Contributing to EquityLens

Keep changes focused and describe the behavior they affect. Follow the [development guide](docs/development.md) to run the project and its checks.

## Design conventions

- Keep controllers responsible for HTTP input/output and application services responsible for behavior.
- Reuse repositories and parameterized queries; use DTOs for public responses rather than exposing persistence entities.
- Centralize financial normalization and calculations. Preserve reporting periods, units, and available source metadata.
- Implement market integrations through provider interfaces and represent unsupported capabilities explicitly.
- Keep REST dashboard access independent of MCP and local model availability.
- Validate and bound external inputs. Preserve missing-data and provider-error behavior.
- Keep credentials in ignored environment files; examples contain placeholders only.

## Validation

Backend changes use Java 21 and the Gradle wrapper. Run `./gradlew test build` in `backend`. Frontend changes should pass lint, Vitest, TypeScript validation, and a production build using the commands in the root README. For streaming changes, run the Spark tests described in the streaming guide and document any live-provider checks separately from synthetic replay.

Include meaningful regression coverage for behavior changes. Document environment-dependent failures accurately and avoid unrelated refactoring.
