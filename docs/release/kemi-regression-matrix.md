# KEMI Regression Matrix

Complete this matrix for every production candidate and after a high-risk dual-screen, account, storage, sync, or security change. Use dedicated test accounts and sanitized evidence only.

## Required environments

|   ID   |         Form factor         |         Android          |      Distribution       |                                    Required scope                                     |
|--------|-----------------------------|--------------------------|-------------------------|---------------------------------------------------------------------------------------|
| ENV-01 | Emulator or reference phone | API 23 minimum supported | KEMI release-equivalent | Install, launch, account setup, core mail smoke, permission behavior                  |
| ENV-02 | Dual-screen KEMI tablet     | API 31                   | KEMI release-equivalent | Full dual-screen, rotation, process-restart, upgrade, privacy, and performance matrix |
| ENV-03 | Reference phone or emulator | API 34                   | KEMI release-equivalent | Core mail, notification, background, locale/theme, accessibility smoke                |
| ENV-04 | Reference phone or emulator | API 35 target            | KEMI release-equivalent | Core mail, notification, background restrictions, permission and SBOM smoke           |

Record the exact build SHA-256, commit, application version, device model, OS build, distribution, network, locale, theme, and whether the run was a fresh install or an in-place upgrade. Do not clear app data for the upgrade row.

## Dual-screen and mode coverage

For `ENV-02`, mark each cell Pass, Fail, Blocked, or Not Applicable and attach only sanitized screenshots or issue links.

|  ID   |                            Scenario                             |                                               Expected result                                               |
|-------|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| DS-01 | First launch after install or upgrade                           | Immersive mode is selected only when no saved choice exists; existing accounts and settings remain intact   |
| DS-02 | Switch Immersive → Smart from the lower-screen entry            | Selection changes immediately, content remains usable, and the choice persists after process restart        |
| DS-03 | Switch Smart → Immersive                                        | One continuous canvas is restored without duplicate panes, stale selection, or navigation loss              |
| DS-04 | Smart mode message selection                                    | Upper screen shows the selected message; lower screen keeps the list and available actions                  |
| DS-05 | Smart mode empty/loading/error states                           | Each pane shows a clear independent state; no blank trap or blocked navigation                              |
| DS-06 | Fold, unfold, rotate, and resize during list and reader states  | Layout reflows without crash, overlap, clipped controls, or lost selected message                           |
| DS-07 | Back, deep link, notification open, and task restoration        | Navigation returns to the correct pane/state without duplicate activities                                   |
| DS-08 | Compose, reply, forward, and draft return                       | Editor remains usable across the two screens and draft state survives mode/layout changes                   |
| DS-09 | Move, archive, delete, mark read, star, and undo                | Action affects the intended message and both panes converge on the same state                               |
| DS-10 | Search and folder/account switching                             | Results and selection belong to the visible account/folder; no stale cross-account content appears          |
| DS-11 | Offline → online and Wi-Fi transition                           | Queued operations, banners, refresh, and selection recover without repeated action or data loss             |
| DS-12 | Large list, long HTML message, attachments, and font scale 200% | Scrolling, focus, rendering, and controls remain responsive and accessible                                  |
| DS-13 | Light/dark theme and Simplified Chinese/English                 | Both panes update consistently; text fits and contrast remains acceptable                                   |
| DS-14 | Smart Assistant availability                                    | No AI entry is visible while no approved provider is configured; no AI network request occurs               |
| DS-15 | Crash-diagnostic sanity                                         | Normal launch creates no false crash report; forced test-fixture reports contain no account or mail content |

## Core mail and release coverage

Run the upstream [Release Manual Testing Checklist](testing-checklist.md), with at least these KEMI exit conditions:

- Existing signed-version upgrade preserves accounts, selected mode, preferences, drafts, and local cache.
- Fresh install requests no unexpected permission and starts without crash or ANR.
- IMAP receive/read/search/compose-draft/reply/move/archive/delete flows pass; a real send is performed only with approved test accounts.
- Notification open/actions, background sync, offline queueing, certificate errors, attachments, and external-content policy pass.
- No log, crash report, screenshot, SBOM, or issue attachment exposes credentials, addresses, subjects, bodies, attachments, or account identifiers.
- The KEMI release has a validated SBOM tied to the candidate commit.

## Result record

For each failed or blocked row record: matrix ID, expected and actual result, reproducibility, severity, owner, issue, workaround, and target fix. Critical or High failures block release. Any Not Applicable result requires a reason and approver.
