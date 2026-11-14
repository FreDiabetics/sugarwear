# Wear Root-Cause Final Design

The supplied typography, insulin-activity, graph-tile and lifecycle prompts are the binding specification.

The existing `feature/master-system-stability` candidate is continued because it is based exactly on current local `main` and already contains the traced shared graph, independent metabolic scaling, supported ProtoLayout inline-image delivery, tile typography calibration, revision deduplication, boot/Bluetooth recovery and targeted update fan-out. It is not treated as proven merely because code exists: focused renderer, scale, transport and lifecycle tests plus the complete gate remain required.

For startup/reconnect performance, keep durable DataItem plus low-latency Message delivery, but fingerprint the successfully committed payload. Both deliveries enter one IO mutex; a byte-identical second delivery is rejected before JSON decoding. JSON decoding, history merge, persistence and fan-out must never execute synchronously in the Wearable callback. The fingerprint is persisted only after the canonical state is stored, so process death cannot mark an uncommitted state complete.

No polling, watchdog, history deletion or data-integrity relaxation is introduced. Hardware-only startup, crash, memory and long-run claims remain explicitly open when the watch is unavailable.
