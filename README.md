# MysterriaReporter

Automated beyonder pathway reporting system for the Mysterria Minecraft server. Provides HTTP API endpoints to query player pathway status, sequences, and acting progress from the Circle of Imagination plugin.

## Features

- **Player Pathway Queries**: Get individual player beyonder status and progression
- **Bulk Beyonder Data**: Retrieve all beyonders and their pathways in a single request
- **Intelligent Caching System**:
  - Configurable TTL for player and bulk queries
  - Automatic background cache refresh
  - Reduces API calls to Circle of Imagination
  - Memory-efficient with size limits
- **Admin Commands**: Manage cache, view statistics, and reload configuration
- **RESTful API**: Clean JSON responses via CatWalk web server

## Dependencies

- **Paper 1.21+** - Minecraft server platform
- **CatWalk** - Web server framework for HTTP endpoints
- **CircleOfImagination** - Beyonder pathway system
- **Caffeine** - High-performance caching library

## Installation

1. Place the plugin JAR in your `plugins` folder
2. Ensure CatWalk and CircleOfImagination are installed and running
3. Start the server to generate `config.yml`
4. Configure cache settings as needed
5. Reload or restart the server

## API Endpoints

All endpoints return JSON responses and do not require authentication.

### Get Single Player Pathway

```
GET /pathway/single/{player}
```

Returns the primary pathway information for a specific player.

**Path Parameters:**
- `player` - Player's username

**Response:**
```json
{
  "player": "PlayerName",
  "beyonder": true,
  "pathway": "Sun",
  "sequence": "5",
  "acting": "0.75"
}
```

**Fields:**
- `player` - Player's username
- `beyonder` - Whether the player is a beyonder (true/false)
- `pathway` - Primary pathway name (lowest sequence)
- `sequence` - Current sequence level (0-9, lower = more powerful)
- `acting` - Acting progress as decimal (0.0 - 1.0)

**Example:**
```bash
curl http://localhost:8080/pathway/single/Steve
```

### Get All Beyonders

```
GET /pathway/everyone
```

Returns pathway information for all beyonders on the server.

**Response:**
```json
{
  "amount": 42,
  "beyonders": [
    {
      "player": "Player1",
      "beyonder": true,
      "pathway": "Moon",
      "sequence": "7",
      "acting": "0.5"
    },
    {
      "player": "Player2",
      "beyonder": true,
      "pathway": "Sun",
      "sequence": "6",
      "acting": "0.85"
    }
  ]
}
```

**Fields:**
- `amount` - Total number of beyonders
- `beyonders` - Array of player pathway data

**Example:**
```bash
curl http://localhost:8080/pathway/everyone
```

## Configuration

### config.yml

```yaml
# MysterriaReporter Configuration

cache:
  # How long to cache individual player pathway data (in minutes)
  # Set to 0 to disable caching for single player queries
  player-ttl: 5

  # How long to cache the everyone/bulk pathway data (in minutes)
  # Set to 0 to disable caching for bulk queries
  everyone-ttl: 10

  # How often to refresh the everyone cache in the background (in minutes)
  # This ensures the bulk data is always relatively fresh
  # Set to 0 to disable automatic refresh
  refresh-interval: 15

  # Maximum number of individual player entries to cache
  # Prevents memory issues on large servers
  max-player-entries: 1000

endpoints:
  # Enable or disable specific endpoints
  player-pathway: true
  everyone-pathway: true

logging:
  # Log cache hit/miss for debugging
  debug-cache: false

  # Log when cache refresh tasks run
  debug-refresh: false
```

### Cache Configuration Explained

- **player-ttl**: How long to remember individual player lookups before re-fetching
- **everyone-ttl**: How long the bulk beyonder list stays valid
- **refresh-interval**: Background task frequency to keep bulk data fresh
- **max-player-entries**: Limits memory usage by capping cached players

**Recommended Settings:**
- Small servers (< 50 players): `player-ttl: 5`, `everyone-ttl: 10`, `refresh-interval: 15`
- Large servers (> 200 players): `player-ttl: 10`, `everyone-ttl: 15`, `refresh-interval: 30`

## Commands

### /reporter (aliases: /mr, /pathwaycache)

