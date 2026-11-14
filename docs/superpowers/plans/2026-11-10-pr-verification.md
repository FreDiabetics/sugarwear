# Pre-merge verification: Sugarlicious and SugarWear

## Scope and decisions
- User requests all open PRs verified/merged, with unfinished implementation from chats `Umsetzungsstand Installation` and `Installation vorbereiten` completed first. Hardware testing/analysis is expressly deferred; do not access devices or change sensors.
- Initial remote state: Sugarlicious PR117, head 5fbd1a4e, base e49ef21c. SugarWear has no open PR; pairing/unlink/diagnostics PRs9,11,12,13 already merged, main fa81d83e.
- Both local checkouts initially clean. Fast-forward SugarWear main; preserve all source/assets and other local branches. No destructive cleanup.

## Audit against chats
- Exact sensor unlink: CgmSensorUnlink targets only consistent recorded session/credential addresses; never globally removes all advertised G7 bonds. Failure pauses collector, keeps retry identity and reports failure. Confirmed removal purges credentials/session/diagnostics/ledger/retired-address state while retaining glucose DB. Existing unlink/companion regression suites cover this.
- Pairing, discovery, auth-phase diagnostics and bond/GATT inventory are already integrated in SugarWear main. Real pairing/auth/reconnect/alarms, two-sensor and battery checks remain hardware work, not represented as verified here.
- Canonical provider/resolver path exists with 15-minute mobile failover, distinct two-reading recovery, retained history and explicit denied/unavailable/malformed outcomes. Existing resolver/provider/source-transition and stale/NO_SOURCE tests must pass.
- Package/provider visibility is already correctly declared in Wear. Initial suspicion from an incomplete excerpt was disproven by parsing the whole manifest and a passing authority-contract test. No visibility/manifest change is needed; keep the regression contract.
- neverForLocation is explicit and bounded hardware scan filters are present. Firmware-specific advertisement filtering cannot be established from code alone; retain current non-location contract and defer controlled Samsung/Pixel observation rather than add speculative scan workarounds.

## Execution / verification
- [x] Visibility contract test confirms the existing authority query. No failing behavior or runtime fix asserted.
- [ ] Review PR117 plus provider-visibility regression contract, all existing reference/parser/asset tests, no protocol/crypto/timing changes.
- [ ] Full static/test/build/lint/WFF gates for Sugarlicious and pinned-source SugarWear sequentially. No local override may stand in for the pinned SugarWear dependency.
- [ ] Independent read-only review and current-head remote CI, then merge matching verified SHA; no bypass of checks or branch protection.
- [ ] Refresh local main branches and re-check both remote PR inventories and merge CI results. Record remaining hardware scope honestly.

Official visibility contract: https://developer.android.com/training/package-visibility/declaring

Independent review found additional reproducible safety gaps: recovery published first Mobile when Watch disappeared; recovery high-water timestamp could regress and reset across no-source/direct-only reads; direct provider rows were not limited to current sensor/session; invalid-receipt rows reappeared through watch-only fallback; Bluetooth OFF could falsely confirm bond absence. Added failing regressions (4 core + 6 provider; disabled radio on API35/36) and fixes. Canonical resolver/provider tests then passed; new production changes remain outside crypto and BLE timing. Dedicated safety PRs are used, separate from Orbit artwork.