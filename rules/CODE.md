# Code Quality Rules — N-Zik Desktop Compagnon

**Version:** 1.0.0 | **Last updated:** 2026-10-05

## Naming Conventions

- **Classes/PascalCase**: `BridgeSession`, `RemotePlayerRepository`, `AlbumScreen`
- **Functions/camelCase**: `requestSnapshot`, `loadNextPage`, `playFrom`
- **Constants/UPPER_SNAKE_CASE**: `MENU_SHEET_PARTIAL_FRACTION`, `LIVE_RELOAD_DEBOUNCE_MS`, `DEFAULT_PORT`
- **Variables/camelCase**: `playerState`, `isLive`, `candidateAddresses`
- **Packages/lowercase**: `app.n_zik.compagnon.bridge.pairing`, `app.n_zik.compagnon.components.player`

## Wire Contract (MANDATORY — the domain of this app)

The PC speaks a versioned wire contract with the phone's local server (spec `spec-n-zik-pc-bridge`, contract 1.x — the spec lives in the workspace `_bmad-output/`). The phone is the source of truth for library, queue and player state.

- **Features gate the UI:** a screen/action exists only while its `SessionContract` feature is present (`queue`, `playback`, `artwork`, `library.*` …). Without the feature, hide or disable — do not fake it.
- **Inert, never fake:** a button/menu entry without a contract route is an `InertButton`/`InertMenuItem` (or inert placeholder) carrying **the phone's own title**. It is shown so the screen stays faithful to the phone; it performs no action and its KDoc documents why.
- **Decisions from the error `code` only** (`404` not found, `401 DEVICE_REVOKED` revoked, `409 CONFLICT_ACTIVE_CLIENT` other PC, …) — never from the HTTP status alone, never by guessing.
- **Older phones must keep working:** the PC reads the served features and fields and falls back (e.g. absent `sortMenu` → static menus; ≤ 1.7.2 phone → binary bookmark). Never assume a field exists because a newer phone has it.
- **Optimistic UI** exists ONLY for the contract §10.2 writes (like/bookmark/follow/pin/pin-playlist): patch the loaded lists at the click, the confirmed `200` re-patches with the phone's resulting state, a failure re-reads the touched lists. All other commands have no optimistic UI.
- **PC-local state** (settings, cache, per-chip sorts) is separate from phone state — the PC never stores or re-serves phone data, it re-reads it (`PagedList` lists live in memory only, contract §12).

When in doubt what the PC may do → read the contract spec in `{project-root}/_bmad-output/` and HALT/ask, don't invent.

## Kotlin/Compose Anti-Patterns

NEVER use these — they cause bugs, crashes, or performance issues:

```kotlin
// BAD — structured scope required
GlobalScope.launch { ... }

// GOOD — app-lifetime scope via the fire-and-forget helper (see "Coroutines & Dispatchers")
private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
```

```kotlin
// BAD — blocks a thread (production code)
runBlocking { ... }

// GOOD — suspend function
suspend fun fetchData() { ... }
```

```kotlin
// BAD — race condition
_state.value = _state.value.copy(loading = true)

// GOOD — atomic update
_state.update { it.copy(loading = true) }
```

```kotlin
// BAD — no key, bad performance
LazyColumn {
    items(list) { item -> ItemRow(item) }
}

// GOOD — key + contentType
LazyColumn {
    items(list, key = { it.id }, contentType = { "item" }) { item ->
        ItemRow(item)
    }
}
```

Rules:

- NEVER use `GlobalScope` — use structured scopes; app-lifetime scopes are `CoroutineScope(SupervisorJob() + Dispatchers.X)` (see **Coroutines & Dispatchers**)
- NEVER use `runBlocking` in production code (use suspend functions); any usage — new or pre-existing — must carry a comment explaining why a synchronous API forces it. In tests, `runTest` (kotlinx-coroutines-test) is the convention
- **`collectAsState()` IS the convention here** — Compose Desktop, no lifecycle-aware variant in this project's dependencies; the whole codebase collects repository flows with plain `collectAsState()`
- NEVER do a read-modify-write (`_state.value = _state.value.copy(...)`) — use `_state.update { it.copy(...) }`. Direct assignment (`_state.value = …`) is allowed only to set the initial state
- Expose a single state flow per repository (e.g. `PlayerRepository.state`), reduced by pure logic (`StateReducer`) where transitions are complex — sealed state classes only when states are mutually exclusive (e.g. `ConnectionState`)
- Data params to children: annotate with `@Stable` or `@Immutable`
- No IO/network in composition body
- LazyColumn/LazyRow must have `key` + `contentType`

