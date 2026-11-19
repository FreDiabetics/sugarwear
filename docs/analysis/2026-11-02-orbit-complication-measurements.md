# Orbit complication geometry

Read-only reference: user-selected com.amoledwatchfaces.ultra2, favorite280, 450-unit viewport. Only numeric geometry is used; third-party source/artwork stays in temporary diagnostics.

The initial shared tick exporter used ApeX's10-tick grid and its longer selected stroke. Orbit's reference instead uses12 ticks at20,25,...75 degrees, normal total length15 and selected total length21, width5. Round caps are included in those lengths. The corrected Orbit exporter subtracts half the stroke width from the centerline endpoints so caps do not protrude outside the199.5-unit outer radius. The selected tick extends inward; the color gradient remains anchored to the actual arc.

Outer text uses28-unit reference size (32 in512), radius165 (187.73), anchors344/78/166 degrees withEND/END/START alignment. The prior upper anchors338/72 were too far from their endpoints; upper-right numeric size30 was undersized. Icons already match the28-unit reference boxes and radial centers; their explicitly requested upright orientation is retained.

Inner captions already match26-unit reference type (30 in512) and the127/84-unit radii within rounding. Lower round gauge arc/radius/stroke/trennluecke already match; its ring stays unchanged. Text fields now use the measured36-unit value and26-unit ranged title, with title/icon-dependent positions and native autosizing. Short text places icons/titles according to delivered fields, instead of reusing the ranged layout. Round custom foreground icons retain the measured system-icon centers but use68% of their padded image boxes to match visible ink, just as the ApeX measurement pass distinguishes foreground artwork from padded system assets.

Colors, providers, slot identities and accepted circle-indicator geometry are retained. Compilation, native schema checks, targeted Mobile preview tests and hardware comparison pass. The canonical native10:08:32 preview now includes the corrected ticks/track and full3.457 steps text.

The dark upper-right track remains visible beneath ticks in every tick mode, including the full-track indicator variants. Its existing theme tone and60/255 alpha are preserved.

Palette restoration supersedes the Orange experiments above:76 original named combinations from both measured reference faces, one unchanged trio per name, plus the explicitly retained Olive and pure white. Where the originals differ slightly, the original ApeX trio is canonical. Original Neon Orange isFFCCB1 / FF935C / FF5F0D. Existing Olive IDs14 (Orbit) and61 (ApeX) retainD4DAB6 / A4AF73 /758544; the original Proto Green receives a free ID to avoid replacing the accepted Olive. Monochrome families follow hue order, neutral colors follow, and11 multicolor combinations finish the catalog. Synthetic soft/bold permutations and invented combinations are removed.
