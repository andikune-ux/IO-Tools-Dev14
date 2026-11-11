# IO Invitation Studio

Advanced visual editor prototype for digital invitations. This app is intentionally isolated in `/invitation-studio` so the existing Android IO Tools application remains untouched.

## Current prototype

- Visual invitation canvas with selectable sections.
- Add invitation blocks: cover, text, countdown, event, gallery, RSVP, love story, gift, guestbook and button.
- Reorder sections by dragging layer rows.
- Desktop/mobile canvas preview.
- Theme color, typography, cover height and cover image controls.
- Media file picker (session listing).
- Guest slug manager with generated hostname previews.
- Local draft autosave, JSON export/import.
- Publish confirmation UI with honest demo-mode notice.

## Domain model

The intended hostname shape is:

`<guest-slug>.<project-slug>.<platform-domain>`

Example: `keluarga-budi.andri-sinta.io-unedeveloper.edgeone.dev`.

This is a hostname preview only in the prototype. Real nested subdomains require DNS wildcard coverage for the relevant depth, hosting custom-hostname support, TLS certificates, and a server/edge router that resolves Host headers to the correct project and guest record. A provider's single-level wildcard may not cover nested names. The primary free domain is kept as-is.

## Current limitations

This is a static frontend prototype. Data is stored in the current browser using localStorage. No multi-user authentication, cloud database, durable media storage, real RSVP delivery, DNS changes, or production publishing is connected yet. File uploads are not persisted to cloud storage. Publish button currently saves a local draft and explains the deployment limitation.

## Run locally

Open `index.html` in a modern browser, or serve this directory with any static HTTP server.

## Next production milestones

1. Add authentication and tenant/project database.
2. Add durable object storage for uploaded media.
3. Implement safe page rendering and invitation routing.
4. Verify EdgeOne Pages hostname and wildcard capabilities.
5. Add a domain resolver for project + guest slugs, collision checks, and TLS.
6. Add actual publishing, ZIP export/import, GitHub sync, and RSVP API.
7. Add automated tests and deployment preview.

Do not place provider API keys, GitHub tokens, or secrets in browser JavaScript.
