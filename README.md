# Solgram 2.0.0 - Native Kotlin Telegram Client with Trading-Signal Intelligence

Solgram is a personal-use Windows desktop Telegram client with crypto-trading signal automation built on top. It speaks the real Telegram protocol (MTProto) as your own account via TDLib. It is not a bot, not a web wrapper, and it never executes trades - it surfaces information and opens URLs for a human to act on.

## Features (Full Spec Implementation)

- **Platform**: Windows 10/11 (packaging), Kotlin 100% application source
- **Native dep**: TDLib (libtdjson.dll) isolated behind single interface
- **UI**: Compose Multiplatform for Desktop - native Skia rendering
- **Database**: SQLite via SQLDelight - WAL mode, reactive Flow
- **Local API**: Ktor embedded server (REST + WebSocket) bound to 127.0.0.1 only
- **Packaging**: Gradle + jlink + jpackage + WiX -> signed MSI/EXE
- **Storage**: %APPDATA%\Solgram

### Implemented Modules

1. **Architecture** - Single JVM process, TelegramActor single dispatcher, StateFlow/Flow collection, SolgramResult sealed type
2. **Account & Login** - QR code and phone login via TDLib AuthorizationState machine, session manager
3. **Messaging** - Chat list with pinned first, LazyColumn virtualization, date separators, sender grouping, media handling, datasaver mode, composer with debounced drafts
4. **Message Actions** - React, Reply, Copy, Translate, Forward as new, Export, Pin/Unpin, Edit, Delete, SELECT MODE
5. **Contract-Address Detection** - Solana base58 32-44 and EVM 0x40 hex, Solana wins on ambiguity, non-overlapping, noise filtering
6. **Signals Dashboard** - BY TOKEN and BY CALL, crowd confidence distinct channels, caller list with gap formatter, master channels, reputation decay with suggestions, velocity alerts, leaderboard, sentiment tagging
7. **Compare Channels** - Ultimate vs match channels, share %, combined success rate (no double-count), filters, cached-data honesty
8. **CA Feed + Local API** - Live stream, REST + WS, bearer-token, channel scope in SQL, openUrl only http/https
9. **Forward Rules** - Name, sources, dests, extraction modes, prefix, min trust, duplicate window, send interval, conditional rule chains with AND/OR
10. **Trading Shortcuts** - Jupiter, Photon, BullX, DexScreener, Birdeye, Raydium, GMGN, GeckoTerminal, Uniswap, PancakeSwap, DEXTools, Maestro - pure URL construction
11. **Portfolio Watchlist** - Read-only wallet polling via public RPC, cross-reference against Signals history, rate-limited
12. **Price Anomaly Detection** - Liquidity drop, sell cluster, price collapse - heuristic, not verdict
13. **Search** - Cross-chat global search, FTS5 with filters, local cache only
14. **Exporting** - JSON/TXT/CSV, shared destination control, UTF-8 BOM for Excel
15. **Import** - Telegram Desktop JSON export, CA detection on import, one-time bulk import
16. **Translation** - Google unofficial + LibreTranslate fallback, language detection strips CAs/URLs/tickers, cached
17. **Appearance** - 5 themes, 6 backdrops, per-chat override, keyboard-first navigation, intensity 0-200%, scale 85-130%
18. **Notifications & Alerts** - Custom sound profiles, Telegram-to-Telegram relay to Saved Messages, Zapier/n8n webhook builder with JSON template validation, DND windows, Windows toast
19. **Window, Tray & Background** - Custom frameless window, system tray with Show/Pause-Resume/Quit, single instance with lock file + loopback handshake, global hotkeys, CLI flags
20. **Resilience** - TDLib reconnect/backoff, flood-wait handling, background sync scheduler ON/OFF default ON, clean shutdown bounded and ordered, DB self-repair
21. **Data, Privacy & Security** - Local unencrypted by default, SQLCipher optional, no telemetry, noforwards guarantee via detekt rule on parsed syntax tree, API 127.0.0.1 only, credentials never round-tripped
22. **Diagnostics & Recovery** - Health dashboard, size dashboard + pruning with preview, startup profiler, Doctor flags --repair/--reset-db/--unlock/--no-repair
23. **Testing** - Kotest + JUnit5 + MockK, CaDetector fuzz-tested, gap formatter single implementation, RulesEngine shared, forward-ban rule, combined success rate test, webhook validation
24. **Building & Packaging** - Full Gradle config with jlink + jpackage + WiX, code signing example, CI example

## Prerequisites

```
JDK 17+
WiX Toolset v3.11        winget install WiXToolset.WiXToolset
```

## Build Commands

```
./gradlew run                 # dev run
./gradlew test                # unit + property tests
./gradlew detekt              # static analysis incl. forward-ban rule
./gradlew packageMsi          # -> build/compose/binaries/main/msi/Solgram-2.0.0.msi
./gradlew packageExe          # -> build/compose/binaries/main/exe/Solgram-2.0.0.exe
./gradlew doctor              # environment self-check, startup profiler report
```

