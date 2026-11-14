# AndroidAPS AAPS V4 Watchface notice

- Upstream: `nightscout/AndroidAPS`
- Branch and commit: `dev` at `18101c8a2c0204a08d417f3d5fbac3e9ceae380f`
- The copied WFF resources are unchanged from the initially inspected
  `593e78fd475536b7ab1bd11c21522ff07d41c131` revision.
- Upstream license: GNU Affero General Public License version 3
- License text: `LICENSES/AndroidAPS-AGPL-3.0.txt`
- Upstream files:
  - `wear/watchfacepush/template/watchface.xml`
  - `wear/watchfacepush/src/main/res/drawable-nodpi/preview.png`
  - `wear/watchfacepush/src/main/res/drawable-nodpi/preview_circular.png`
  - `wear/watchfacepush/src/main/res/xml/watch_face_info.xml`
  - `wear/watchfacepush/src/main/res/xml/watch_face_shapes.xml`
- Local destination: `watchfaces/aaps-v4/src/main/res`

Local modifications to `watchface.xml` are limited to replacing the five
AndroidAPS package/provider placeholders with the independent app's glucose,
IOB, COB, glucose-graph and AAPS-status Complication provider component names.
Packaging metadata and the application ID are independent. The preview images
and WFF geometry were copied unchanged.
