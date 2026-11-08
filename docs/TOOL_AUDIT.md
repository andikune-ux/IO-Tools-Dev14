# IO Tools — Functional Audit

This document distinguishes implemented code from verified runtime behavior. A source-code implementation is not considered fully verified until its tests pass and the feature is exercised on a real Android device.

## Tool inventory

| Tool | Implementation found | Current verification | Remaining checks |
|---|---|---|---|
| QR & Barcode | ZXing QR/Data Matrix and 1D barcode generation plus camera scanner | EAN-13/Code 39/Code 128 validation regression tests added; camera not device-verified | Camera permission, autofocus, low-light, scanner compatibility and generated output on devices |
| Image Studio | Bitmap compression, resize, conversion, image-to-PDF, EXIF reading/removal | Resize memory-limit regression tests added; processing moved to IO | Large images, orientation variants, transparency, storage output and EXIF stripping on devices |
| PDF Tools | PDFBox merge/split/rotate/AES-256 protect/compress; Android PdfRenderer for raster recompression with scratch-file buffering | Range/password/rotation/compression-bound regression tests added; device PDF compatibility not verified | Encrypted inputs, malformed files, very large PDFs, page order, scratch storage and output compatibility |
| File & Hash | Streaming SHA-256/MD5/SHA-1/SHA-512 and CRC32; SAF batch rename with collision preflight | Filename-safety regression tests added; hash stream loop corrected | Very large files, provider-specific rename failures, read-only folders and partial rename behavior |
| Network Recon | Public IP lookup, DNS, TCP connect latency, TLS certificate inspection and WHOIS | Host/port and WHOIS injection regression tests added; public IP response size bounded | Offline/captive portal behavior, IPv6 across OEM stacks, slow DNS and provider rate limits |
| Privacy Vault | Secure password generation, versioned PBKDF2/AES-GCM notes, TOTP/HMAC-SHA1 with Android Keystore | Password/Base32/RFC 6238/crypto regression tests added; TOTP persistence is explicit | Keystore invalidation/restore, legacy migration, wrong-passphrase UX and device-specific crypto providers |
| Audio & Video | MediaExtractor/MediaMuxer metadata, AAC/AMR demux, keyframe trim with rotation metadata, scaled frame capture | Timestamp validation regression tests added; device codec behavior not verified | Codec/container combinations, variable frame rate, rotation, long media, muxer support and device codec compatibility |
| Web-to-APK | Generated Gradle project, manifest, standalone WebView container and ZIP export | Added config validation and generated runtime-permission regression tests; initial download URL and filename validation hardened | Real project import/build/install, HTTPS sites, file picker, camera/mic/location permission prompts and DownloadManager redirect behavior |
| TikTok / YouTube / Instagram / TeraBox | YouTube, Instagram and TikTok public media extraction use local yt-dlp; TeraBox supports a user-controlled Cloudflare Worker resolver | URL/CDN, safe format selector, carousel routing and Worker endpoint regression tests added; audio/video merge selection hardened | YouTube/Instagram/TikTok device-level extraction and downloads; TikTok watermark-free format availability; TeraBox Worker deployment, auth, public-share and download behavior |
| UC Media Sniffer | Static HTML scan plus embedded WebView network-request and video-element capture; progressive, HLS and DASH classification including extensionless MIME-based CDN detection | URL/media classification and extensionless MIME regression tests added; WebView and download behavior not device-verified | Player interaction, request headers/referrer, HLS/DASH extraction, dynamic players, blob/DRM limitations and false positives |
| Download Manager | OkHttp streaming, progress state, MediaStore output, share/play, cancellation, per-hop HTTPS redirect validation, exact MIME and file-signature checks | URL/MIME/signature/extension helper regression tests added; runtime transfer tests not yet run | Real HTTP interruption, content-length mismatch, disk full, duplicate names, redirect chains, and Android 7–9 storage behavior |

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
2. **Instagram:** use the local yt-dlp extractor for public posts, Reels, and carousel entries. Re-resolve media at download time rather than relying on expiring CDN URLs. Return clear login-wall/private-content errors. Do not automate login or bypass private-account restrictions.
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
- The current download integration exposes an active/canceling state and a cancel action, but does not yet publish actual yt-dlp progress into the shared Download Manager progress state.
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
- Added unit regression tests for supported YouTube URL forms, rejection of untrusted hosts / malformed IDs, and safe format selector construction. Metadata requests now canonicalize all accepted URL forms to a standard HTTPS watch URL; credentials, unapproved subdomains and unusual ports are rejected.
- Format metadata now records whether a selected video stream already contains audio. yt-dlp adds `bestaudio` only for video-only formats, avoiding unnecessary duplicate audio tracks; format IDs are validated before being used in yt-dlp selectors.
- The selected download process ID is now wired to a visible Cancel action. Cancellation is checked before and after yt-dlp execution, and each per-process cache directory is removed after success, failure or cancellation.
- Remaining verification: run CI on this revision, then test metadata and actual video/audio downloads on physical Android devices. YouTube EJS challenge solving and ABI/runtime packaging still require device-level confirmation. A green compile is not proof of extractor success.


