# ARYAN VPN (Android, Kotlin, Jetpack Compose, sing-box)

A VLESS over WebSocket + TLS client with English/Farsi UI. Source package: `com.aryan.vpn`.

## Build status

This archive contains **source only**. It has not been built or run on Android. No APK is included. The sandbox used for this review has no Android SDK, Gradle executable, Kotlin compiler, network access, or `libbox.aar`, so Android compilation, device tests, and sing-box API verification could not be performed here. The local bridge in `PlatformBridge.kt` must still be verified by compiling against the exact engine artifact.

## Build requirements

Install JDK 17, Gradle 8.7, Android SDK 34, Android NDK, Go, and gomobile. Build the engine from an existing local sing-box checkout pinned to v1.9.7:

```sh
./scripts/build-libbox.sh /path/to/sing-box
```

The script refuses any checkout not exactly at `v1.9.7`, runs the documented `make lib_install` and `make lib_android` targets, and copies the resulting `libbox.aar` into `app/libs/`. The engine binary is omitted from this archive. `app/libs/libbox.aar` is ignored by Git. If using another engine version, update and compile-test the `PlatformBridge` signatures and config schema together.

The archive does not include Gradle wrapper binaries. With Gradle 8.7 installed, initialize the wrapper once, then run:

```sh
gradle wrapper --gradle-version 8.7
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

`preBuild` now stops with a targeted message if the required AAR is missing. Run `python3 scripts/check-source.py` for the available offline integrity checks. Install and exercise the APK on both IPv4-only and dual-stack Android networks before release.

## Profiles and secrets

No server UUID, VLESS link, or default profile is bundled in source or in the app. Import the server's VLESS link on the Profiles screen. Imported profile data, including the URI/UUID, is encrypted with an AES-GCM key held by Android Keystore; it is not written as plaintext SharedPreferences data. The old local plaintext profile format is migrated and removed on first load. If secure migration fails, the app fails closed and asks the user to import the link again.

The previous source archive contained a live UUID. Treat that credential as exposed and rotate it at the server; do not reuse it in this project or a published APK. The app now starts with an empty profile list, so import newly generated links after rotation. Keep the repository private until secrets and release licensing are reviewed. Signing keys and `local.properties` are excluded by `.gitignore`.

## IPv6 endpoint fallback

For an imported profile whose endpoint is an IPv6 literal, the client now uses the TLS SNI hostname (or WebSocket Host hostname) as its connection target when available. The OS/sing-box can resolve an IPv4 A record on IPv4-only networks. This is a DNS-backed fallback, not a guarantee: it only works if the supplied hostname resolves to the same service and has an IPv4 route. The endpoint check resolves on the underlying non-VPN network, tries returned addresses, and verifies TLS certificate and hostname.

## Licensing

The sing-box engine is GPLv3. Distribution of an app linked with it carries source-code and corresponding-license obligations. This source archive does not make a legal determination or publish the project under a license on the author's behalf. Before distributing, choose and apply the appropriate license to the app's own code, include required third-party notices and corresponding source/build materials, and verify the obligations for the exact sing-box build.

## Existing behavior

The app includes DNS presets/custom DNS, a foreground VPN service, separate endpoint/proxy/tunnel latency checks, connection verification through a real HTTP request, auto-reconnect, traffic stats, diagnostics with redaction, and English/Farsi locale switching. These features remain unverified until the project is compiled and exercised on devices.

## Checks

| Check | Result |
|---|---|
| Offline source checks (`python3 scripts/check-source.py`) | Passed |
| Source credential scan | Passed after removing the bundled VLESS UUID and profiles |
| Kotlin/JVM unit tests | Not run: no Gradle/Kotlin compiler available |
| Android build | Not run: no Android SDK, Gradle executable, or libbox AAR |
| libbox bridge/API compatibility | Unverified: requires building the pinned sing-box engine |
| Install, VPN permission, connect, DNS, latency, reconnect | Not run: requires Android device/emulator and a valid server profile |
| IPv4-only fallback | Implemented but needs a real endpoint and IPv4-only network test |
