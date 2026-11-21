# Android release signing

IO-Tools Dev14 uses one persistent Android release keystore. Do not generate a replacement keystore during normal CI builds.

## Required GitHub Actions repository secrets

Configure all four secrets in **Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded contents of the persistent JKS keystore.
- `ANDROID_KEY_ALIAS`: alias of the release key.
- `ANDROID_KEYSTORE_PASSWORD`: JKS store password.
- `ANDROID_KEY_PASSWORD`: private-key password.

The workflow must fail closed for production publishing when any secret is missing or invalid. A CI-only validation build must never be presented as a production release.

## Consistency and verification

- Keep the keystore, alias, and passwords stable across releases.
- Never commit the keystore, Base64 payload, or passwords to the repository.
- Verify the APK signer certificate SHA-256 fingerprint against the retained release certificate before publishing.
- Keep the APK version metadata and Git tag/release version synchronized.
- Back up the keystore and passwords securely. Losing the private key prevents signing compatible updates with this signing identity.

The currently configured release certificate fingerprint is documented in the private signing package distributed to the repository owner; do not publish private key material or passwords in this repository.
