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
| TikTok / YouTube / Instagram / TeraBox | YouTube and Instagram public media metadata use local yt-dlp via youtubedl-android; TeraBox/TikTok remain resolver-backed | URL/CDN validation regression tests added for YouTube and Instagram; Instagram changes pushed, CI/device verification not awaited | YouTube/Instagram device-level extraction and downloads, EJS runtime coverage, output format compatibility; TeraBox/TikTok resolver runtime |
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


## External implementation research (2026-11-08)

The current resolver implementation is not production-reliable: it depends on hard-coded public Piped/Invidious/Cobalt/loader.to instances, an Instagram embed HTML pattern plus third-party converters, and several unverified TeraBox resolver endpoints. These endpoints are not stable service contracts. Empty catches also hide which upstream failed and why.

### Public projects reviewed

| Project / source | Useful engineering approach | License / integration constraint |
|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [yt-dlp EJS guide](https://github.com/yt-dlp/yt-dlp/wiki/ejs) | Maintained extractor registry, format selection, metadata, retries, and external JavaScript challenge runtime for current YouTube extraction | Review yt-dlp and bundled component licenses before bundling. EJS requires a supported JS runtime and solver scripts; a plain HTTP call is not equivalent |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | Android wrapper pattern for running yt-dlp locally, with optional FFmpeg and aria2 | GPL-3.0; adding it as a library can impose GPL obligations on distribution. Do not copy or bundle until the app's licensing strategy is explicitly compatible |
| [NewPipe](https://github.com/TeamNewPipe/NewPipe) / NewPipe Extractor | Extractor-per-service architecture and site parsing when no suitable official API exists | GPL-3.0; NewPipe's own README warns against publishing NewPipe or forks on Google Play. Treat as a reference unless licensing/distribution is reviewed |
| [parth-dl](https://github.com/parthmax2/parth-dl) | Instagram public-post extraction with layered public paths, rate limiting, retry/backoff, resumable transfer, and structured errors | MIT, but Python package; adapt the architecture or use a separately isolated runtime rather than copying Python implementation into Kotlin |
| [Instaloader](https://github.com/instaloader/instaloader) | Mature Instagram media and metadata model; documents the difference between public content and session-required content | MIT; authenticated modes require the user's own valid session. Do not collect passwords or silently reuse browser credentials |
| [Paidguy/terabox-api](https://github.com/Paidguy/terabox-api) | Multi-strategy public-share resolution, typed error responses, expiring-link recovery, and HLS proxy pattern | MIT, but requires a self-hosted Cloudflare Worker and an account session cookie. Do not embed a personal cookie or depend on an unknown public deployment |
| [Android WebViewClient reference](https://developer.android.com/reference/android/webkit/WebViewClient) | `shouldInterceptRequest` can observe many network resource requests made by an embedded page | Does not observe `javascript:` or `blob:` URLs and only sees the initial URL on redirects; must combine with safe DOM/player instrumentation and explain unsupported cases |
| [Media3 download guide](https://developer.android.com/media/media3/exoplayer/downloading-media) | Dedicated DownloadService/DownloadManager model for managed background downloads and supported HLS/DASH media | Use only for media the user is authorized to download; DRM-protected streams remain unsupported |

### Additional Android reference: SnapNet

[hoodmoshla/SnapNet](https://github.com/hoodmoshla/SnapNet) is the strongest directly comparable Android implementation found in this pass. Its README describes a Jetpack Compose Android app using yt-dlp through bundled CPython, a runtime-updatable yt-dlp zipapp, QuickJS-ng for EJS challenge solving, FFmpeg for merging separate audio/video streams, and a foreground service for background downloads. It reports test results for YouTube format listing with and without QuickJS and a real 720p video+audio merge. It also documents a roughly 150 MB storage requirement and the limits of cookie-gated, region-locked, and DRM-protected media.

SnapNet is GPL-3.0 and builds on youtubedl-android (also GPL-3.0). Its README explicitly requires corresponding source availability when distributing builds. This is a strong technical blueprint, but it is **not** a drop-in dependency for IO Tools until the repository's own licensing/distribution model is confirmed. Do not copy SnapNet's code, binaries, app name, branding, or UI. A clean-room engine adapter can be prototyped against the upstream yt-dlp/youtubedl-android stack after a license decision.

### Implementation decisions

1. **YouTube:** replace the brittle chain of anonymous public API instances with a maintained extractor engine. First prototype yt-dlp Android integration in an isolated branch and measure APK size, ABI coverage, runtime support (including EJS), startup, metadata, format selection, and a permitted test download. Compare NewPipe Extractor as an alternative only after GPL-3.0 and distribution implications are reviewed. Never label a stream as MP4 unless the extractor reports a compatible muxed format or the app successfully merges separate audio/video streams.
2. **Instagram:** implement public-post support only, with structured extraction results, layered fallbacks, retry/backoff, and clear login-wall/private-content errors. Do not pretend that an embed page or generic converter guarantees access. Do not automate login or bypass private-account restrictions.
3. **TeraBox:** stop treating unknown third-party resolver endpoints as dependable. Prefer a user-controlled/self-hosted integration based on documented public-share behavior, with explicit session setup if required, secrets stored server-side, expiry recovery, and typed upstream errors. No session cookie may be hard-coded in the APK or committed to Git.
4. **UC Media Sniffer:** change from fetching static HTML only to a controlled in-app browser capture flow. Collect network resource URLs and player metadata, classify direct MP4/WebM and HLS/DASH manifests, deduplicate and validate candidates, and preserve the required request headers/referrer where allowed. Explicitly report that blob URLs, DRM, login-protected media, and inaccessible cross-origin resources may not be downloadable.
5. **Shared transfer layer:** validate response status, content type, byte signature, size, and final file before reporting success. For HLS/DASH, use a manifest-aware downloader rather than saving the manifest as if it were an MP4. Keep cancellation, progress, retry limits, and MediaStore error propagation.
6. **Per-feature workflow:** fix one tool at a time, add deterministic unit tests and mock-server integration tests, run the full unit/build pipeline, inspect logs and audit that feature, then move to the next tool. Real-device validation remains a separate release gate.

### Research limitations

This is a source/repository review, not proof that every upstream resolver currently works. Platform internals and public endpoints change frequently. No external repository code has been copied into IO Tools in this research pass; any future dependency must have its license, maintenance activity, Android compatibility, binary size, and transitive dependencies reviewed before adoption.


## YouTube engine integration (2026-11-08)

The legacy chain of public Piped, Invidious, Cobalt, and loader.to resolvers has been replaced on the working branch with local yt-dlp execution through `io.github.junkfood02.youtubedl-android:library:0.18.1` and its FFmpeg module.

Implemented:
- Initialize yt-dlp and FFmpeg from `MainActivity`.
- Request metadata and the format list using yt-dlp's JSON output.
- Store the extractor's format ID rather than an expiring direct CDN URL; resolve the selected format again at download time.
- Download into app-private cache, then publish the completed file through `MediaStoreSaver`.
- Add GPL-3.0 `LICENSE` and third-party notices.

Important remaining YouTube work:
- The integration does not yet bundle the QuickJS-NG native runtime and yt-dlp EJS solver assets described by SnapNet. Current YouTube extraction may therefore return incomplete formats or fail when the platform requires an external JavaScript challenge solver. Do not advertise complete YouTube compatibility until this is bundled and tested.
- The wrapper dependency's bundled yt-dlp/Python version and Android ABI coverage must be verified from the resolved AAR and on actual devices.
- The current download integration reports completion via Toast but does not yet publish yt-dlp progress into the shared Download Manager progress state.
- Test public, non-DRM media with user authorization on real ARM64 and 32-bit ARM devices; validate merged audio/video, audio extraction, cancellation, and low-storage failure.

The current CI run is the first compile gate for this integration. A green Gradle build proves dependency and source compatibility only; it does not prove YouTube extraction or real media download. See [SnapNet's documented QuickJS/EJS architecture](https://github.com/hoodmoshla/SnapNet) and [yt-dlp EJS requirements](https://github.com/yt-dlp/yt-dlp/wiki/ejs).


### YouTube engine integration progress

- The repository already contains a GPL-3.0 `LICENSE` file. The selected `youtubedl-android:0.18.1` library and FFmpeg runtime dependencies are declared in `app/build.gradle.kts`.
- The Android activity now initializes both `YoutubeDL` and `FFmpeg` before exposing downloader screens, and reports initialization failures instead of silently continuing.
- Each YouTube download now uses an isolated cache directory and a process ID passed to yt-dlp, so cancellation targets the correct process and stale files cannot be mistaken for a fresh download.
- This integration is not yet certified end-to-end. The current GitHub Actions run must pass, followed by on-device verification of metadata extraction, format listing, MP4 muxing, MP3 extraction, cancellation, and MediaStore output.


### CI compile failure follow-up (2026-11-09)

The first yt-dlp integration CI run exposed two Kotlin API mismatches:
- FFmpeg is published in package `com.yausername.ffmpeg`, not `com.yausername.youtubedl_android`. The activity import now uses the package declared by upstream source.
- YoutubeDL.execute accepts `(request, processId, callback)` (and a redirect-stream overload); the service now passes the process ID as the second argument so cancellation targets the same execution.

Both source fixes are committed on the working branch. The next GitHub Actions run is required to verify the correction; do not treat the YouTube tool as build-verified until the run passes. Upstream API reference: https://github.com/yausername/youtubedl-android/tree/0.18.1.


## YouTube downloader — focused repair pass (2026-11-09)

- Replaced dependence on anonymous hosted resolver APIs with local yt-dlp extraction through the GPL-3.0 Android wrapper already declared in Gradle.
- Metadata extraction uses yt-dlp JSON output; selected format IDs are resolved again at download time because CDN URLs expire.
- Restored yt-dlp's default partial-file behavior instead of disabling `.part` files. This prevents incomplete output from being treated as a finished download.
- Disabled optional thumbnail, info JSON, and subtitle sidecar output so the output directory contains the intended media artifact.
- Added explicit MIME mappings for MP4, WebM, MKV, MOV, M4V, and common audio formats. Unknown output extensions now fail visibly rather than being mislabeled as MP4.
- Added unit regression tests for supported YouTube URL forms and rejection of untrusted hosts / malformed IDs.
- Remaining verification: run CI on this revision, then test metadata and actual video/audio downloads on physical Android devices. YouTube EJS challenge solving and ABI/runtime packaging still require device-level confirmation. A green compile is not proof of extractor success.


## Instagram media extractor — focused repair pass (2026-11-09)

- Removed the brittle Instagram embed HTML regex parser and hard-coded SnapInsta/Cobalt public resolver calls.
- Metadata extraction now runs through the locally integrated yt-dlp engine, using JSON metadata and extractor-provided formats rather than assuming a third-party endpoint returns a working direct URL.
- Added support for extractor `entries` so carousel posts can be represented as multiple media items.
- Direct media candidates are accepted only over HTTPS from `cdninstagram.com` / `fbcdn.net` hosts. This prevents arbitrary page URLs or unrelated hosts from being passed into the download manager.
- Preserved the actual supported media extension (MP4/MOV/WebM and JPEG/PNG/WebP) and map it to the corresponding MIME type instead of labeling every video as MP4 and every photo as JPEG.
- Added regression tests for Instagram shortcode parsing, untrusted host rejection, and HTTPS media CDN allowlisting.
- Private/login-gated or deleted posts are reported as unsupported; this implementation does not automate login or bypass account restrictions.
- Remaining verification: CI compile/unit tests and device-level tests against public, authorized Reels, single-photo posts, and carousel posts. yt-dlp's Instagram extractor can change with platform updates, so no universal availability claim is made.


## TeraBox downloader — focused repair pass (2026-11-09)

- Removed all four hard-coded third-party resolver endpoints. The app no longer sends user share URLs to anonymous resolver services or silently swallows resolver failures.
- Added an adapter for the self-hosted [Paidguy/terabox-api Cloudflare Worker](https://github.com/Paidguy/terabox-api), using its documented `GET /api/resolve?link=...` response envelope and first `files[]` item.
- Added a Worker endpoint field in the app. The endpoint is stored locally as a non-secret setting; the optional API key is masked in the UI and kept in screen memory only.
- Enforced HTTPS for the configured Worker endpoint and required returned `download_url` / `stream_url` resources to use the same HTTPS origin. This prevents a malformed Worker response from redirecting the download manager to an unrelated host.
- Parsed the Worker error codes into user-readable messages, including unauthorized, rate-limited, password-required, verification-required and expired-session cases.
- Preserved the original TeraBox share URL for sharing; download output now uses the Worker-provided MIME type rather than assuming every file is MP4.
- Added regression tests for TeraBox share URL hosts/schemes and secure Worker endpoint validation.
- Setup requirement: deploy your own Worker, configure `TERABOX_COOKIE` as a server-side secret, optionally configure `API_KEY`, then enter the HTTPS Worker origin in the app. Never put a TeraBox session cookie into the APK, app UI, source code or Git repository.
- Remaining verification: deploy and test a Worker against an authorized public share, including metadata response, file bytes, Range/resume behavior, and expired-link recovery. The Android integration deliberately does not claim to work until a Worker endpoint is configured.