Manage the pathway cache system.

**Permission:** `mysterriareporter.cache` (default: op)

**Subcommands:**

- `/reporter refresh` - Manually refresh the everyone cache
- `/reporter clear` - Clear all caches (player + everyone)
- `/reporter clear <player>` - Clear cache for specific player
- `/reporter stats` - View cache statistics
- `/reporter reload` - Reload config and invalidate all caches

**Examples:**
```
/reporter stats
/reporter clear Steve
/reporter refresh
/reporter reload
```

## How It Works

### Primary Pathway Selection

When a player has multiple pathways, the plugin determines the "primary" pathway by:
1. Finding the pathway with the **lowest sequence number**
2. Lower sequence = higher power (Sequence 0 > Sequence 9)
3. This represents the player's strongest progression path

### Acting Progress Calculation

Acting progress is calculated as:
```
acting_progress = current_acting / needed_acting
```

Returns a decimal between 0.0 and 1.0 (e.g., 0.75 = 75% complete)

### Caching Strategy

1. **Player Cache**:
   - Individual lookups cached for fast repeated queries
   - Expires after `player-ttl` minutes
   - Limited to `max-player-entries` to prevent memory issues

2. **Everyone Cache**:
   - Bulk data loaded asynchronously
   - Refreshed automatically every `refresh-interval` minutes
   - Prevents expensive full-server scans on every request

3. **Cache Warming**:
   - Everyone cache loaded on plugin startup
   - Background refresh keeps data fresh
   - Player cache populated on-demand

## Performance

### Without Caching
- Every request queries Circle of Imagination API
- 50 requests = 50 API calls
- Potential lag on large servers

### With Caching (Default Settings)
- First request queries API, subsequent requests use cache
- 50 requests in 5 minutes = 1 API call
- Near-instant response times
- Minimal server load

## Troubleshooting

### Endpoint not found (404)
- Check that CatWalk is running: `/plugins`
- Verify endpoints are enabled in `config.yml`
- Check CatWalk logs for registration errors

### Stale data
- Lower TTL values for more frequent updates
- Use `/reporter refresh` to manually refresh
- Check `refresh-interval` is not too high

### High memory usage
- Reduce `max-player-entries` value
- Lower `player-ttl` to expire entries faster
- Disable player cache by setting `player-ttl: 0`

### Cache not refreshing
- Check `refresh-interval` is greater than 0
- Enable `debug-refresh: true` to see refresh logs
- Verify CircleOfImagination API is responsive

## Technical Details

### Architecture

```
HTTP Request → CatWalk → Endpoint → PathwayCacheManager
                                          ↓
                                   Caffeine Cache
                                          ↓
                                   (if cache miss)
                                          ↓
                              CircleOfImagination API
```

### Components

- **PlayerPathwayEndpoint**: Handles `/pathway/single/{player}` requests
- **EveryonePathwayEndpoint**: Handles `/pathway/everyone` requests
- **PathwayCacheManager**: Manages caching and refresh logic
- **PathwayUtil**: Utility methods for pathway calculations
- **CacheCommand**: Admin command implementation

### Thread Safety

- Caffeine cache is thread-safe
- Everyone cache refresh runs asynchronously
- No blocking on main server thread

## API Response Codes

- **200 OK**: Successful request, data returned
- **404 Not Found**: Endpoint disabled or not registered
- **500 Internal Server Error**: CircleOfImagination API error

## Development

### Building

```bash
./gradlew clean build
```

### Dependencies

See `build.gradle` for full dependency list.

### Adding Custom Endpoints

To add new endpoints:
1. Create endpoint class in `net.mysterria.reporter.api`
2. Inject `PathwayCacheManager` via constructor
3. Register in `MysterriaReporter#registerEndpoints()`

## Changelog

### Version 1.0.0
- Initial release with beyonder pathway reporting
- Caffeine-based caching system
- Player and everyone endpoints
- Admin cache management commands
- Configurable TTL and refresh intervals

## Support

For issues, feature requests, or questions:
- Contact the Mysterria development team
- Check server logs for error messages
- Use `/reporter stats` to verify cache operation

## License

Proprietary - Mysterria Server
