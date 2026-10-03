# Workarounds for upstream issues

Every place where MinDis works around a defect or gap in a dependency, so each can be removed once
upstream fixes it. Each entry names the cause, where the workaround lives, and how to tell it is no
longer needed. The code comments at those places explain the mechanics; this is the register.

When adding a workaround, add it here. When upgrading one of the libraries below, check its
entries.

## JavaFX

### A text stylesheet cannot `@import` a binary one (JavaFX 27)

- **Cause:** since conditional stylesheet imports (JDK-8364149, JavaFX 27), imported rules decode
  their declarations lazily against the *importing* stylesheet's string store. A parsed (text)
  stylesheet has none, so importing a precompiled `.bss` throws an NPE in
  `javafx.css.Declaration.readBinary` and the app does not start. JavaFX substitutes the `.bss`
  for any `.css` path that has one beside it, which AtlantaFX's themes do.
- **Workaround:** `ThemeStyler.withModenaTokens` imports the theme through AtlantaFX's
  `stylesheet:` URL with every module listed, which serves the CSS text.
- **Cost:** the theme is parsed from text instead of loaded precompiled, some 10–40 ms per theme
  switch.
- **Fixed when:** importing `base.getUserAgentStylesheet()` (the plain `.css` path) in
  `withModenaTokens` starts the app and passes `ModenaTokensTest`. The import itself is still
  needed for the Modena tokens below; only the URL changes back.

### `requestFocus()` cannot mark keyboard focus

- **Cause:** `Node#requestFocus` clears `:focus-visible`, and the public API that sets it,
  `Node#requestFocusTraversal`, only visits focus-traversable nodes. The sidebar's entries are one
  Tab stop, so all but the active entry are not traversable.
- **Workaround:** `AppShell.KEYBOARD_FOCUS`, a `:keyboard-focus` pseudo-class set when the arrow keys
  move focus, styled alongside `:focus-visible` in `shell.css`.
- **Fixed when:** JavaFX offers a public way to focus a node with `focusVisible` set; switch
  `AppShell.focusNavButton` to it and drop the pseudo-class.

### jpackage cannot build a WiX v5 `.exe` (JDK-8356592)

- **Cause:** the msiwrapper step fails with `AccessDeniedException` copying the final `.exe`.
- **Workaround:** the Windows installer is an MSI (`installerType` in `mindis-gui/build.gradle.kts`).
- **Fixed when:** `./gradlew jpackage -PinstallerType=exe` succeeds with WiX v5.

## AtlantaFX

### `Styles.encode` produces a `data:` URI JavaFX misreads

- **Cause:** for `text/css` it writes the CSS as is (`data:text/css;charset=utf-8,...`), and JavaFX
  percent-decodes a non-Base64 `data:` URI, so any `%` in the CSS (`derive(..., 40%)`) breaks it.
  The same applies to `ThemeManager.Change#applyStylesheet`, which uses it.
- **Workaround:** `ThemeStyler` encodes its stylesheets as Base64 itself and installs them with
  `ThemeStyler.apply` rather than `Change#applyStylesheet`.
- **Fixed when:** `Styles.encode` escapes the content or uses Base64.

### Light text on every accent fill

- **Cause:** AtlantaFX puts `-color-fg-emphasis` (light) on accent-filled controls regardless of the
  accent, which reads at 2.2–2.8:1 on light accents (green, orange, teal).
- **Workaround:** `ThemeStyler.ACCENT_TEXT_CSS` and `-color-accent-on`: dark text and marks on
  accents where light text falls below 3:1 (`ThemeStyler.needsDarkTextOn`), also used by
  `dashboard.css` and `shell/power-pane.css`.
- **Fixed when:** AtlantaFX derives the on-accent colour from the accent; then point
  `-color-accent-on` at its token, or drop it. `ContrastTest` tells whether the result still reads.

### No hover highlight on `Tile`

- **Cause:** AtlantaFX styles no hover state for `Tile`.
- **Workaround:** `.tile:hover` in `modules/settings.css`.
- **Fixed when:** AtlantaFX's `Tile` highlights on hover by itself.

## GemsFX

### Control CSS written for Modena

- **Cause:** GemsFX's bundled stylesheets look up Modena tokens (`-fx-control-inner-background`,
  `-fx-selection-bar-text`, `-fx-outer-border`, ...) that AtlantaFX never defines, and hardcode
  light-theme literals (`white`, `#e0e0e0`, `yellow`, ...). Unresolved lookups log a warning each
  and paint nothing; literals stay light in the dark theme. Several GemsFX controls install this
  CSS as a per-node user-agent stylesheet (`getUserAgentStylesheet()`).
- **Workarounds:**
  - `ThemeStyler.MODENA_TOKENS_CSS`: the Modena tokens, mapped to AtlantaFX's.
  - `ThemeStyler.SEARCH_POPUP_CSS`: the search popup's flat rows and their hover/selected fills.
  - `CalendarPickers`, `TimePickers`, `SearchFields`: per-control token and literal overrides.
  - `shell/power-pane.css`: the PowerPane's drawer, dialog and info-center literals.
- **Fixed when:** GemsFX's CSS follows AtlantaFX tokens (or exposes its own). Remove a piece and
  watch the `javafx.css` log while using the control in both themes; `ModenaTokensTest` and
  `ContrastTest` cover the search popup and the screens.

### Popups style themselves before their window is registered

- **Cause:** `CustomPopupControl.show` sets the node orientation before showing the window, which
  runs a CSS pass while the popup is not yet in `Window.getWindows()`. AtlantaFX's `ThemeManager`
  only puts our stylesheet on a window once it is there, so that first pass sees the user-agent
  stylesheet alone.
- **Workaround:** the Modena tokens are in the user-agent layer (`ThemeStyler.withModenaTokens`),
  not in the per-scene stylesheet.
- **Fixed when:** GemsFX no longer needs the Modena tokens (above) — then `withModenaTokens` can go
  and `ThemeManager` can take the plain theme. `ModenaTokensTest` shows a popup with only the
  user-agent layer.

### `requires javafx.swing` for one class

- **Cause:** GemsFX's module descriptor requires `javafx.swing`, used only by `SVGUtil` behind
  `SVGImageView`, which MinDis does not use.
- **Workaround:** `extraJavaModuleInfo` rewrites GemsFX's descriptor without it
  (`build-logic/.../org.mindis.gradle.module.gradle.kts`), so the Swing bridge stays out of the
  runtime image. The rewritten descriptor must be re-checked on every GemsFX upgrade.
- **Fixed when:** GemsFX makes it `requires static` or drops it.

### JavaFX 17 modules pulled in by POMs

- **Cause:** GemsFX, PickerFX and ControlsFX declare `javafx-swing` and `javafx-fxml` as POM
  dependencies, pinned to JavaFX 17.
- **Workaround:** both are excluded in `build-logic/.../org.mindis.gradle.feature.javafx.gradle.kts`.
- **Fixed when:** those POMs drop or align the dependencies.

## PickerFX

### Unused ControlsFX dependency

- **Cause:** PickerFX's published metadata declares ControlsFX, which its code never references.
- **Workaround:** excluded in `mindis-gui/build.gradle.kts`, keeping it out of the installer and
  `THIRD-PARTY-NOTICES`.
- **Fixed when:** PickerFX's metadata drops it.

## Micrometer

### 1.16.x crashes javac

- **Cause:** micrometer-core 1.16.x class files carry type annotations that crash javac when read
  from the module path.
- **Workaround:** pinned to 1.15.12 in `versions/build.gradle.kts`.
- **Fixed when:** the build compiles with the pin removed.
