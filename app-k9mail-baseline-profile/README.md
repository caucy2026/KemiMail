# KEMI Mail startup performance

This module contains the repeatable cold-start benchmark and the Baseline/Startup Profile generator for KEMI Mail.
The benchmark reports both time to initial display (TTID) and, for an already configured account, time to full display
(TTFD) after the first message list has been rendered.

Use a physical device with a release-like KEMI Mail account configuration. Generate the profiles with:

```shell
./gradlew :app-k9mail:generateKemiReleaseBaselineProfile
```

Run the startup benchmark with:

```shell
./gradlew :app-k9mail-baseline-profile:connectedKemiBenchmarkReleaseAndroidTest
```

Keep the generated files under `app-k9mail/src/kemiRelease/generated/baselineProfiles/` in version control. The app
also contains conservative starter rules in `src/main/baseline-prof.txt` and the KEMI release variant's
`generated/baselineProfiles/startup-prof.txt` so release builds are optimized before the first device-generated
profile is checked in.
