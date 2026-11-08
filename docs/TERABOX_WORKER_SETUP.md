# TeraBox Worker Setup

The Android app intentionally does not call anonymous public TeraBox resolver services. It connects to a Cloudflare Worker deployment that you control.

## 1. Deploy the Worker

Use the upstream project and review its current instructions and license before deployment:

- Repository: https://github.com/Paidguy/terabox-api
- API reference: https://github.com/Paidguy/terabox-api/blob/main/docs/API.md
- License: MIT

The Worker requires Node.js 20+ and a Cloudflare account. Follow the upstream deployment guide rather than copying a random public Worker URL.

## 2. Configure the TeraBox session on the server

In the Worker project, configure the TeraBox session cookie as a Cloudflare secret:

```bash
wrangler secret put TERABOX_COOKIE
```

Paste the cookie only into the Wrangler secret prompt. Do not paste it into IO Tools, a GitHub issue, source code, build configuration, or the Android app.

Deploy the Worker:

```bash
npm install
wrangler login
npm run deploy
```

If you enable API-key protection in the Worker, configure its `API_KEY` secret using the upstream deployment instructions. The Android screen supports an optional API key and sends it as a Bearer token.

## 3. Configure IO Tools

1. Open **TeraBox Downloader**.
2. Enter the HTTPS Worker origin, for example `https://your-worker.workers.dev`.
3. If the Worker requires an API key, enter it in the masked API key field.
4. Paste a public TeraBox share URL and select **Generate Link**.
5. Confirm the returned file metadata, then select **Unduh File**.

The app stores only the Worker origin as a local preference. The API key is kept in app memory for the current screen session and is not written to source code or SharedPreferences.

## 4. Supported behavior and limits

- The app currently displays and downloads the first file returned by the Worker. Multi-file folder browsing is not yet exposed in the UI.
- Public shares are supported by the upstream Worker. Password-protected shares currently return a clear unsupported message; the app does not collect or guess share passwords.
- Download URLs must be HTTPS and must use the same origin as the configured Worker.
- The Worker may require a valid TeraBox session cookie and may be affected by TeraBox verification, rate limits, or upstream changes.
- Test your deployment with the upstream project's smoke-test instructions before relying on it.

Never use this integration to access files without authorization or to bypass TeraBox access controls.
