# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Native Android client(s) for the stoganet ecosystem. **Kotlin + Compose**, three Gradle modules:
`:core` (data/DI, no UI framework deps), `:tv` (Compose-for-TV app, shipping today), `:mobile`
(phone/tablet app, placeholder screen only — real screens are a separate follow-up). Manual DI
via a `ServiceLocator` in `:core`, MVI-lite (single `UiState` + `onIntent`) per screen. Talks
exclusively to `api-proxy` (`https://api.stoganet.com`) — never directly to Jellyfin. Sibling
repos: `api-proxy` (Go backend), `infra` (compose stack), `edge` (Caddy), `stogad` (Rust host
daemon).

`:tv` and `:mobile` ship as separate Android apps with separate `applicationId`s
(`com.stoganet.tv`, `com.stoganet.mobile`) and separate manifests — `:tv` requires the Leanback
launcher category and `android.software.leanback` feature, `:mobile` doesn't. Both depend on
`:core` for data/business logic; neither depends on the other.

## Key wiring

**Single Ktor `HttpClient`:** built by `HttpClientFactory.buildHttpClient(tokenStore)` (in
`:core`) with the Ktor `Auth` plugin (bearer). `loadTokens` reads from `TokenStore`;
`refreshTokens` posts to `auth/refresh` and marks the request with `markAsRefreshTokenRequest()`
so the Auth plugin does not retry the refresh on a 401, preventing infinite loops.
`sendWithoutRequest` is scoped to the API host so the `Authorization` header is never sent to
third-party URLs. The same client instance is reused by Coil via `KtorNetworkFetcherFactory` in
`:tv`.

**Two NavHosts (`:tv` only):** `AuthNavHost` (no tokens → Quick Connect screen today) and
`AppNavHost` (authenticated → Home placeholder today). `MainActivity` reads `TokenStore` to decide
which to show. `:mobile` has no navigation graph yet — just a single placeholder screen.

**ServiceLocator graph (`:core`):** `TokenStore` → `HttpClient` → `StoganetApi` → repositories.
New repositories should be added to `ServiceLocator` and wired there. Both `:tv` and `:mobile`
consume the same `ServiceLocator`.

**OpenAPI client:** Generated from `openapi/openapi.yaml` (kept in sync with `api-proxy`'s spec)
via `./gradlew :core:openApiGenerate`, into `com.stoganet.core.api`. Output lands in
`core/build/generated/openapi/`. When `api-proxy` adds new endpoints, copy the updated spec here
and regenerate before implementing the repository method.

## Architecture invariants

These cross-file constraints matter when editing — violating any is a 🔴 Important in review:

- **`refreshTokens` calls `markAsRefreshTokenRequest()`** — without it, a 401 on the refresh
  request triggers another refresh attempt → infinite loop.
- **Token refresh is serialised by Ktor's `Auth` plugin** — N parallel 401s trigger exactly one
  `refreshTokens` call; the other N-1 see the new token and retry automatically.
- **All token access goes through `TokenStore`** (Proto DataStore + Tink). No raw
  `SharedPreferences`, no direct file I/O, no globals.
- **`UiState` is immutable** and updated only via `_state.update { it.copy(...) }`.
- **Required parameters before optional ones** in data classes and functions.
- **`UiState` data classes are annotated `@Immutable`** — tells the Compose compiler the type is
  stable, enables recomposition skipping.
- **Collections inside `UiState` are `ImmutableList` / `ImmutableMap`** from
  `kotlinx.collections.immutable`.
- **`ViewModel → Repository → generated ApiClient`** layering is one-way.
- **Composables never receive a `ViewModel` as a parameter.** The NavHost `composable {}` block
  owns ViewModel creation and state collection; it passes `state: UiState` and
  `onIntent: (Intent) -> Unit` to the screen composable. This keeps screens pure, previewable,
  and testable without Android framework dependencies.
- **Every screen composable has `@Preview` functions** for each meaningful UI state.
- **All interactive TV elements have an explicit `contentDescription`** via
  `Modifier.semantics { contentDescription = "..." }`. On TV, TalkBack reads `contentDescription`
  from the focusable element — child `Text` implicit labels are unreliable on D-pad focus.
- **Strings live in `res/values/strings.xml`.** No hardcoded user-visible English in Compose code. Pure format patterns that contain no translatable words (e.g. `"${n}. $title"`, `"${h}h ${m}m"`) are exempt — the words come from the API or are universal punctuation.
- **No telemetry / crash reporting / analytics.**
- **CI actions are SHA-pinned**, never tag-pinned.
- **Never call `runBlocking` on the main thread.** ViewModel factories
  (`initializer {}` blocks), `@Composable` functions, and any code not inside a
  coroutine execute on the main thread. Blocking there risks ANR. Use
  `viewModelScope.launch` or `withContext(Dispatchers.IO)` instead. Exception:
  lambdas passed to background-thread APIs where the library documents the
  callback runs off-main (e.g. `DataSource.Factory` in Media3 — ExoPlayer calls
  `createDataSource()` on its loader thread).
- **ViewModel factory `initializer {}` runs on the main thread** — no blocking
  I/O, no `runBlocking` over suspend functions there.
- **`CompletableDeferred` in tests must be resolved before the test body ends.**
  Call `.complete(value)` or `.cancel()` — never rely on dispatcher teardown to
  implicitly clean up a suspended coroutine. If bridging a callback API in
  production, prefer `suspendCancellableCoroutine` (handles cancellation
  automatically) over `CompletableDeferred`.

## Common operations

```bash
./gradlew :tv:installDebug
./gradlew :mobile:installDebug
./gradlew :core:testDebugUnitTest :tv:testDebugUnitTest
./gradlew detekt
./gradlew detekt --auto-correct
./gradlew :core:openApiGenerate
./gradlew :tv:assembleRelease
./gradlew :mobile:assembleRelease
```

## Editing notes

- **Generated OpenAPI client (`core/build/generated/openapi/`) is never edited by hand.**
- **Android TV manifest (`tv/src/main/AndroidManifest.xml`) is load-bearing.** Do not remove
  `LEANBACK_LAUNCHER`, the leanback `uses-feature`, the banner reference, or flip `allowBackup`
  to true.
- **R8 keep rules for `:core` types (serialization, OkHttp, Tink) live in
  `core/consumer-rules.pro`** and are merged automatically into any module that depends on
  `:core` — don't duplicate them in `tv/proguard-rules.pro` or `mobile/proguard-rules.pro`.

## Stoganet conventions

Skills in `.claude/skills/` (if present) enforce the org-wide workflow — invoke them via the
Skill tool:

- **committing** — Conventional Commits; match the style used in `api-proxy`.
- **creating-pull-requests** — PR title becomes the squash-commit.
- **creating-issues** — Shared body template across all Stoganet repos.
- **handoff** — Use before context compaction when there's uncommitted work worth preserving.
