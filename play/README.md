# Google Play Release Checklist

## Build Artifact

Upload this Android App Bundle to Play Console:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Version

- Application ID: `info.yoshimov.instantdailymemo`
- Version code: `1`
- Version name: `0.1`
- Target SDK: `35`
- Min SDK: `23`

## Signing

This project is configured to sign release builds when `keystore.properties` exists.

Ignored local files:

- `keystore.properties`
- `keystores/upload-keystore.jks`

Back up both files securely. Losing the upload key may block future updates unless Play Console upload key reset is available for the app.

## Commands

```powershell
$env:JAVA_HOME='C:\bin\jdk-17.0.6+10'
gradle lintRelease
gradle bundleRelease
```

## Play Console Inputs

- Release notes: `play/release-notes/`
- Store listing draft: `play/store-listing-ja.md`, `play/store-listing-en.md`
- Privacy policy draft: `play/privacy-policy.md`
- Data safety notes: `play/data-safety.md`

Before publishing, replace the contact placeholder in the privacy policy with the real developer contact.
