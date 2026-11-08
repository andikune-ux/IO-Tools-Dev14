# IO Tools — Functional Audit

This document distinguishes implemented code from verified runtime behavior. A source-code implementation is not considered fully verified until its tests pass and the feature is exercised on a real Android device.

## Tool inventory

| Tool | Implementation found | Current verification | Remaining checks |
|---|---|---|---|
| QR & Barcode | ZXing generation and camera scanner | Existing source review | Camera permission, focus, low-light and supported formats on devices |
| Image Studio | Bitmap compression, resize, conversion, image-to-PDF, EXIF reading | Existing source review | Large images, orientation variants, storage output and EXIF stripping on devices |
| PDF Tools | PDFBox merge/split/rotate/protect/compress; Android PdfRenderer for raster recompression | Existing source review | Encrypted PDFs, malformed files, large documents, page order and output compatibility |
| File & Hash | Streaming SHA-256/MD5/SHA-1/SHA-512 and CRC32; SAF batch rename | Existing source review | Very large files, duplicate target names, read-only providers and rename conflicts |
| Network Recon | Public IP lookup, DNS, TCP connect latency, TLS certificate inspection and WHOIS | Existing source review | Offline state, captive portals, IPv6, slow DNS and provider rate limits |
| Privacy Vault | Secure password generation, AES-GCM secure notes, TOTP/HMAC-SHA1 | Source review; TOTP storage now uses Android Keystore | RFC 6238 test vectors, key invalidation, restore/migration behavior and passphrase UX |
| Audio & Video | MediaExtractor/MediaMuxer metadata, audio extraction, fast trim and frame capture | Existing source review; fast trim starts at a prior sync frame | Codec/container combinations, variable frame rate, rotation, long media and device codec support |
| Web-to-APK | Generated Gradle project, manifest, standalone WebView container and ZIP export | Source review; generated WebView security defaults have unit assertions | Real project import/build/install, HTTPS sites, file picker, camera/mic/location permission prompts |
| TikTok / YouTube / Instagram / TeraBox | Resolver-backed media metadata and download flows | URL host validation covered by unit tests; resolver runtime not verified | External service uptime, rate limits, account/privacy restrictions and direct-link expiry |
| UC Media Sniffer | Parses public page HTML and identifies direct media URLs | Source review | HLS/DASH manifests, authenticated streams, dynamic JavaScript players and false positives |
| Download Manager | OkHttp streaming, progress state, MediaStore output, share/play and cancellation | Source review; cancellation now cancels the OkHttp call | Interrupted network, unknown content length, disk full, duplicate names and Android 7–9 permissions |

## Important behavior and constraints

- Android 7–9 requires the legacy storage permission for public Downloads/Pictures output. The app now requests it on startup for API 28 and below; if declined, saving may fail.
- Android 10+ uses scoped MediaStore output.
- WebView file URL access, mixed content and pop-up windows are disabled. HTTPS should be used for Web-to-APK targets.
- Fast video trim is stream-copy/keyframe based. It does not re-encode, so the actual first frame can be earlier than the requested timestamp.
- Online download resolvers are external dependencies. They can change their API, rate-limit, or stop working without notice. A resolver returning a URL does not prove the resulting file is valid.
- The Cobalt project documentation says hosted instances should not be used by third-party projects without explicit permission; deploy an instance you control or obtain access from its owner.
- Download only content the user owns or is authorized to save. Do not bypass DRM, paywalls, login controls, private-account restrictions or platform access controls.

## Release gate

Do not publish a release APK until all of the following are true:

1. GitHub Actions unit tests pass.
2. Debug and signed release builds complete.
3. The release keystore fingerprint is confirmed to match the app already distributed to users. The previous workflow generated a new keystore during each run, so update-signature continuity must be checked explicitly.
4. Camera, storage, SAF, MediaStore, PDF, media codec and WebView flows are tested on physical Android devices.
5. Resolver-backed features are tested against permitted public test URLs and failures are shown clearly in the UI.
