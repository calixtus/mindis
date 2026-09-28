# ADR 010: The update check offers the platform installer; MinDis never patches itself

Date: 2026-09-28
Status: accepted

## Context

MinDis is handed to parish volunteers who will not watch a GitHub repository for releases. A fix
they never hear about is a fix they never get, so the application has to say when a newer version
exists.

The obvious shape - a library that downloads new jars and swaps them into the running installation
(update4j, Getdown and the rest of the Java updater family) - is closed off by how MinDis ships.
[ADR 002](002-packaging.md) settled on jpackage: every release is a platform package (MSI, DMG/PKG,
DEB/RPM) around a jlink runtime, plus a portable zip of the same app-image. Those packages are
signed, or will be, as a unit:

- replacing files inside a signed macOS `.app` invalidates its signature, and Gatekeeper then
  refuses to launch it - the app breaks itself on the first self-update;
- on Windows the same holds for the Authenticode signature, and the install lives under
  `Program Files`, which an unelevated process cannot write to;
- on Linux the package manager owns the installed files and will happily report them as modified.

Beyond that, the field is thin. update4j is Apache-2.0 and was designed for JPMS, but its repository
is archived and its last release is from 2020; it also wants to own the launcher, which is jpackage's
job here. Install4j's updater is proprietary, which [ADR 008](008-third-party-licensing.md) rules out
of the bundle. The one tool that does solve self-updating properly for JVM desktop apps - Hydraulic
Conveyor, free for OSI-licensed projects, with Sparkle on macOS and delta updates - does it by
*replacing the packaging step*, i.e. by reopening ADR 002. That is a bigger decision than "tell the
user about new versions", and it should not be smuggled in as a side effect of one.

## Decision

**MinDis checks, asks, downloads and verifies. The platform's own installer installs.**

```
latest-<platform>.json  (release asset, written by :gui:updateManifest)
        │  HTTP GET, releases/latest/download/…
        ▼
UpdateService.check()        → newer version?     → dialog: "Version X is available"
UpdateService.download(…)    → SHA-256 verified   → dialog: "Install and quit"
UpdateService.install(…)     → cmd /c start | open | xdg-open
        ▼
jpackage installer runs, MinDis exits
```

- **No file inside the running installation is ever touched.** The update is a package install, so
  signatures, package databases and elevation all stay the platform's business.
- **Static manifest, no update server.** One `latest-<platform>.json` per platform, published as a
  release asset and fetched through GitHub's stable `releases/latest/download/…` URL. No API token,
  no rate limit, no service to operate. One manifest *per platform* rather than one listing all
  three, because each platform's packages are built on their own CI runner: that runner can publish a
  complete manifest of its own without waiting for, or being broken by, the others.
- **Checksums are mandatory.** A downloaded installer gets executed, so `Checksums.verify` checks it
  against the manifest's SHA-256 before it is handed to the OS, and a failed download is deleted
  rather than left lying around. The manifest is fetched over TLS from the same release as the
  packages, and the checksums are written by the build that produced them (`:gui:updateManifest`).
- **The user decides, twice.** Once to download, once to quit into the installer - and the second
  prompt goes through the same unsaved-changes confirmation as closing the window, so an update can
  never discard staged edits. Nothing is installed silently; the platform installer's own UI (and
  its elevation prompt) is what runs.
- **The check on startup is silent unless it has something to offer.** Up to date, no network, a
  server that does not answer: log line, no dialog. It is on by default and can be switched off in
  Settings, where the manual "Check now" stays available either way.
- **The portable zip is not an update target.** MinDis never unpacks an archive over itself; a
  portable install is offered the installer like any other, and installing it is a deliberate move
  from portable to installed. The manifest still lists the portable artifact, so a future change of
  mind has the data it needs.

## Consequences

- The user still clicks through the real installer. This is an *assisted* update, not Sparkle-grade
  silent updating - the same trade-off JabRef makes, and the honest one for signed platform packages.
- Releases must carry the manifest. `:gui:updateManifest` runs after `:gui:jpackage` on every
  platform runner (`.github/workflows/binaries.yml`) and its output is attached to the release; a
  release without it simply reports "no update information", because the URLs are pinned to a tag and
  a stale manifest must never point at a different release's files.
- Unsigned packages will make the OS warn on install. That is a release-engineering gap, not an
  update-check one, and it is worth closing before this feature is put in front of parish users.
- The download URLs are pinned to `v<version>` (overridable with `-PreleaseTag`). Retagging a
  published release breaks its manifest; publish a new version instead.
- One architecture per platform is assumed (`windows`/`macos`/`linux`), matching the jpackage targets
  in `mindis-gui/build.gradle.kts`. Shipping, say, both macOS arches means adding an architecture to
  the manifest and to `UpdatePlatform`.
- If seamless updates ever become a requirement, the decision to revisit is ADR 002's packaging
  choice (Conveyor), not this one - the manifest and the dialogs here would be replaced wholesale,
  which is why nothing outside `org.mindis.core.update` and `org.mindis.gui.update` knows how
  updating works.
