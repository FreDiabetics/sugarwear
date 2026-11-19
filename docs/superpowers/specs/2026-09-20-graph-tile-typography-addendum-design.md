# Graph, therapy tile and typography addendum design

## Intent

This addendum records the new requirements supplied after the master stability candidate. It keeps the existing canonical CGM, resolver, collector, transport and signal-policy work intact and changes only behavior that the new prompt explicitly tightens.

## Design

- Mobile graph layers share one time-to-X transform. Now inset derives from visible point radius, target steps use the same world coordinates exactly once, and the 250 mg/dL reference is mapped to the rounded plot's upper tangent height without an arbitrary screen offset.
- CGM, IOB, COB and insulin activity retain independent vertical axes. The existing IOB maximum becomes the configured upper requirement for both the IOB ring and ordinary IOB graph mode. COB ring maximum is a separate persisted setting. COB graph bounds remain dynamic and include visible COB plus relevant carb-entry headroom of 50 g.
- Mobile therapy indicator presentation becomes the platform-neutral semantic source for value formatting and ring progress. Wear ProtoLayout renders the same semantics as equal-diameter circular indicators in deterministic one/two/three-item layouts.
- SugarWear app typography remains authoritative. Shared semantic Wear typography tokens define size, weight and fallback behavior for app cards and every G7 collector tile; platform rendering stays native.
- No polling, alternate data source or second graph model is introduced. Every change is covered by a failing regression test before implementation.

## Acceptance

The supplied master prompt sections 14–25 and 30–36 are authoritative. Previously completed master-stability requirements remain regression constraints. Installation occurs only after the full project gate, WFF validation and DEX-free verification succeed.
