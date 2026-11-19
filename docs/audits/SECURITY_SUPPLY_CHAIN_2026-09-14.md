# Phase 13 - Security, privacy, and supply chain

Date: 2026-09-14

## Application security findings

- No hard-coded application/API secret was found by the repository pattern scan. The cryptographic private-key match is runtime key material, not a checked-in credential.
- Mobile and Wear applications disable backup and cleartext traffic. Code-free watchfaces also disable both.
- Nightscout configuration requires HTTPS, rejects user-info/query-bearing base URLs, applies connect/read timeouts, and stores authentication material with Android Keystore AES-GCM. Settings backup excludes the secret.
- File sharing uses a non-exported `FileProvider` with URI grants.
- PendingIntents are immutable except the BLE scan PendingIntent, which must be mutable for Android to attach scan result extras.
- Tile and complication services are exported only behind the platform bind permissions.
- Direct SugarWear reading broadcasts are protected by the app signature permission.
- Data Layer payloads use typed, versioned decoding and reject malformed commands at their consumers.

## Accepted external-source boundary

The AndroidAPS status receiver must be exported to consume the established third-party broadcast contract. Because AndroidAPS is not signed with this application's key, a signature permission cannot be added without breaking the integration. Payload validation limits malformed input, but another local app can imitate the action. This is an explicit trust boundary; canonical validation/freshness must continue treating the broadcast as untrusted input.

## Supply-chain findings

- Dependabot alerts are currently disabled for the GitHub repository; the live API returned HTTP 403.
- Local `gitleaks`, `trivy`, `syft`, and `grype` binaries are not installed.
- Existing CI builds/tests artifacts and validates WFF/code-free APKs, but has no dependency-review or SBOM job.
- The static secret scan found no credential candidate requiring rotation.

## Planned CI controls

Phase 15 adds SHA-pinned GitHub dependency review for pull requests and an SPDX SBOM artifact. Secret scanning is not wired through the Gitleaks organization action because it requires repository organization licensing/secret configuration; adding a knowingly non-runnable required check would make CI less reliable. Repository-native or licensed secret scanning remains an administrator action.

## Threat model summary

Protected assets are CGM/therapy integrity, sensor credentials, Nightscout credentials, retained history, alarm decisions, and update artifacts. Principal trust boundaries are third-party local broadcasts, Google Wear Data Layer peers, BLE advertisements/GATT callbacks, Nightscout TLS, exported platform-bound services, and signed APK upgrades. Safety controls are canonical identity/freshness, sensor/session separation, typed decoding, generation ownership, encrypted secrets, no backups, immutable PendingIntents, and signed artifact validation.
