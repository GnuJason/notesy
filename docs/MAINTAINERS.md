# Maintainer Build And Release Guide

See [READINESS.md](READINESS.md) for the current corrective-pass results and blockers.

## Toolchains

Development: JDK 21+, Maven 3.9+, GTK 3, `libgthread-2_0-0` for Maven JavaFX 21 on
current Tumbleweed, and `notify-send` from `libnotify-tools`. Tests use JUnit 5.
Maven versions are pinned; `project.build.outputTimestamp` stabilizes jar timestamps.

Distribution: `java-21-openjdk-devel`, `openjfx`, `sqlite-jdbc`, `make`,
`desktop-file-utils`, and `appstream-glib`. The Makefile runs `javac --release 21`
directly, with no Maven repository or network access. Tumbleweed currently packages
OpenJFX 17; the app uses APIs available there. The launcher uses the classpath because
the distribution JavaFX directory includes split build jars that cannot all be used
as modules. It loads system native libraries from the RPM library directory.

```sh
export JAVA_HOME=/usr/lib64/jvm/java-21-openjdk
make
make check-metadata
make source-dist
```

Do not treat the Maven jar as a standalone fat jar. For packaged execution, use the
installed `notesy` launcher. For development, use `mvn javafx:run`.

To verify installed distribution libraries, use the opt-in Maven profile:

```sh
JAVA_HOME=/usr/lib64/jvm/java-21-openjdk NOTESY_SYSTEM_JAVAFX=1 \
   STICKYNOTEFX_UI_TEST=1 mvn -o clean verify
```

This replaces Maven JavaFX and SQLite with installed jars and sets the native
library path to the distribution library directory. The display and notification
service must work. `system.javafx.lib` and `system.libdir` are overridable Maven
properties. This profile is verification-only; RPM builds still use Make.
If JavaFX fails to start, the suite must fail, not silently skip native tests.

The All Notes desktop action and subsequent normal launches signal the existing
profile through `activation.request` beside `application.lock`. A filesystem
watcher dispatches manager activation on the JavaFX thread; no socket, network
connection or additional dependency is used. Compositor focus policy still applies.

## OBS Staging

1. Verify the approved GPL-3.0-or-later license, root LICENSE file, `%license`
   entry and artwork provenance are included in the release archive and RPM.
2. Publish the canonical upstream project and release tarball. Add `URL` and the
   public `Source0` URL to the spec. Confirm the AppStream homepage and developer
   identity, publish `docs/screenshots` and validate screenshot URLs online. Confirm
   the application ID before public release.
3. Run `mvn verify`, the opt-in native UI tests, and metadata validators.
4. Run `make source-dist`; copy the contents of `dist/Notesy/` into a package
   created under an authorized OBS home/devel project.
5. Run `osc build openSUSE_Tumbleweed x86_64` in that OBS checkout. The build must
   succeed offline. Run `rpmlint` on resulting packages and inspect installed paths.
6. Install the RPM on a disposable Tumbleweed desktop. Test KDE/X11, KDE/Wayland and
   GNOME/Wayland, notifications, resizing, display unplug/replug, and session logout.
7. Build twice with the same source epoch and compare payloads with `diffoscope`.
8. Use an appropriate devel project and submit to Factory only after maintainer and
   licensing review. OBS credentials and permission are required; never store them here.

The approved release version remains 1.0; staged v1.2 features are not a release.
Metadata can be
structurally valid while still lacking the real upstream identity and screenshots
required for a polished software-center listing. Never mistake local validation for
Factory acceptance. The package is noarch: its launcher selects the installed
system library directory at runtime, and its jar contains only Java bytecode
and project resources. Native dependencies remain separate system packages.

## Operational Checks

- Verify autosave across a clean quit/relaunch, including immediate quit after typing.
- Verify database errors leave visible unsaved state and prevent normal quit from
  silently discarding data. Do not launch two writers against the same data directory.
- Test missing `notify-send`, desktop rejection and a hung notification process.
- Test overdue startup, cancellation, replacement, suspend/resume, DST gaps and
  calendar month/year boundaries. Ambiguous fall-back times use the earlier offset.
- Back up the closed application's entire data directory before migration. Never
  copy only a live WAL-mode database file.
- There is no autostart or closed-application reminder service in v1.0.

## Scope Control

No v1.2 git branch is created by the build. The opt-in visual implementation is
not release approval. Vector paths in `ui.playful` are original project artwork;
PNGs in `docs/screenshots` come from the native UI test. The standard screenshot is
a side-by-side composition of two UI snapshots. Code and original artwork are
GPL-3.0-or-later; see ARTWORK.md. AppStream metadata remains CC0-1.0.
Do not bundle Java, SQLite JNI binaries, or Maven caches in an OBS source tarball.

## Historical Verification Record

These are earlier observations, not evidence that current release gates pass.

- JDK 21 with Maven JavaFX 21: repository/reminder tests and the opt-in JavaFX
   lifecycle test pass. The UI test exercises editing, actual debounce, drag, resize,
   color changes, reminder state, deletion and restoration, and emits note snapshots.
- The offline Makefile compiles with extracted Tumbleweed OpenJFX 17.0.20.0-2.1
   jars. Repeated jar builds and repeated source archives produced identical hashes.
- SQLite tests also pass with Tumbleweed sqlite-jdbc 3.53.4.0-1.1.
- Distribution native startup is blocked: `ldd -r libglass.so` reports unresolved
   `XOpenDisplay` and `XCloseDisplay`. An X11 preload used only for diagnosis exposed
   another unresolved C++ runtime symbol in `libglassgtk3.so`. No preload workaround
   is shipped. Reproduce with the installed distribution JDK/OpenJFX in OBS and
   resolve with the OpenJFX package maintainer before an RPM release.
- Local foundation RPM build and installation passed, but the installed launcher
   reproduced the system OpenJFX `XOpenDisplay` native failure described above.
- The v1.2 preview is tested with Temurin JDK 25 and Maven JavaFX 21 on the active
   XWayland display. It covers preference persistence, all overlay modes, finite
   animations, focus, bounds and actual scheduler-to-notify-send delivery.
- Desktop entry and relaxed AppStream checks validate. Homepage and screenshot
   references are now present; developer identity, online image availability and
   public release sources must be confirmed before distribution.
- No OBS upload, Factory submission, performance baseline, or full KDE/GNOME
   session certification has been completed in this workspace.