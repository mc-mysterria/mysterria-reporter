# Activity history compatibility

The history endpoint requires authentication and returns the existing response shape asynchronously.
It calls the coordinated COI plugin's `readPlayerActivity(name, limit)` capability. COI sequences the
bounded tail read behind accepted activity writes on its log worker. No flush command, whole-file
read, main-thread wait or independent history copy is created by Reporter.

The history read is deliberately uncached. Requests are capped at 10,000 lines and COI reads at most
1 MiB of the tail. A missing capability, unavailable worker or three-second timeout returns 503 rather
than a stale-success response. Deploy the coordinated COI change first.

Caffeine, Gson and SnakeYAML are bundled and privately relocated in the deployment JAR. Paper,
CatWalk and COI APIs remain supplied by their actual plugins. `-PcoiApiJar=/path/to/api.jar` supports
coordinated local verification; normal builds retain the published API dependency.