## Kotlin Null-Safety

- NEVER use `!!` operator unless absolutely justified (add comment explaining why)
- Prefer `?.` + `let` for safe calls
- Use `requireNotNull()` for preconditions with clear error messages
- Use `checkNotNull()` for state assertions
- Return early for null values instead of deeply nested null checks

```kotlin
// BAD — will throw NPE
val name = user!!.name

// GOOD — safe call + let
user?.let { nameText.text = it.name }

// GOOD — requireNotNull with message
val track = requireNotNull(queue.firstOrNull { it.id == trackId }) { "Track $trackId not in queue" }
```

## File Placement (MANDATORY)

New files MUST go under `app.n_zik.compagnon.*`. The tree mirrors the phone app's organization (ported code keeps the phone's file names — see Done.txt), so prefer placing a new file where its phone counterpart lives.

| Component type               | Location                                                        |
| ---------------------------- | ---------------------------------------------------------------- |
| Screens (page-level)         | `components/ui/screens/{screen}/` (home, album, artist, localplaylist, player, bridge, settings, profiles) |
| Menus (per domain)           | `components/menu/{domain}/` (song, album, artist, playlist, player) + `components/menu/` for the shared sheet |
| Player UI                    | `components/player/` (Player, MiniPlayer, queue, seek bars, `controls/`) |
| Chips / toolbars / tab logic | `components/tab/` + `components/tab/toolbar/`                    |
| Settings rows                | `components/settings/` (`SettingsSectionCard`, `ToggleSettingsEntry`, …) |
| Theme & typography           | `components/theme/` (`ColorPalette`, `Typography`, `AnimatedAppearance`, `Hsl`) — do NOT invent new CompositionLocals |
| Reusable themed widgets      | `components/themed/` (Dialog, DropdownMenu, Loader, NowPlaying, …) |
| Navigation                   | `core/navigation/` (BackNavigation — keyboard back + stack)       |
| Network (contract client)    | `core/network/` (`BridgeClient` + `LibraryApi`, `PlayerApi`, `AudioApi`, `CandidateAddresses`) |
| Pairing / bridge             | `bridge/pairing/`, `bridge/state/`, `bridge/library/`, `bridge/command/` |
| Playback                     | `playback/vlc/`, `playback/cache/`, `playback/services/`          |
| Pure helpers (palette, etc.) | `core/palette/`, `core/coil/`                                    |
| Enums                        | `enums/`                                                        |
| Utilities                    | `utils/` (`Preferences`, `Toaster`, `formatMessage`/`formatText`, `PhoneDensity`, …) |

## Imports

- Imports at top of file ALWAYS
- NO wildcard imports (`import com.example.*`) — one exception: generated compose resources use a single wildcard `import app.n_zik.compagnon.generated.resources.*` (never per-resource explicit imports: the accessors are generated one top-level extension per resource, `Res` is covered by the same wildcard, and explicit imports have to be hand-maintained on every resource swap)
- NO inline fully qualified names (`java.util.List`) unless absolute naming conflict
- Remove unused imports before committing
- Sort: LEXICOGRAPHIC within the whole import block (the IntelliJ/VS Code default — keeps the block stable across editors and `Optimize Imports` runs; do NOT regroup by stdlib/third-party/project)

## Comments

- Add comments only for complex or non-obvious logic
- Do NOT restate what the code already says
- Use KDoc for public APIs (see example below)
- Mark TODOs with `// TODO(author): description`
- **Ported-code note:** when a screen/constant is copied from the phone, the KDoc states the phone origin (file/lines) and any deliberate deviation — this is the convention throughout `components/`; keep it when adding ported code

```kotlin
// BAD - restates the code
// Increment counter by one
counter++

// GOOD - explains WHY
// Coalesce live reloads to ~300 ms per family so a burst of libraryChanged
// deltas re-reads each touched list once instead of per delta
val debounceMs = LIVE_RELOAD_DEBOUNCE_MS
```