## Native Library Loading

```kotlin
private fun loadNativeLibrary() {
    val appDir = Path.of(System.getProperty("compose.application.resources.dir")
        ?: System.getProperty("user.dir"))
    System.load(appDir.resolve("libtdjson.dll").toAbsolutePath().toString())
}
```

`libtdjson.dll` and dependencies (`zlib1.dll`, `libssl-3-x64.dll`, `libcrypto-3-x64.dll`) live in `native-libs/` so `appResourcesRootDir` places them alongside executable.

## Code Signing

```
signtool sign /f certificate.pfx /p <password> /fd SHA256 ^
  /tr http://timestamp.digicert.com /td SHA256 ^
  build\compose\binaries\main\msi\Solgram-2.0.0.msi
```

## CI

GitHub Actions workflow builds on windows-latest with JDK 17 and WiX, runs tests, packages MSI/EXE, and uploads artifacts. See `.github/workflows/build.yml`.

## Known Limits and Caveats

- WINDOWS ONLY IN PRACTICE for this release, despite multiplatform-structured domain layer
- ONE NATIVE DEPENDENCY - libtdjson.dll isolated behind single interface
- YOUR OWN API CREDENTIALS required from https://my.telegram.org
- PERSONAL USE - mass-forwarding risks account limits; duplicate guards and send intervals help avoid
- NO WALLET INTEGRATION, NO EXECUTION - permanent line, Trade buttons only open URL, Portfolio Watchlist read-only never requests private key
- DETECTION IS PATTERN-BASED - correctly-shaped string that isn't real mint can still match; verify before trading
- COMPARISON, SIGNALS, BACKTESTING AND SEARCH ONLY SEE CACHED DATA - background sync scheduler reduces how often this bites but does not eliminate for very recently joined channels
- REPUTATION SUGGESTIONS, VELOCITY ALERTS, LEADERBOARD, SENTIMENT TAGS, ANOMALY DETECTION ARE HEURISTICS - informational aids, never verdicts
- UNOFFICIAL TRANSLATION ENDPOINT can break; LibreTranslate stable alternative
- DATA IS UNENCRYPTED ON DISK BY DEFAULT - SQLCipher opt-in
- TELEGRAM-TO-TELEGRAM ALERT RELAY sends alerts through own authenticated session to Saved Messages - counts as messages sent by account
- ANY CONFIGURED WEBHOOK IS ONE PLACE DATA LEAVES MACHINE BY DESIGN - disclosed in builder UI
- PORTFOLIO WATCHLIST POLLING IS RATE-LIMITED - fast wallet activity may be delayed
- IMPORT IS ONE-DIRECTIONAL AND ONE-TIME per file - not ongoing sync bridge, media only if files included and locatable

## Project Structure

```
commonMain/    domain/, db/, ui/ - zero platform-specific calls
desktopMain/   app/ - window chrome, tray, clipboard, hotkeys, TdLibEngine, packaging entry point
```

Actually implemented as `src/main/kotlin/com/solgram/` with packages:
- app/ - entry point, window lifecycle, tray, single-instance guard, global hotkeys
- domain/telegram/ - TelegramEngine interface + TdLibEngine
- domain/rules/ - RulesEngine, rule-chain evaluator, Backtester
- domain/detect/ - CaDetector
- domain/trade/ - trade URL builders
- domain/translate/ - translation backends
- domain/export/ - JSON/TXT/CSV writers
- domain/import/ - importers for other clients
- domain/price/ - PriceFeed, call-performance, AnomalyDetector
- domain/rug/ - RugHeuristics
- domain/signals/ - ReputationEngine, velocity alerts, leaderboard, SentimentTagger, crowd confidence
- domain/portfolio/ - PortfolioWatcher
- domain/search/ - SearchIndexer
- domain/alerts/ - AlertEngine, sound profiles, Telegram-relay, webhook builder
- db/ - SQLDelight schema, migrations, repair-before-migrate, size/pruning
- automation/ - Ktor server REST+WS bearer-token auth /docs
- ui/ - Compose screens
- concurrency/ - TelegramActor, ShutdownCoordinator, sync scheduler
- singleton/ - FileLock instance guard + loopback handshake
- diagnostics/ - Doctor env checks, startup profiler, repair flags
- build-logic/ - Gradle Kotlin DSL packaging config
- native-libs/ - libtdjson.dll and dependencies
```

## Running in Mock Mode

Without libtdjson.dll present, the app runs in mock mode for development:
- Mock chats and messages
- Mock price feed with random walk
- All UI functional

Set `-Dsolgram.mock=true` to force mock mode.

## License

Personal use. No telemetry, no analytics, no remote config.
