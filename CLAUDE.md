# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MysterriaReporter is a Paper Minecraft plugin that provides HTTP API endpoints for querying beyonder pathway data from the Circle of Imagination plugin. It uses CatWalk for web serving and Caffeine for high-performance caching.

## Build and Development Commands

### Building
```bash
./gradlew clean build
```
Built JAR: `build/libs/MysterriaReporter-{version}.jar`

### Running Test Server
```bash
./gradlew runServer
```
Launches a Paper 1.21 test server with the plugin automatically loaded.

### Clean Build Directory
```bash
./gradlew clean
```

## Architecture

### Plugin Lifecycle
The main class `MysterriaReporter` (extends JavaPlugin) coordinates:
1. **Dependency Loading**: Hooks into CircleOfImagination API via Bukkit ServicesManager
2. **Cache Initialization**: Creates `PathwayCacheManager` with Caffeine-based caching
3. **Endpoint Registration**: Registers HTTP handlers with CatWalkWebserverService
4. **Command Registration**: Registers `/reporter` command for cache management
5. **Shutdown**: Cancels refresh tasks and clears caches

### Core Components

**PathwayCacheManager** (`manager/PathwayCacheManager.java`)
- Manages two-tier caching: individual player cache and bulk "everyone" cache
- Player cache: Caffeine cache with configurable TTL and max entries
- Everyone cache: In-memory single object, refreshed asynchronously on timer
- Background refresh: BukkitTask runs async every `refresh-interval` minutes
- Thread safety: All cache operations are thread-safe via Caffeine

**PathwayUtil** (`util/PathwayUtil.java`)
- Static utility methods for pathway calculations
- Primary pathway selection: Finds pathway with lowest sequence number (lower = more powerful)
- Acting progress: Calculates `current_acting / needed_acting` as decimal (0.0-1.0)
- Data fetching: Interfaces with CircleOfImagination API

**Endpoint Structure** (`api/` package)
- Uses CatWalk annotations: `@BridgeEventHandler` for routing
- `PlayerPathwayEndpoint`: GET `/pathway/single/{player}` - single player data
- `EveryonePathwayEndpoint`: GET `/pathway/everyone` - all beyonders
- All endpoints return `BridgeApiResponse` wrapper with JSON payloads
- No authentication required (`requiresAuth = false`)

**Models** (`model/` package)
- `PlayerPathwayResponse`: Player data (name, beyonder status, pathway, sequence, acting)
- `EveryonePathwayResponse`: Bulk data (amount, list of beyonders)
- Uses Lombok `@Builder` pattern

### Caching Strategy

1. **Player Cache (Caffeine)**:
   - TTL-based expiration (`player-ttl` minutes)
   - Size-limited (`max-player-entries`)
   - Populated on-demand (cache miss triggers API call)
   - Also populated during everyone cache refresh

2. **Everyone Cache (Single Object)**:
   - Loaded asynchronously on startup via `CompletableFuture.runAsync()`
   - Refreshed periodically by BukkitTask (async scheduler)
   - Scans all `OfflinePlayer[]` and checks `coiAPI.isBeyonder()`
   - Stores complete list in memory

3. **Cache Invalidation**:
   - `/reporter clear` - clears all caches
   - `/reporter clear <player>` - clears specific player
   - `/reporter reload` - reloads config and invalidates all caches
   - Manual refresh: `/reporter refresh` triggers immediate everyone cache update

## Configuration System

Config loaded from `config.yml` via Bukkit FileConfiguration:
- `cache.player-ttl`: Minutes to cache individual player lookups
- `cache.everyone-ttl`: (Currently unused in code - everyone cache has no TTL)
- `cache.refresh-interval`: Minutes between automatic everyone cache refreshes
- `cache.max-player-entries`: Max player cache size
- `endpoints.player-pathway`: Enable/disable player endpoint
- `endpoints.everyone-pathway`: Enable/disable everyone endpoint
- `logging.debug-cache`: Log cache hits/misses
- `logging.debug-refresh`: Log refresh task execution

## Dependencies

### Hard Dependencies (plugin.yml)
- **CatWalk**: Web server framework, provides `CatWalkWebserverService`
- **CircleOfImagination**: Beyonder pathway system, provides `CircleOfImaginationAPI`

Both must be present and enabled or plugin disables itself.

### External APIs
- **CircleOfImagination API** (`dev.ua.ikeepcalm:circle-of-imagination-api`):
  - `isBeyonder(String playerName)`: Check if player is beyonder
  - `getBeyonderData(String playerName)`: Get full beyonder data
  - `getPathways(String playerName)`: Get Map of pathway names to sequence levels
  - `BeyonderData.getPathway(String name)`: Get specific pathway data
  - `PathwayData.lowestSequenceLevel()`: Get current sequence
  - `PathwayData.acting()` / `neededActing()`: Acting progress values

- **CatWalk Bridge** (`com.github.ikeepcalm:catwalk`):
  - `@BridgeEventHandler`: Marks methods as HTTP handlers
  - `@BridgePathParam`: Binds path variables to parameters
  - `BridgeApiResponse.success()`: Wraps responses in standard format

## Common Patterns

### Adding New Endpoints
1. Create class in `net.mysterria.reporter.api`
2. Inject `PathwayCacheManager` via constructor
3. Add method with `@BridgeEventHandler` annotation
4. Add `@OpenApi` annotation for Swagger documentation
5. Register in `MysterriaReporter.registerEndpoints()` with config toggle
6. Return `BridgeApiResponse.success(data)` or `BridgeApiResponse.error()`

### Accessing CircleOfImagination Data
Always go through `PathwayUtil` utility methods or `PathwayCacheManager` to leverage caching. Direct API access bypasses cache.

### Async Operations
- Everyone cache refresh uses `CompletableFuture.runAsync()`
- Background tasks use `Bukkit.getScheduler().runTaskTimerAsynchronously()`
- Never block main thread for cache operations

## Deployment

CI/CD via GitHub Actions (`.github/workflows/deploy.yml`):
- Triggered on push to `main` or `production` branches
- Builds with Gradle
- Extracts plugin name from `src/main/resources/plugin.yml`
- Deploys to Pelican panel servers configured in external `minecraft-deployment-config` repo
- Removes old JAR versions matching plugin name
- Uploads new JAR and deployment manifest
- Sends Discord webhook notifications for build/deploy status

**Important**: Deployment targets configured externally in `servers.json`, not in this repository.

## Testing Locally

1. Build plugin: `./gradlew clean build`
2. Copy JAR from `build/libs/` to test server `plugins/` folder
3. Ensure CatWalk and CircleOfImagination plugins are present
4. Start server and check console for:
   - `CircleOfImagination API hooked successfully`
   - `Registered PlayerPathwayEndpoint at /pathway/single/{player}`
   - `Registered EveryonePathwayEndpoint at /pathway/everyone`
   - `Started cache refresh task - interval: X minutes`
5. Test endpoints: `curl http://localhost:8080/pathway/single/PlayerName`

## Troubleshooting

- **Plugin won't enable**: Check CircleOfImagination and CatWalk are loaded first
- **Endpoints not registered**: Check `endpoints.*` config values, verify CatWalk is running
- **Cache not refreshing**: Verify `refresh-interval > 0`, check `debug-refresh: true` logs
- **High memory**: Lower `max-player-entries` or `player-ttl` to reduce cache size