## Instagram media extractor — focused repair pass (2026-11-09)

- Removed the brittle Instagram embed HTML regex parser and hard-coded SnapInsta/Cobalt public resolver calls.
- Metadata extraction now runs through the locally integrated yt-dlp engine, using JSON metadata and extractor-provided formats rather than assuming a third-party endpoint returns a working direct URL.
- Added support for extractor `entries` so carousel posts can be represented as multiple media items.
- Direct media candidates are accepted only over HTTPS from `cdninstagram.com` / `fbcdn.net` hosts. This prevents arbitrary page URLs or unrelated hosts from being passed into the download manager.
- Preserved the actual supported media extension (MP4/MOV/WebM and JPEG/PNG/WebP) and map it to the corresponding MIME type instead of labeling every video as MP4 and every photo as JPEG.
- Carousel state is carried on each extracted media item so the selected carousel entry is downloaded with `--playlist-items` instead of accidentally treating it as a standalone post. Output type is checked against the selected photo/video item before saving.
- Added regression tests for Instagram shortcode parsing, untrusted host rejection, HTTPS media CDN allowlisting, media-type classification, and carousel item state.
- Private/login-gated or deleted posts are reported as unsupported; this implementation does not automate login or bypass account restrictions.
- Remaining verification: CI compile/unit tests and device-level tests against public, authorized Reels, single-photo posts, and carousel posts. yt-dlp's Instagram extractor can change with platform updates, so no universal availability claim is made.


## TeraBox downloader — focused repair pass (2026-11-09)

