# Development — LZCY Personal Fork

This document describes how to build and maintain the LZCY personal fork of Stash Android Player.

- Repository: [lzcy1326/stash-player-android](https://github.com/lzcy1326/stash-player-android)
- Development branch: `main`
- Releases and APK downloads: [Releases](https://github.com/lzcy1326/stash-player-android/releases)
- Bug reports: [Issues](https://github.com/lzcy1326/stash-player-android/issues)
- Personal versioning and upstream synchronization: [FORK_VERSIONING.md](FORK_VERSIONING.md)

## Get the Source

```bash
git clone https://github.com/lzcy1326/stash-player-android.git
cd stash-player-android
```

The `origin` remote is the personal fork. The optional `upstream` remote points to the original project and is used to incorporate upstream changes. The [upstream synchronization workflow](.github/workflows/sync-upstream.yml) checks for changes automatically; see [FORK_VERSIONING.md](FORK_VERSIONING.md) for its behavior.

## Build From Source

Install JDK 17 or newer and Android SDK 35. Configure the Android SDK with `ANDROID_HOME` or an untracked `local.properties` file, then run:

```bash
./gradlew --no-daemon :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`. The debug APK is written to `app/build/outputs/apk/debug/`. Its application ID is `gomeng.dev.stashplayer.debug`, so it can coexist with the release app.

## Signed Release Build

The LZCY release uses the original application ID `gomeng.dev.stashplayer` and the LZCY personal signing key. To build a signed release APK, provide these environment variables:

```bash
export ANDROID_SIGNING_KEYSTORE_BASE64="<base64 encoded keystore>"
export ANDROID_SIGNING_STORE_PASSWORD="<store password>"
export ANDROID_SIGNING_KEY_ALIAS="<key alias>"
export ANDROID_SIGNING_KEY_PASSWORD="<key password>"
./gradlew --no-daemon :app:assembleRelease
```

The build also accepts a keystore path and signing credentials via untracked `local.properties`; see `app/build.gradle.kts` for the supported property names. Without a complete signing configuration, the release APK is unsigned. Verify the output using the Android SDK `apksigner verify --verbose --print-certs` before distribution.

The personal fork and the upstream app use different signing keys. A release signed with the LZCY key can update an existing LZCY release, but cannot overwrite the upstream app with the same application ID. Back up app data before uninstalling an incompatible installation.

Keep the LZCY keystore and its credentials securely backed up. Never commit signing files, passwords, APKs, AABs, or build outputs.

## Versioning and Releases

Change `versionName` and `versionCode` in `app/build.gradle.kts` only when the maintainer explicitly requests a release, following [FORK_VERSIONING.md](FORK_VERSIONING.md). Routine fixes and local test builds keep the current version. Document release changes in [CHANGELOG.md](CHANGELOG.md), run the relevant checks, and build and verify the signed APK. Publish the matching `v`-prefixed tag and APK to this fork's [Releases](https://github.com/lzcy1326/stash-player-android/releases); do not reuse a published tag.

## Tech Stack

- Kotlin
- Jetpack Compose and Material 3
- AndroidX Navigation
- Media3/ExoPlayer
- Coil
- Room
- DataStore
- Retrofit, OkHttp, and Moshi

## Validation

Useful local checks:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:lintDebug
git diff --check
```

On the maintainer's Windows environment, Android Lint has previously failed with a UAST/MessageBus disposal error. If this recurs, record the failure and run the other checks; a build that skips `lintVitalRelease` has not passed release Lint.
