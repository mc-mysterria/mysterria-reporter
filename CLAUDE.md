# CLAUDE.md

MysterriaReporter is a Paper 26.3 plugin exposing Circle of Imagination data over CatWalk 0.9 (Javalin 7). README.md
documents every endpoint and the response contract.

## Build

`./gradlew build` → `build/libs/MysterriaReporter-{version}.jar`. CatWalk 0.9-SNAPSHOT comes from mavenLocal
(`./gradlew publishToMavenLocal` in `../catwalk`), the COI API from the Mysterria Nexus.

## Layout

- `MysterriaReporter` wires everything; `config/ReporterConfig` is loaded once at startup.
- `domain/pathway` - `PathwayService` caches single lookups (TTL map) and an async-refreshed roster of all beyonders.
- `domain/beyonder` - bless/rebirth through the COI offline API, beyonder data files and activity logs.
- `domain/report` - artifact files and the balance export.
- `util/file/ServerFiles` reads only inside `plugins/CircleOfImagination`; validate names with `isSafeName` first.
- `util/api` - `ApiException(status, message)` thrown by services, `Responses.of/ofAsync` turn it into the envelope.

## Conventions

- Endpoints return data payloads in CatWalk's `BridgeApiResponse`; failures use HTTP status codes, never `success:false` inside `data`.
- Field names on the wire are Gson's (the Java field names); the live backend parses them, so renaming a field is a breaking change.
- Read endpoints use scope `reporter.read`, state-changing ones `reporter.admin` with `mainThread = true`.
- Each endpoint checks its `endpoints.*` toggle and answers 404 when disabled.