### KDoc Format

```kotlin
/**
 * Reads the next page of a collection from the phone.
 *
 * @param offset 0-based offset into the phone's list
 * @param limit page size (contract default 100)
 * @return the page plus the collection total
 * @throws IOException if the phone is unreachable
 */
suspend fun nextPage(offset: Int, limit: Int): Page
```

## Dead Code

- Remove commented-out code blocks
- Remove unused functions, classes, variables, parameters
- If kept for reference, add `// TODO: reason`

## Logging — java.util.logging ONLY

NEVER use `println`, `System.out`, `e.printStackTrace()`. Use the platform logger, one per class, named by the class:

```kotlin
import java.util.logging.Logger

class MyClass {
    private val log = Logger.getLogger("MyClass")

    fun doSomething() {
        log.info("Doing something")
    }
}
```

### Logging Levels

- **fine** (debug): development-only diagnostics
- **info**: important lifecycle events (session open/close, snapshot, libvlc loaded)
- **warning** (warn): recoverable issues (unreadable settings → defaults used, a CredRead/CredWrite failure, a dropped malformed frame)
- **severe** (error): unrecoverable failures (bridge down after all backoff, corrupt credential state)

> **NEVER log the device token** — not at any level, not even masked (see rules/SECURITY.md).

## Error Handling — runCatching

```kotlin
runCatching {
    riskyOperation()
}.onFailure { e ->
    log.warning("Operation failed: ${e::class.simpleName}")
}
```

- Match the house style: log the exception's simple name (and its message only when it adds information), never stack traces
- NEVER swallow exceptions silently — ALWAYS log

## Performance

- Use `NzikDispatchers` named dispatchers only — see **Coroutines & Dispatchers** below (raw `Dispatchers.*` is allowed inside `NzikDispatchers` itself, nowhere else new)
- Move CPU-bound work off the EDT: `withContext(NzikDispatchers.MEDIA)` (palette extraction, bitmap work); network/disk through `NzikDispatchers.DATA`
- Cancel coroutines when their owner disappears (`DisposableEffect`, `scope.cancel()`)
- Bounded caches with eviction (artwork LRU 256, audio cache size cap from settings) — the pattern is established in `ArtworkLoader`/`AudioCache`; mirror it, don't leak unbounded maps
- Profile startup and rendering performance; keep list rows cheap (no per-row bitmap work — art comes from the loader)

## Coroutines & Dispatchers — NzikDispatchers (MANDATORY)

All named threads/dispatchers in the app come from `NzikDispatchers` (`app.n_zik.compagnon.utils.coroutines`) — the single source of truth for threading, **same name and same shape as the phone app's** `NzikDispatchers`. NEVER add new raw `Dispatchers.IO` / `Dispatchers.Default` / `Dispatchers.Main` / hand-rolled `Executors.*` usages in app code — use the named entries below.

| Entry point                  | Threads                       | Use                                                                                                                          |
| ---------------------------- | ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `NzikDispatchers.UI`         | `Dispatchers.Main`            | UI & the Swing/Compose thread only — the ONLY place to touch the Compose state from non-UI work                                |
| `NzikDispatchers.PLAYBACK`   | single `nzik-playback`        | Audio work (VLC engine commands, `LocalPlayback`, audio-cache bookkeeping) — single thread guarantees ordering                  |
| `NzikDispatchers.MEDIA`      | pool of 2 `nzik-media-*`      | CPU-bound media work: cover palette extraction, morphing-shape math, bitmap work, seek-bar wave sampling                       |
| `NzikDispatchers.DATA`       | `Dispatchers.IO`              | Network & disk IO: Ktor bridge calls (REST + WS), `pairing.json`/`settings.json`, audio download, the pairing listener sockets |

Rules:

