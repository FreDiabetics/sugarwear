# Optional Nightscout treatment enrichment

Sugarlicious continues to use the read-only AndroidAPS broadcast as its primary therapy source.
Nightscout is optional and is queried only for `/api/v1/treatments.json`; CGM entries are never
requested or passed to the canonical CGM resolver.

The Mobile app normalizes AAPS and Nightscout events into `TherapyEvent`, merges them through
`CanonicalTreatments`, and persists the bounded Nightscout event history independently of graph
rendering. Stable identifiers are preferred; otherwise kind, a 30-second timestamp tolerance and
the relevant insulin/carb amount are required to match. AAPS values always remain primary.

Supported Nightscout authentication modes are a SHA-1 `api-secret` header and an access token.
Secrets are encrypted with an Android Keystore AES/GCM key and excluded from settings backup and
diagnostic output. Synchronization occurs on app entry at no more than one attempt per 15 minutes,
or explicitly through the connection-test action. A failure only updates Nightscout status and
does not block AAPS ingest, Wear synchronization, curves, CGM, or the G7 collector.