- Removed all four hard-coded third-party resolver endpoints. The app no longer sends user share URLs to anonymous resolver services or silently swallows resolver failures.
- Added an adapter for the self-hosted [Paidguy/terabox-api Cloudflare Worker](https://github.com/Paidguy/terabox-api), using its documented `GET /api/resolve?link=...` response envelope and first `files[]` item.
- Added a Worker endpoint field in the app. The endpoint is stored locally as a non-secret setting; the optional API key is masked in the UI, kept in app memory only, and forwarded in the Authorization header for both resolve and download requests.
- Enforced HTTPS for the configured Worker endpoint and required returned `download_url` / `stream_url` resources to use the same HTTPS origin. This prevents a malformed Worker response from redirecting the download manager to an unrelated host.
- Parsed the Worker error codes into user-readable messages, including unauthorized, rate-limited, password-required, verification-required and expired-session cases.
- Preserved the original TeraBox share URL for sharing; download output now uses the Worker-provided MIME type rather than assuming every file is MP4.
- Added regression tests for TeraBox share URL hosts/schemes and secure Worker endpoint validation.
- Setup requirement: deploy your own Worker, configure `TERABOX_COOKIE` as a server-side secret (for example `wrangler secret put TERABOX_COOKIE`), optionally configure `API_KEY` (the app sends `Authorization: Bearer ...`), then enter the HTTPS Worker origin in the app. Never put a TeraBox session cookie into the APK, app UI, source code or Git repository.
- Remaining verification: deploy and test a Worker against an authorized public share, including metadata response, file bytes, Range/resume behavior, and expired-link recovery. The Android integration deliberately does not claim to work until a Worker endpoint is configured.


## Web-to-APK Builder — focused repair pass (2026-11-09)

- Fixed generated and in-app WebView permission handling: camera/microphone PermissionRequest objects are retained while Android runtime permission prompts are shown, then granted only after the OS returns a successful result. Canceled page requests clear pending state; denied or unsupported WebView resources remain denied.
- Geolocation is no longer granted before Android permission approval. The origin callback is retained until the runtime permission result arrives and revoked if the page navigates; only configured fine/coarse location permissions are requested.
- Coarse-only configuration now works in the standalone activity loaded from assets/config.json; it no longer silently checks only the fine-location toggle.
- WebView-triggered downloads require an initial public HTTPS URL without embedded credentials or local/IP-literal hostnames. Suggested filenames are reduced to a safe basename before passing them to Android DownloadManager.
- Web-to-APK export and standalone launch validation now check HTTPS URL structure, reject local/IP-literal targets and URL credentials, validate package names against Java keywords, bound app/version metadata, check version-code limits, validate HEX colors, and reject multiline/oversized User-Agent overrides. Fine and coarse location choices are preserved when opening the in-app standalone preview.
- Added regression tests for generated permission-flow source, fine/coarse manifest toggles, public HTTPS configuration, invalid targets, package names, version codes and theme colors.
- Remaining verification: build and install an exported project; exercise file chooser, camera/microphone and fine/coarse geolocation permission grants and denials; test DownloadManager with cross-origin redirects and authenticated sites. Android DownloadManager follows redirects internally, so only the initial URL is validated by the generated wrapper.

## UC Media Sniffer — focused repair pass (2026-11-09)

- Replaced static-HTML-only assumptions with a visible embedded WebView capture flow. `shouldInterceptRequest` and `onLoadResource` observe eligible media requests; page-finished JavaScript inspection reads ordinary `video` / `source` element URLs.
- Added explicit URL normalization and HTTP(S)-only checks. Non-HTTP schemes, credential-bearing target URLs, localhost variants, local hostnames, shortened/numeric IPv4 spellings, malformed ports and IP-literal targets are rejected for navigation and captured media. Unsafe HTTP(S) subresource requests and redirects are blocked in the embedded WebView.
- Added media classification for progressive MP4/M4V/MOV/WebM/FLV, HLS `.m3u8`, and DASH `.mpd`. The classifier accepts MIME hints for extensionless URLs when a caller has one; Android WebView's default `shouldInterceptRequest` does not expose successful response MIME headers, so extensionless CDN URLs without a MIME hint are not guaranteed to be detected. Tracking/advertising paths and non-media assets are filtered.
- Added a browser capture panel so the user can press Play to trigger network requests when static HTML contains no media URL.
- Progressive media downloads preserve a sanitized referrer and the app user agent. Cross-origin referrers are reduced to the origin, query strings/fragments are removed, and HTTPS-to-HTTP referrers are suppressed. Static HTML reads are capped at 4 MB. Normal webpage requests follow redirects through the DNS-filtered client; security-sensitive media signature probes retain a separate no-auto-redirect client and validate each hop. HLS/DASH manifests are passed to yt-dlp/FFmpeg; the manifest itself is no longer saved and mislabeled as an MP4 file.
- The capture WebView is destroyed when the screen leaves composition, preventing an abandoned page/player from retaining resources in the background.
- `blob:` URLs are deliberately ignored because they are not standalone network media URLs. The sniffer does not export WebView cookies or replay login sessions; authentication-gated media may be detected but is not guaranteed to download. DRM-protected streams and decryption are not supported.
- Added Robolectric regression tests for URL normalization, direct/HLS/DASH classification, extensionless MIME-based detection, and rejection of blob/tracking URLs.
- Remaining verification: test the embedded player on physical Android devices and representative public/authorized pages; confirm HLS/DASH yt-dlp extraction, referrer handling, FFmpeg merge, and MediaStore output. The WebView capture is not a guarantee that every site's player exposes a downloadable stream.


## Shared Download Manager — focused repair pass (2026-11-09)

- Added HTTPS-only public URL validation. Credentials in URLs, local hostnames, and IP-literal targets are rejected before the request is started.
- DNS answers are filtered to public addresses and redirects are handled manually with a five-hop cap. Every destination is validated as HTTPS/public before the next request is made, and caller-supplied headers are stripped when the redirect changes origin (only the basic User-Agent and Accept headers are retained).
- Requires an exact expected Content-Type (while allowing generic octet-stream responses), rejects HTML and unexpected JSON error bodies, and validates common file signatures (MP4/QuickTime, WebM/Matroska, FLV, MP3/AAC/OGG/WAV, JPEG/PNG/WebP/GIF, PDF and ZIP). A WebM or QuickTime response can no longer be saved under an MP4 MIME label merely because both are video.
- Rejects empty responses and content-length mismatches before publishing the MediaStore item. Incomplete output is aborted rather than reported as a successful download.
- Added a generation ID and synchronized active-call registration so an older cancelled transfer cannot overwrite the progress or active-call state of a newer download.
- Cancellation now updates the visible state immediately. Unknown file types receive a `.bin` fallback instead of being mislabeled as MP4. If the server reports a generic MIME type, the manager now infers the expected MIME from a known filename extension before validating the body and creating the MediaStore item.
- The stream-copy loop handles temporary zero-byte reads without stalling. URL validation also rejects malformed DNS labels and invalid ports before starting a transfer.
- Added regression tests for secure URL validation, exact MIME compatibility and common file signatures.
- Remaining verification: device-level tests for redirects, CDN downloads, unknown content length, interrupted transfers, storage exhaustion, and legacy Android storage permissions. The shared manager still uses one active transfer slot at a time by design.


## TikTok downloader — focused repair pass (2026-11-09)

- Removed the hard-coded TikWM public API dependency. Public TikTok/Douyin metadata and formats are now extracted by the locally integrated yt-dlp engine.
- Added TikTok/Douyin URL validation and HTTPS CDN allowlisting for extractor-provided media URLs.
- The no-watermark video selection explicitly excludes the extractor's `download_addr` format and any format whose note identifies it as watermarked. The selected format ID is validated before it is passed to yt-dlp. If no non-watermarked format is exposed, the app returns an error instead of mislabeling a watermarked stream.
- Video downloads now re-run yt-dlp against the original public TikTok page and select the chosen format together with `bestaudio` when the chosen video stream is video-only. FFmpeg merges the streams into MP4; already-muxed formats are not given a second audio stream. The app no longer saves a video-only CDN stream under an MP4 label.
- Audio download now runs yt-dlp extraction against the original public post URL and converts to MP3 through FFmpeg, rather than assuming an arbitrary direct audio URL is already MP3.
- Sharing uses the original TikTok/Douyin page URL instead of a short-lived CDN link. Preview is restricted to allowed HTTPS media CDN hosts.
- Added regression tests for TikTok/Douyin page URLs, media CDN allowlisting, and safe format-selector construction for muxed versus separate audio/video.
- Remaining verification: device-level tests for short links, public video metadata, true no-watermark format selection, MP4 download, MP3 extraction, and rate-limit/login errors. The extractor may fail when TikTok requires login or changes its anti-bot behavior; no private-content or access-control bypass is implemented.
- Upstream format distinction reference: [yt-dlp TikTok extractor](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/tiktok.py).


## QR & Barcode Studio — focused repair pass (2026-11-09)

- Corrected the EAN-13 placeholder and added validation for either 12 data digits (ZXing calculates the check digit) or 13 digits with a valid checksum.
- Added explicit input checks for Code 39's supported character set and Code 128's ASCII range so invalid data produces a clear message before ZXing runs.
- Fixed one-dimensional barcode sizing: Code 128, Code 39 and EAN-13 now render in a landscape aspect ratio instead of being stretched into a square QR-like image.
- Editing the payload or changing barcode type clears the old generated bitmap, preventing a stale image from being saved after the input changes.
- Added payload bounds for QR, Data Matrix, Code 39 and Code 128 to avoid unbounded matrix allocation; Code 128 now rejects non-printable ASCII control characters. Added JVM regression tests for EAN-13 checksum, supported character sets and payload limits.
- Remaining verification: scan generated QR/barcode images with multiple external scanner apps and test camera permission, focus, and low-light behavior on physical devices.


## Image Studio — focused repair pass (2026-11-09)

- Moved compression, resizing, format conversion, PDF creation and MediaStore writes off the Compose main thread to prevent UI stalls and ANRs.
- Added a load-generation token so a slower earlier image decode cannot overwrite a more recently selected image; old preview state is cleared while a new image loads.
- Disabled image replacement while an operation is running, preventing the source bitmap from being swapped during an active transform.
- Added resize limits of 8192 pixels per side and 16 megapixels total to reduce out-of-memory risk. Source image decoding now also downsamples to at most 4096 pixels per side and 16 megapixels total before allocating the working bitmap.
- JPEG encoding now composites transparent pixels onto a white background instead of leaving the result dependent on implicit alpha handling.
- PDF document resources are closed in a `finally` block, and resized temporary bitmaps are recycled even if encoding fails.
- Added regression tests for accepted/rejected resize dimensions and decoded-image sample sizing for large square and panoramic images.
- Remaining verification: exercise large images, EXIF orientations, transparent PNG-to-JPEG conversion, WebP support and PDF output on physical devices with different memory capacities.


## Audio & Video Lab — focused repair pass (2026-11-09)

- Added metadata-load generation tracking so an older asynchronous file scan cannot overwrite information for a newly selected media file. File size and display-name queries now run off the UI thread.
- Audio demux now bounds its sample buffer to 64 KB–32 MB, checks that samples exist, and selects an output container based on codec: AAC to M4A/MP4 and AMR to 3GP. Unsupported codecs fail explicitly instead of being mislabeled as M4A.
- Fast trim now validates the requested range against actual media duration, requires a video track and at least one video sample, bounds sample buffers, and preserves supported 0/90/180/270-degree rotation metadata.
- Frame capture validates the requested timestamp against media duration, uses scaled frame extraction on Android 8.1+ and caps output frames at 2048 pixels per side. Frame saving runs on IO.
- Added regression tests for trim range, frame timestamp, and overflow validation.
- Remaining verification: test AAC/AMR demux, codec/container edge cases, keyframe trim accuracy, rotation, variable frame rate, and frame capture on physical devices and representative media files.


## PDF Tools — focused repair pass (2026-11-09)

- Replaced whole-document `readBytes()` in split with PDFBox stream loading and `MemoryUsageSetting` scratch-file buffering. Merge/load and output documents also use bounded mixed memory with the app cache directory as scratch storage.
- PDF output is now streamed through a temporary cache file into MediaStore instead of materializing a second full-document byte array. Temporary files are deleted in `finally`.
- Added operation limits: up to 50 merge inputs, 200 split outputs, 5,000 referenced split pages, 2,048 characters of range input, 300 compressed pages, 36–300 DPI, 16 megapixels per rendered page and 8192 pixels per side. Empty-range splitting rejects oversized page counts before allocating one range per page.
- Checked JPEG encoder success and guaranteed rendered bitmap recycling even if a page fails.
- Rotation accepts only 90/180/270 degrees. PDF encryption requires an 8–128 character user password and limits owner password to 128 characters.
- Password fields are masked and cleared after the protection operation. Compression size details are preserved in the completion status.
- Added regression tests for page ranges and split-allocation bounds, rotation, password length, DPI/quality and render dimensions.
- Remaining verification: run merge/split/rotate/AES-256/compression against unencrypted, encrypted, malformed and large sample PDFs on physical Android devices; confirm PDFBox scratch files and MediaStore output under low storage.


## File & Data Hash Engine — focused repair pass (2026-11-09)

- Fixed the file hash stream loop so a zero-byte read is retried instead of being treated as end-of-file; large files continue to be processed incrementally with a fixed buffer.
- Moved SAF file name/size metadata queries off the UI thread and added `try/finally` around hashing state so the picker becomes available again after errors.
- Removed the SHA-512 display truncation so the copy action exposes the complete digest rather than a shortened value.
- Added input length bounds for text hashing, Base64 and URL encoding/decoding to limit accidental memory spikes.
- Batch folder enumeration now runs on IO and uses a generation token to ignore stale folder loads. Rename operations capture an immutable file list and naming configuration before execution.
- Added safe filename sanitization, duplicate-target detection, existing-name conflict preflight and output length limits. The operation refuses to start if it would collide with an existing file.
- Added regression tests for generated batch names and path-separator sanitization.
- Remaining verification: test large-file hashing against known digests, SAF providers that reject rename, read-only folders, and partial failures on physical devices.


## Network Recon — focused repair pass (2026-11-09)

- Added shared hostname normalization using URI and IDN rules; invalid schemes, embedded credentials, control characters, malformed labels and invalid TLS ports are rejected before network operations.
- DNS and TCP latency now use the same normalized host. TCP timing starts separately for each port attempt, so a failed 443 attempt no longer inflates a successful 80 measurement.
- Local interface enumeration now runs on IO instead of the Compose main thread.
- Public IP lookup now accepts HTTPS JSON responses only, disables redirects, applies connection/read timeouts, caps the response at 64 KB and always disconnects the connection.
- TLS inspection parses host and port safely and continues to rely on the platform's normal certificate/hostname validation; it does not install a permissive trust manager.
- WHOIS domain and referral server values are validated as DNS names before connecting or writing to TCP port 43, preventing CR/LF query injection and arbitrary referral hosts.
- Added regression tests for IDN/host normalization, WHOIS domain restrictions and TLS host/port parsing.
- Remaining verification: test DNS A/AAAA, IPv6, TCP timeout/refusal, valid/expired/self-signed TLS certificates, WHOIS referral chains and captive-portal behavior on real networks.


## Privacy Vault — focused repair pass (2026-11-09)

- Password entropy now uses the exact selected character-pool sizes. Selecting no character groups yields no password instead of silently falling back to an unselected pool.
- Secure Notes masks passphrases, bounds note/passphrase input, clears plaintext and passphrase after successful encryption, and clears the passphrase after successful decryption.
- Secure Notes ciphertext is now versioned as `v2` and records the PBKDF2 provider (`PBKDF2WithHmacSHA256` or the explicit compatibility fallback). Legacy `v1` payloads are still attempted with both providers so older notes remain readable across provider differences.
- PBKDF2 password character arrays and PBEKeySpec values are cleared after key derivation. AES-256-GCM authentication remains mandatory; tampered ciphertext fails decryption.
- TOTP Base32 parsing now checks alphabet, length, canonical padding and unused trailing bits; secrets shorter than 80 bits are rejected. Added the RFC 6238 SHA-1 test vector.
- TOTP secret persistence is explicit through Save/Delete actions. Initial load no longer triggers an automatic save that could erase ciphertext after a Keystore failure. Decryption failures preserve the encrypted payload and surface a warning; a permanently invalidated key is rotated only when the user explicitly saves a replacement secret.
- TOTP secrets are masked in the UI and are stored with Android Keystore AES-GCM. Legacy plaintext migration is attempted once; plaintext legacy storage is removed after the migration attempt.
- Added regression tests for password character groups, Base32 encoding/padding, RFC 6238 and Secure Notes encryption/tamper detection.
- Remaining verification: exercise TOTP save/load across process restarts, Keystore invalidation, backup/restore, legacy migration, and Secure Notes v1 decryption on API 24+ devices.


## Shared MediaStore and Stream Probe — focused repair pass (2026-11-09)

- MediaStore name collision checks now use the actual destination collection and relative folder for Downloads, Pictures, Music and Movies instead of always querying Downloads.
- Android 7–9 output files are created with exclusive `createNewFile()` semantics before opening the stream, preventing accidental overwrite races. Filesystem permission errors stop retries immediately.
- Output subdirectories are sanitized to remove path traversal and unsafe characters. Fallback filenames are sanitized too.
- Bitmap output now compresses directly into a pending MediaStore stream instead of allocating an additional full-size byte array. Failed/empty bitmap and stream outputs are aborted.
- Generic `readBytes` now has a 32 MB default limit and rejects oversized reads instead of allocating unbounded memory.
- Shared direct-stream probing now requires HTTPS public URLs, revalidates the final redirect URL, reads only a bounded 512-byte prefix, and validates MP4/WebM/FLV signatures or HLS/DASH manifest markers. HTML, JSON and arbitrary text responses are rejected.
- Removed the fake constant download ID from the native download helper; it no longer claims to return a real transfer identifier.
- Added regression tests for output filename sanitization and secure stream URL validation.
- Remaining verification: exercise MediaStore collection paths and duplicate names on Android 10+, legacy file creation on Android 7–9, FileProvider sharing, and real CDN redirects on devices.


## TeraBox Direct Engine — focused repair pass (2026-11-09)

- Corrected the Worker setup guide to match the implemented multi-file UI and the upstream API's password-protected share support.
- Preserved the leading `1` in path-based share IDs, matching the upstream Worker response contract (`share.id`); added coverage for `surl` query links with and without that marker.
- Hardened bounded Worker response reading: a temporary zero-byte stream read no longer stalls the parser, non-positive limits are rejected, and bodies larger than the configured 2 MB cap fail while a body exactly at the cap remains valid.
- Added user-readable handling for Worker `budget_exhausted`, upstream availability/shape errors, and invalid-parameter errors.
- Added JVM regression coverage for response stream edge cases and share-ID parsing.
- Remaining verification: deploy a user-controlled Worker, run the upstream smoke test against an authorized public file and password-protected share, then verify multi-file folders, Range/resume, API-key auth, expired-link recovery and MediaStore output on an Android device. The app does not claim live TeraBox extraction until a real Worker deployment is configured and exercised.