- Fire-and-forget / app-lifetime scopes (deliberately never cancelled — the toast scope, the shuffle flash, the artwork loader, `BridgeSession` cleanup, `PairingListener`): build them with `NzikDispatchers.fireAndForget(dispatcher)` (or the `CoroutineContext` overload) — it adds a `SupervisorJob` + a logging `CoroutineExceptionHandler`, so one exception can no longer cancel siblings or crash the process. The helper NEVER cancels the scope
- NEVER use a bare `Job()` for a scope that must survive individual failures (fire-and-forget / app-lifetime scopes): with a plain `Job`, one unhandled exception cancels the whole scope and its siblings — `SupervisorJob` is the only correct cancellation root here
- NEVER cancel app-lifetime scopes from component lifecycle code (a composable leaving composition is NOT the owner of an app-lifetime scope)
- `NzikDispatchers` is an app-lifetime singleton with daemon threads — NEVER `shutdown()` its executors or close them from a scope/cleanup path: that kills the pools for the rest of the process
- Use `withContext(NzikDispatchers.X)` to move work between named dispatchers; cancel regular coroutines in `DisposableEffect`
- Unit tests may still use `Dispatchers.setMain()`/`Dispatchers.resetMain()`; the established test style is `runTest` with virtual time (see Testing); real-socket tests (the pairing listener) run on the real dispatcher by design

