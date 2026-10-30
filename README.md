# MysterriaReporter

HTTP endpoints over CatWalk for Circle of Imagination data: beyonder pathways, raw data files, activity logs, the balance
export, and admin bless/rebirth actions.

## Requirements

- Paper 26.3, Java 25
- CatWalk 0.9+ (Javalin 7)
- CircleOfImagination with its API registered

## Endpoints

| Method | Path | Scope | Returns |
|---|---|---|---|
| GET | `/pathway/single/{player}` | `reporter.read` | `{playerName, beyonder, pathway, sequence, acting}`; `beyonder=false` when the player has no pathway |
| GET | `/pathway/everyone` | `reporter.read` | `{amount, beyonder: [...], generatedAt}`; 503 while the first roster is still being built |
| GET | `/beyonder/{player}` | `reporter.beyonder` | `{player, data, found}`, `data` is the raw YAML; 404 when there is no file |
| GET | `/beyonder/logs/{player}/{amount}` | `reporter.logs` | `{player, requested, returned, logs, found}`; flushes the log first; `amount` 1-10000 |
| GET | `/artifact/{id}` | `reporter.beyonder` | `{artifactId, data, found}`; 404 when there is no file |
| GET | `/balance/report` | `reporter.read` | the latest balance export JSON; 404 when none exists |
| POST | `/bless` | `reporter.admin` | `{playerName, uuid, pathway, sequence, spiritualityGranted, actingGranted}` |
| POST | `/rebirth` | `reporter.admin` | the request back |

The master CatWalk key passes every scope. Every response uses CatWalk's `{success, message, data}` envelope and the HTTP
status carries the outcome: 400 invalid input, 404 missing data or endpoint disabled in `config.yml`, 409 bless of an
existing Beyonder or rebirth of a non-Beyonder, 500 when CircleOfImagination refuses the change.

Bless and rebirth run on the server thread and answer with the real result. Bless validates the pathway against
CircleOfImagination, the sequence (0-9) and the boosts (not negative; defaults 200 spirituality, 50 acting).

## Configuration

```yaml
cache:
  player-ttl: 5          # minutes
  refresh-interval: 15   # minutes, 0 disables the background refresh
  max-player-entries: 1000
endpoints:               # false answers 404
  player-pathway: true
  everyone-pathway: true
  beyonder-data: true
  beyonder-logs: true
  artifact: true
  balance-report: true
  bless: true
  rebirth: true
logging:
  debug-cache: false
  debug-refresh: false
```

## Commands

`/reporter refresh | clear [player] | stats` (aliases `mr`, `pathwaycache`), permission `mysterriareporter.cache`.

## Development

CatWalk 0.9 is not on JitPack yet; publish it locally first with `./gradlew publishToMavenLocal` in the catwalk repo,
then `./gradlew build`.
