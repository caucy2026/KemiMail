# KEMI Release Evidence Template

Copy this template into the access-controlled release record for every candidate. Do not store credentials, account identifiers, message content, keystores, signing properties, or unsanitized logs here.

## Candidate identity

- Version name / code:
- Git commit:
- Source branch:
- Candidate APK/AAB SHA-256:
- Build workflow run:
- Build timestamp (UTC):
- Release owner:
- Security owner:

## Supply-chain evidence

- Dependency Guard run:
- Dependency baseline diff reviewed by:
- KEMI SBOM artifact URL / artifact digest:
- KEMI SBOM file SHA-256:
- CycloneDX Gradle plugin version:
- CycloneDX CLI version / binary SHA-256:
- Fluid Attacks SCA run:
- Fluid Attacks SAST run:
- CodeQL run:
- OpenSSF Scorecard result reviewed:
- Blocking findings: None / links

## Build and signing evidence

- Release build command/workflow:
- Signing performed by authorized release workflow/operator:
- Signed artifact SHA-256:
- Mapping file SHA-256 and restricted-storage location:
- Keystore/signing material uploaded to CI artifacts: No

## Regression evidence

- Completed KEMI matrix location:
- API 23 result:
- API 31 dual-screen tablet result:
- API 34 result:
- API 35 KEMI result:
- Fresh-install result:
- In-place-upgrade result:
- Crash/ANR review:
- Privacy evidence review:
- Open Critical/High defects: None / links

## Exceptions and decision

- Exceptions (severity, evidence, mitigation, owner, expiry, approvers):
- Known issues included in release notes:
- Release owner decision: Approve / Reject
- Security owner decision: Approve / Reject
- Decision timestamp (UTC):

