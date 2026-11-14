# AndroidAPS classic watchface provenance

Source: `nightscout/AndroidAPS`, branch `dev`, commit
`18101c8a2c0204a08d417f3d5fbac3e9ceae380f`. The referenced classic
watchface resources are unchanged from the initially inspected
`593e78fd475536b7ab1bd11c21522ff07d41c131` baseline.

The Circle, Digital Style and Custom/Standard ports are derived from the
corresponding AGPL-3.0-or-later Kotlin classes, Android layouts, vector
resources and unchanged preview images in the AndroidAPS Wear module.

Changes: executable rendering and therapy actions were removed. Layouts were
re-expressed as declarative WFF v1 resources whose default complication
providers point to this project's read-only Wear application. Circle's ring
uses WFF time expressions; Digital Style and Standard are constrained to the
platform maximum of eight complication slots.

License: GNU Affero General Public License v3.0 or later. See the repository
root `LICENSE` and `LICENSES/AndroidAPS-AGPL-3.0.txt`.
