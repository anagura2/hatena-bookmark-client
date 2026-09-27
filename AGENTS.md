# AGENTS.md

## Project overview

This repository is a native Android Hatena Bookmark client for personal use. It uses Kotlin, Jetpack Compose, RSS feeds, WebView, and Hatena OAuth 1.0a.

## Important files

- `app/src/main/kotlin/jp/hatena/bookmarkclient/MainActivity.kt`
  - Main Compose UI, navigation, category feeds, article WebView, comments, star counts, My Bookmarks, read history, and bookmark editor.
- `app/src/main/kotlin/jp/hatena/bookmarkclient/HatenaOAuthClient.kt`
  - OAuth 1.0a flow, token persistence, authenticated profile/bookmark requests, and bookmark posting.
- `app/src/main/AndroidManifest.xml`
  - Application metadata and launcher icon.
- `app/build.gradle.kts`
  - Android/Compose configuration and local OAuth credential injection.
- `README.md`
  - Local OAuth setup.
- `local.properties`
  - Local-only SDK path and Hatena Consumer Key/Secret. Never commit or expose this file.

## Build and run

Environment:

- Android Studio
- Embedded JDK: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`
- Android SDK: `$HOME/Library/Android/sdk`
- JDK target: 17
- compileSdk/targetSdk: 35
- minSdk: 26

Configure `local.properties`:

```properties
sdk.dir=/Users/<user>/Library/Android/sdk
hatena.consumerKey=YOUR_CONSUMER_KEY
hatena.consumerSecret=YOUR_CONSUMER_SECRET
```

Build:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug --no-daemon
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

ADB is usually not on `PATH`:

```bash
$HOME/Library/Android/sdk/platform-tools/adb devices -l
```

Install on the Pixel 8 Pro when connected:

```bash
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s <device-id> shell monkey -p jp.hatena.bookmarkclient 1
```

The commonly used emulator is `emulator-5554`, AVD name `Pixel_8_Pro`. When both the phone and emulator are connected, always pass `-s`.

After a successful debug build, automatically deploy the APK to the emulator without waiting for a separate request:

1. Check connected devices with `$HOME/Library/Android/sdk/platform-tools/adb devices -l`.
2. Prefer `emulator-5554` when it is connected.
3. Install with `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Launch with `adb -s emulator-5554 shell monkey -p jp.hatena.bookmarkclient 1`.
5. If `emulator-5554` is not connected, report that deployment was skipped. Do not install to a physical device unless explicitly requested.

## Authentication

The app uses Hatena OAuth 1.0a with the `oob` PIN flow. The requested scopes are:

```text
read_public,write_public,read_private,write_private
```

After changing scopes, clear the existing login from the app and authenticate again. Tokens are currently stored in plain SharedPreferences for this prototype. Consumer Secret is embedded in the APK, so this is not suitable for public distribution without a backend redesign.

## Current behavior

- Popular/new/category article feeds use Hatena RSS.
- Categories are displayed in a looping, animated carousel and the selected category is centered with an accent underline.
- Opening an article stores its URL as read history in SharedPreferences and changes the list title color.
- Article comments are loaded from `entry/jsonlite`; comment star counts are fetched in batches of 10 through `https://s.hatena.com/entry.json`.
- Comment star posting uses the safe WebView flow: the logged-in user taps the pale star and opens the specific Hatena Bookmark comment page. No unofficial star-posting API is used.
- Article detail starts comment/star-count prefetching while the detail screen is open.
- My Bookmarks displays 50 items at a time and loads/enriches later pages when scrolling.
- Bookmark creation uses the authenticated Hatena Bookmark REST endpoint and restores the existing comment when editing an already-bookmarked URL.
- Theme follows the Android system light/dark setting.

## API notes

Common public feeds:

- `https://b.hatena.ne.jp/hotentry.rss`
- `https://b.hatena.ne.jp/entrylist/all.rss?sort=new`
- `https://b.hatena.ne.jp/hotentry/all.rss`
- `https://b.hatena.ne.jp/hotentry/it.rss`
- `https://b.hatena.ne.jp/hotentry/social.rss`
- `https://b.hatena.ne.jp/hotentry/game.rss`

Comment star count flow:

1. Fetch `https://b.hatena.ne.jp/entry/jsonlite/?url=...`.
2. Build a comment permalink from the entry `eid`, bookmark user, and `YYYYMMDD` timestamp.
3. GET `https://s.hatena.com/entry.json?uri=...`.
4. Count `entries[0].stars`.

The official Hatena Star API documents retrieval, not external star posting. Do not replace the safe WebView flow with an undocumented endpoint without explicit approval.

## Development conventions

- Keep changes focused and preserve existing UX.
- Do not commit `local.properties`, credentials, APKs, keystores, or generated build output.
- Use `apply_patch` for manual edits.
- Validate Kotlin changes with the existing Gradle assemble task.
- Keep Japanese UI text consistent with the current app.
