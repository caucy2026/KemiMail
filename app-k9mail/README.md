# KEMI Mail

KEMI Mail is the KEMI product family email application. It is based on the open-source Thunderbird for Android and
K-9 Mail codebase and retains the Apache License, Version 2.0 attribution and third-party notices.

For the complete environment setup, build, APK inspection, dual-screen tablet installation, and acceptance workflow,
see the [KEMI Mail Build, Installation, and Acceptance Guide](../docs/developer/kemi-build-install-guide.md).

## Standard APK builds

KEMI Mail has one supported product flavor: `kemi`. Use only these project entry points for distributable APKs:

```shell
# Local testing, signed with the Android Debug key
./gradlew :app-k9mail:kemiDebugApk

# Startup performance testing, release-optimized and signed with the Android Debug key
./gradlew :app-k9mail:kemiPerformanceApk

# Production release, requires .signing/k9.release.signing.properties and its KEMI keystore
./gradlew :app-k9mail:kemiReleaseApk
```

The version is read from `app-k9mail/build.gradle.kts`. The standard outputs are:

- `build/outputs/kemi/debug/KEMI-Mail-v<version>-debug.apk`
- `build/outputs/kemi/performance/KEMI-Mail-v<version>-performance-debug.apk`
- `build/outputs/kemi/release/KEMI-Mail-v<version>.apk`

The `-debug` suffix is mandatory for Debug-signed packages. Never rename a Debug-signed package to look like a
production release. The performance APK uses the same application ID and Debug signing key as the standard Debug APK,
so it can be installed over an existing Debug build without clearing account data. It is minified, resource-shrunk,
and non-debuggable to reproduce release startup behavior. Generic Android `assemble*` tasks are internal build-system
inputs and are not KEMI delivery artifacts.

## Maintenance

### Public source and local configuration

Signing archives, keystores, signing properties, local environment files, and environment-specific relay deployment
scripts are not distributed with the source. Obtain your own signing identity and deployment configuration through
approved private storage. Test fixtures remain part of the repository. The relay runbook uses placeholder deployment
addresses; the application build configuration still defines the actual runtime endpoints.

The GitLab `dev` branch retains the existing development history. The GitHub `opensource` branch starts from a clean
source snapshot with no earlier commits. Publish future snapshots as descendants of `opensource`; never merge `dev`
into it or push `dev`, all branches, or a mirror to GitHub, because the older private history contains signing material.
Ignoring a file or deleting it in a new commit does not remove it from earlier commits.

### F-Droid

Upstream K-9 Mail metadata for F-Droid is maintained outside this customer product module. Any KEMI Mail store
listing must use KEMI-owned signing, support, privacy, and distribution information. The metadata format is described
in the [All About Descriptions, Graphics, and Screenshots](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/)
and [Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/).
