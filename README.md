# IO Tools Dev14

Android utility toolkit built with Kotlin and Jetpack Compose.

## Application

- Application ID: `dev.andikune.iottools`
- Minimum Android: API 24
- Target / Compile SDK: API 36
- Build system: Gradle 9.3.1
- JDK: 17

## Build locally

1. Install Android SDK Platform 36 and JDK 17.
2. Ensure Android SDK environment variables are configured.
3. Run:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Release signing

Never commit a release keystore or signing password.

Configure these GitHub Actions repository secrets to enable signed release builds:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded release keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_PASSWORD`: private-key password.
- `ANDROID_KEY_ALIAS`: key alias (defaults to `upload` when unset).

If release signing secrets are absent, CI builds and uploads a debug APK only. It does not create a fake release identity or publish an unsigned APK as a release.

The release keystore must remain stable across app updates. Back it up securely outside the repository.

See [docs/TOOL_AUDIT.md](docs/TOOL_AUDIT.md) for the functional audit matrix, known limitations, and release gate.

## Tool modules

- QR and barcode generation / scanning
- Image compression, resizing, conversion, EXIF inspection and image-to-PDF
- PDF merge, split, rotate, protect and recompression
- File hashing and batch rename
- Network diagnostics (IP, DNS, TCP latency, TLS certificate and WHOIS)
- Privacy tools (passwords, encrypted notes and TOTP)
- Media metadata, extraction, trimming and frame capture
- Web-to-APK project export and WebView container
- Media link helpers and download workflows

Some online services and media platforms restrict automated downloading. Tools must respect account permissions, site terms, copyright, privacy, and technical access controls; they must not bypass DRM, paywalls, authentication, or platform restrictions.

## Security notes

- Cleartext HTTP is disabled globally by default.
- WebView file URL access is disabled in the container.
- FileProvider paths are limited to app cache and the IO-Tools download folder.
- Android backup is disabled to avoid exporting locally stored vault data.
- Android 7–9 requests the legacy storage permission because those versions require it for public Downloads/Pictures output.
- TOTP secrets are encrypted at rest with an Android Keystore AES-GCM key; legacy plaintext secrets are migrated.
- Release signing credentials are supplied only through CI secrets.
