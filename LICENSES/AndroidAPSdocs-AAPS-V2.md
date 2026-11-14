# AAPS V2 custom watchface notice

- Upstream: `openaps/AndroidAPSdocs`
- Repository baseline: `master` at `30622415cac9923b77aba8d9ee2d8f08972bf9bf`
- Source file: `docs/_static/ExchangeSiteCustomWatchfaces/AAPS_V2.zip`
- Source blob SHA: `3c8b9c1bd136e01285440bf0006199d404299b8b`
- Repository license: GNU Affero General Public License version 3
- Original design metadata: Andrew Warrington, created 2017
- Custom Watchface adaptation metadata: Philoul, 2023
- Original ZIP contents: `CustomWatchface.json`, `CustomWatchface.png`

The local WFF module preserves the original 400×400 layout hierarchy and uses
the original PNG only as its picker preview. Dynamic therapy fields are
implemented with eight WFF Complication slots, which is the platform maximum.
The original executable Custom Watchface renderer is not copied.

