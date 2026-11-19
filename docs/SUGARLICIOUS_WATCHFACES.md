# Sugarlicious Watchfaces

Both packages are declarative Watch Face Format v1 applications with
`android:hasCode="false"`, separate application IDs, eight customizable slots,
round-display scaling and explicit AOD variants.

| Package | Clock | Default slots |
|---|---|---|
| `app.aapswear.watchface.sugarlicious.digital` | digital | glucose/trend/delta, age, graph, IOB, COB, basal, loop, phone battery |
| `app.aapswear.watchface.sugarlicious.analog` | analog | glucose/trend/delta, age, profile, IOB, COB, basal, loop, phone battery |

The visual system follows the supplied Sugarlicious smartphone reference:
`#050B10` background, `#091117` tiles, `#22313A` borders, cyan structural
accents, fixed green glucose typography and semantic blue/orange/cyan/purple
labels. Glucose color is not recalculated from hard-coded ranges.

The analog hands were drawn specifically for this repository as white,
dark-inset rounded vectors with a cyan second hand. They are not extracted,
traced or copied from Apple. In ambient mode, the second hand and active tile
decorations disappear while hour/minute hands, glucose and low-power dial
marks remain.

Emulator goldens and hashes are under
`docs/test-artifacts/wear-os-6/sugarlicious-0.5.0/watchfaces/`.
