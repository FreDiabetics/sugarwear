# WFF validation

`validate.ps1` fetches the official Google Watch Face Format validator at a
pinned commit, builds it with this project's Gradle wrapper, and validates all
`watchfaces/*/src/main/res/raw/watchface.xml` files as WFF version 1.

Run from the project root:

```powershell
pwsh -File tools/wff-validator/validate.ps1
```

The script deliberately checks the validator's text result because the
validator can return process exit code 0 even when one input document fails.