> **Transition — `NzikDispatchers` is planned, not yet in the tree:** creating it (in `utils/coroutines/`, same shape as the phone app's) is a tracked step (see `assets/notes/TODO.txt`, "Threading"). Until it lands, the established `CoroutineScope(SupervisorJob() + Dispatchers.X)` pattern (`Toaster.kt`, `ShuffleOkFlash.kt`, `ArtworkLoader.kt`, `BridgeSession.kt`, `PairingListener.kt`) stands in for the fire-and-forget helper. Pre-existing raw `Dispatchers.*` usages in the current tree (`Main.kt`, `MainActivity.kt`, `BridgeClient.kt`, `Player.kt`, `PairingListener.kt`) are **grandfathered** — do NOT opportunistically migrate them unless the user asks (same treatment the phone repo gives its grandfathered legacy usages).

## UI — Compose Multiplatform Desktop + Material 3

- All UI in Compose (Desktop target, single `:app` module) — no XML, no Swing UI code outside the Compose bootstrap in `Main.kt`
- **The phone's look & feel is the source of truth:** screens are ported from the phone file by file (same names, same constants, same modifier chains); when in doubt, check the phone app's file (workspace `N-Zik/` repo, READ-ONLY reference) and match it
- Use the existing theming system — `ColorPalette` (dynamic palette read from the current track's cover via `CoverPaletteExtractor`, N-Zik violet without a cover), `AnimatedAppearance`, `PaletteFade`, Rubik/Roboto typography; do NOT invent new CompositionLocals
- Animations under 300ms for snappy feel (sheet appear 300 ms, the 250 ms pop — ported values)
- Use `Modifier` for styling, chain for multiple effects
- Keep composables small (single responsibility)
- **Input model:** no touch — right click stands in for the phone's long press (opens item menus); drag gestures are ported where the phone has them (sheet drag with the `MENU_SHEET_*` snap thresholds, queue reorder)

### Sheet Drag & Snap

Menu sheets follow the phone's `CustomModalBottomSheet` semantics, ported at `Menu.kt` (`menuSheetSnapFraction`, pure + tested): a 48 dp zone along the sheet's top edge drags between the partial half and the full height; snap to full above 85 %, close below 40 %, half in between (spring 0.8 / 300 ms). When touching sheet behavior, keep the thresholds in `MenuSheetSnapTest` green.

## Accessibility

- All images/icons must have `contentDescription`
- Use semantic properties in Compose
- Maintain WCAG AA contrast ratios (4.5:1 text, 3:1 large text)
- Minimum touch/click target: 48dp
- Keyboard focus must not be trapped by a menu or sheet; Escape closes top-most (established in the sheets)
- If accessibility violation detected → HALT, fix before continuing

## Local Persistence (no database on the PC)

- **The phone's local DB is the only database.** The PC persists nothing of the library — lists are REST snapshots in memory (`PagedList`, contract §12). Never add a database to this repo
- **Device token:** Windows Credential Manager only (generic credential "N-Zik Desktop Compagnon", via `WindowsCredentialSecretStore`'s JNA `CredWriteW`/`CredReadW`/`CredDeleteW`). NEVER in `pairing.json`, NEVER in logs, NEVER in `Done.txt`/README/code
- **`pairing.json`** (`%APPDATA%\N-Zik Desktop Compagnon\`): the non-secret pairing fields only (`serverIps`, `serverPort`, `serverName`, `deviceId`, `deviceName`) — a missing token or a corrupt file must read as "not paired"
- **`settings.json`** (same dir): PC-local settings via `utils/Preferences.kt`; an unreadable file reads as defaults (log a warning), never crashes
- **"Forget this phone" / revocation must remove BOTH** the Credential Manager entry and `pairing.json` (revocation paths go through `RevocationPolicy` — reuse it, don't branch around it)
- **Audio cache** (`%APPDATA%\N-Zik Desktop Compagnon\cache\audio\`): PC-local, replayed without asking the phone; size capped by the app's settings (the phone's "Song cache max size" values, 2 GB default; "Turn off" downloads nothing)

## Network Resilience

- The phone's server is on the **local Wi-Fi** — plain HTTP/WebSocket on the LAN is by design (contract); do NOT "fix" it to HTTPS (see rules/SECURITY.md)
- Phone unreachable → reconnection with backoff 1, 2, 4, 8, 16 then 30 s without limit; a session silent for 45 s (no heartbeat nor pong) also counts as a transient loss (contract §6.4)
- **No automatic reconnect** on deliberate closes: `4000` replaced, `4001` kicked, `409 CONFLICT_ACTIVE_CLIENT` another PC, `503 SERVER_STOPPING` / `1001` server stopped — the user reconnects by hand
- `401 DEVICE_REVOKED` / WS `4003` → `RevocationPolicy` (token + `pairing.json` erased, re-pairing screen)
- REST decisions from the error `code` only (see **Wire Contract**)
- Show user-friendly state for the unreachable/revoked/other-PC cases (the existing connection banners are the pattern)

## Testing Conventions

- **Framework: JUnit 5 (Jupiter) only** — no vintage engine, no Robolectric, no Compose UI test harness in this repo (tests target models, reducers, repositories, network clients, pure UI math)
- Test method names are backtick descriptive sentences (`` fun `snapshot always applies even when lower`() ``) — match this style
- **Coroutines:** `runTest` (kotlinx-coroutines-test) with virtual time for timing logic (backoff, debounces, the 3 s delta wait) — the dominant style
- **HTTP:** Ktor `MockEngine` for `BridgeClient`/repository route tests (assert routes, bodies, parameters, id encoding, error mapping); a **real CIO server on loopback** only where a real socket is the subject (the pairing listener)
- **Pure UI math** (sheet snap, seek bar scrub, duration indicator, palette fade) is extracted to pure functions and tested directly — follow this pattern for new UI logic
- Test files: `app/src/test/kotlin/` — mirror the source package structure

```kotlin
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PagedListTest {
    @Test
    fun `a failed page keeps the loaded ones and waits for retry`() = runTest {
        // ...
        assertEquals(100, list.loadedCount)
    }
}
```

## Known Flaky Test

`PairingListenerTest` "real listener renews the requestId…" is intermittently flaky in the full suite (passes alone) — a race between the real listener thread and the test dispatcher (recorded in `TODO.txt`). One failure in an otherwise-green suite → re-run once; if it fails again → report it as the known flake, don't burn the 3-attempt build counter chasing it.

## Translations

- UI strings live ONLY in `app/src/main/composeResources/values/strings.xml` (English, the single strings file — there are no `values-*` files and no Crowdin on the PC)
- Ported screens **reuse the phone's keys and English texts** — copy the phone's `strings.xml` entries when porting a screen, don't invent new keys for the same concept
- **Format arguments:** this Compose version's `getString`/`stringResource` apply NO format arguments — any string with `%s` is formatted at the call site with `formatMessage` (suspend) / `formatText` (`utils/Toaster.kt`); the raw message is kept on a mismatch. Never add format args to a string expecting the resource loader to apply them

## Dependency Injection

- Plain constructor injection (no DI framework in this repo — none is declared in the catalog)
- There is no `Application` — process lifetime = app lifetime; app-lifetime singletons are held by the Compose bootstrap (`Main.kt`) or as `object`s (precedent: `Toaster`, `GlobalVars` accessors)
- Introducing a DI framework requires user approval
