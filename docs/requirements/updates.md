# Update Check

Telling the user that a newer MinDis exists, and getting it installed.

Related design decision: [ADR 010 — the update check offers the platform installer](../adr/010-auto-update.md)
(why MinDis never replaces files inside its own installation), which builds on
[ADR 002 — packaging](../adr/002-packaging.md).

## Requirements

### Notice new versions
`req~update-notice~1`

MinDis tells the user when a newer version has been released, without the user having to watch the
project's repository. The check runs when the application starts and can be switched off; a manual
check is always available.

Covers:
- feat~multilingual-desktop-app~1

### An unasked check stays quiet
`req~update-check-unobtrusive~1`

The check on startup never delays the application and never interrupts the user unless there is an
update: being up to date, having no network, or an unreachable server produce no dialog. A check the
user asked for always answers, including with its failures.

Covers:
- feat~multilingual-desktop-app~1

### Install on confirmation
`req~update-install~1`

When the user confirms an offered update, MinDis downloads the installer for the platform it runs on,
verifies it, and starts it; MinDis then closes so the installation can proceed. The user confirms
twice — once to download, once to close — and unsaved work is never lost to an update.

Covers:
- feat~multilingual-desktop-app~1
- feat~local-data-ownership~1

### A download is verified before it is run
`req~update-verified~1`

A downloaded installer is executed, so it is checked against the checksum published with the release
before MinDis hands it to the operating system. A file that does not match is discarded and reported,
never started.

Covers:
- feat~multilingual-desktop-app~1

## Design

### Running version
`dsn~app-version~1`

`org.mindis.core.update.AppVersion` is a `major.minor.patch` record — the only shape jpackage and MSI
accept, and therefore the only shape a MinDis release carries. `parse` tolerates a leading `v` (the
release tag) and any `-SNAPSHOT` suffix, and returns empty rather than throwing for anything else, so
neither the running version nor a manifest can break the check by being malformed. `currentText()`
reads `org/mindis/core/version.properties`, generated from `gradle.properties` by
`mindis-core/build.gradle.kts`; it is the one place the running version comes from, for the update
check and for the About screen alike. A build without that resource reads as `dev`, and then
`current()` is empty and the check does nothing at all. Unit-tested by `AppVersionTest`.

Covers:
- req~update-notice~1

### Release manifest
`dsn~update-manifest~1`

A release publishes one `latest-<platform>.json` asset per platform (`UpdatePlatform.key()`:
`windows`, `macos`, `linux` — the jpackage target names), listing the released `version`, an optional
`releaseNotesUrl` and the `artifacts`, each with `fileName`, absolute `url`, `sha256`, `size` and
`kind` (`INSTALLER` or `PORTABLE`). `UpdateManifests.parse` ignores unknown fields, so a manifest
written by a newer MinDis still reads; `UpdateManifests.available(manifest, running)` is the whole
decision — newer version *and* an installer for this platform — and returns empty for every "nothing
to offer" case alike. Both are pure, and unit-tested by `UpdateManifestsTest`.

The manifest is written by the `:gui:updateManifest` Gradle task (`WriteUpdateManifest`) after
`:gui:jpackage`, from the packages that build actually produced: it computes each file's SHA-256,
writes a `.sha256` sidecar for any package that has none yet (jpackage writes its own for the
installers), and pins every URL to the release tag (`-PreleaseTag`,
default `v<version>`). CI runs it on each platform runner and attaches the result to the release, so
a platform's packages and the manifest describing them always come from one build.

Covers:
- req~update-notice~1
- req~update-verified~1

### Check, download, hand over
`dsn~update-service~1`

`UpdateService` (core, `@Singleton`) is deliberately thin plumbing around pure helpers:
`check()` fetches this platform's manifest from `releases/latest/download/…` — a URL that resolves
against the newest release, so it never changes — and evaluates it; `download(artifact, progress)`
streams the file into a fresh temporary directory, reports bytes received, verifies it with
`Checksums.verify` and deletes the partial file on any failure, checksum mismatch included;
`install(file)` hands the verified file to the platform via `InstallerLauncher` (`cmd /c start` on
Windows, `open` on macOS, `xdg-open` on Linux — the installer's own UI and elevation prompt, not a
silent install). `isSupported()` is false for an unknown platform or an unknown running version,
which is what a run from sources looks like. Redirects are followed (GitHub serves assets from its
CDN) and both connect and request have timeouts. Nothing here touches JavaFX. Unit-tested by
`ChecksumsTest` and `InstallerLauncherTest`, and end-to-end by `UpdateServiceTest`, which runs the
real HTTP path (fetch, download, verify, 404) against a loopback `ServerSocket` on an ephemeral
port - no network, no fixed port.

Covers:
- req~update-install~1
- req~update-verified~1

### The dialogs
`dsn~update-dialogs~1`

`UpdateCheckController` (GUI) runs every network call on a virtual thread and hops back with
`Platform.runLater` before touching a control. `checkOnStartup()` shows nothing but the offer —
failures go to the log; `checkNow()` (Settings → Updates → "Check now") also reports being up to date
and reports failures. The offer names both versions, links the release notes through `HostServices`,
and leads to a progress dialog, then to "Install and quit". That last step calls the same
unsaved-changes confirmation as the window's close button (`DocumentSession.confirmDropUnsavedChanges`)
before starting the installer and `Platform.exit()` — an update can never discard staged edits.

Covers:
- req~update-install~1
- req~update-check-unobtrusive~1

### The startup check is optional
`dsn~update-preference~1`

`MinDisPreferences.checkUpdatesOnStart` (default on) gates the check in `MinDisApp.start`, which runs
it last, after the window is shown. Because a primitive boolean cannot tell an absent field from a
deliberate "off", the default for preferences files written before v15 is applied in
`PreferencesService.migrate`, not in the record. The switch is a `ToggleSwitch` in Settings' Updates
group, registered in `UiPreferences` like every other setting
([ADR 006](../adr/006-preferences-architecture.md)); the manual check works regardless of it —
switching the automatic check off is about not being interrupted, not about never updating.
Unit-tested by `PreferencesServiceTest`.

Covers:
- req~update-notice~1
- req~update-check-unobtrusive~1
