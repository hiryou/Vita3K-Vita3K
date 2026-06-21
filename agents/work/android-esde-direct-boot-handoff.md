# Android ES-DE Direct Boot Handoff

## Goal

Allow launching a PS Vita title from ES-DE directly into the installed game in Vita3K, instead of landing on Vita3K's library screen first.

Assumptions for this feature:

- The game is already installed in Vita3K.
- ES-DE indexing is driven from ROM entries under `ROMs/psvita/`.
- For now, only `.zip` ROM entries need to be supported.

## Current State

Direct boot by title ID already exists in Vita3K.

Relevant code:

- `android/app/src/main/java/org/vita3k/emulator/Emulator.java`
- `vita3k/android/jni/main_android.cpp`

What already works internally:

- `Emulator.createLaunchIntent(...)` accepts `EXTRA_TITLE_ID`.
- `Emulator.getArguments()` converts that into `-r <TITLEID>`.
- `vita3k/android/jni/main_android.cpp` parses `-r <TITLEID>` and starts that installed title.

This means the emulator core/native boot path is already good enough for ES-DE direct boot.

## Actual Gap

ES-DE currently launches Vita3K through the exported Android entrypoint:

- `org.vita3k.emulator/.MainActivity`

But `MainActivity` currently behaves like the normal library frontend. It does not parse an ES-DE launch intent and does not forward into `Emulator` with a resolved title ID.

Relevant files:

- `android/app/src/main/java/org/vita3k/emulator/MainActivity.kt`
- `android/app/src/main/AndroidManifest.xml`

## Scope of Code Change

This should be a small Android-side change. No Vita core/native boot changes should be required.

Expected work:

1. Add an ES-DE-facing boot contract.
2. Teach `MainActivity` to detect and handle external boot intents.
3. Resolve incoming `.zip` ROM entry to a single installed Vita title ID.
4. Forward immediately into `Emulator.createLaunchIntent(...)`.
5. Update manifest intent filters for the new launch action.

## Recommended Android Contract

Follow the same pattern used in the RPCSX fork.

Suggested pieces:

- New small contract file, for example:
  - `android/app/src/main/java/org/vita3k/emulator/esde/EsdeBootContract.kt`
- Suggested action:
  - `org.vita3k.emulator.action.BOOT_ROM`
- Suggested extra:
  - `path`

Then `MainActivity` should:

- inspect `intent.action`
- read `path`
- resolve `titleId`
- `startActivity(Emulator.createLaunchIntent(...))`
- `finish()`

Also add `onNewIntent(...)` handling so relaunches while Vita3K is already alive still work.

## `.zip` -> Title ID Resolution

Support `.zip` only for now.

### Important constraint

Do not assume title IDs always start with `PCSE`.

`PCSE` is only one regional/product prefix. A valid Vita title ID can use other prefixes. The repo itself has a note using the broader pattern `PCSXXXXXX`:

- `vita3k/io/include/io/functions.h`

So `PCSE...` should be treated as one example, not the rule.

### Resolution strategy

Use a two-stage resolver:

1. Fast path: parse from the `.zip` filename if a plausible title ID is present.
2. Fallback path: inspect the zip contents and derive the title ID from the internal app directory.

### Fast path

Input example:

- `Limbo (PCSE00268) (NTSC).zip`

If filename contains a valid-looking title ID token, use it directly.

This is cheap and should cover the happy path when ROM names are clean.

### Fallback path

If filename parsing fails or is ambiguous, inspect the zip.

Your observed install structure is the key fallback:

- `app/<titleID>/*content*`

That is the reliable source. If the zip is a valid installable archive and contains:

- `app/<titleID>/...`

then that `<titleID>` is the one to launch.

This fallback is more important than filename parsing, because the zip contents are authoritative.

### Practical rule

Resolver should:

- accept only `.zip`
- first try basename parsing
- if no unique valid title ID is found, inspect zip entries
- look for top-level `app/<titleID>/`
- return that title ID

If neither source yields a title ID, fail the launch request with a user-visible error or log message.

## Implementation Shape

### 1. Add a resolver utility

Suggested new helper:

- `android/app/src/main/java/org/vita3k/emulator/esde/VitaEsdeZipResolver.kt`

Responsibilities:

- validate `.zip`
- parse possible title ID from filename
- if needed, inspect zip entry names
- return:
  - `titleId`
  - optional display title derived from filename
  - debug details for logging

### 2. Add boot handling to `MainActivity`

Suggested flow:

- on app startup, before rendering Compose navigation, call `tryBootEsdeIntent(intent)`
- if handled:
  - `finish()`
  - return
- otherwise continue normal library UI startup

Also:

- override `onNewIntent(intent)`
- `setIntent(intent)`
- call `tryBootEsdeIntent(intent)`

### 3. Manifest update

Add an intent filter to `MainActivity` for the custom ES-DE boot action.

No native manifest work should be needed on `Emulator`.

## Why This Is Enough

Because Vita3K already knows how to boot an installed title once it has the title ID.

The missing feature is not boot execution. The missing feature is Android-side intent ingestion and title-ID resolution from the ES-DE ROM entry.

## Relevant Files

- `android/app/src/main/java/org/vita3k/emulator/MainActivity.kt`
- `android/app/src/main/java/org/vita3k/emulator/Emulator.java`
- `android/app/src/main/AndroidManifest.xml`
- `vita3k/android/jni/main_android.cpp`
- `vita3k/io/include/io/functions.h`
- `vita3k/interface.cpp`

## Notes From Earlier Inspection

- `Emulator.java` already contains:
  - `EXTRA_TITLE_ID`
  - `createLaunchIntent(...)`
  - `resolveCurrentTitleId(...)`
  - `requestRelaunchFromIntent(...)`
- `main_android.cpp` already parses `-r`.
- `MainActivity.kt` currently launches titles only from the internal apps list:
  - `launchApp(titleId, appTitle)`

So the cleanest implementation is to make `MainActivity` translate the ES-DE ROM entry into the same `launchApp(...)` path Vita3K already uses internally.

## Suggested Next Step

Implement only the Android contract + `.zip` resolver first.

Do not touch native boot code unless testing proves a relaunch edge case.
