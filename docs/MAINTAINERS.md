# Maintainer Build And Release Guide

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

## OBS Staging

1. Choose the project license with the owner. Add the license text, update the spec
   and AppStream SPDX IDs, add `%license`, and include it in the source archive.
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

The spec intentionally contains a non-release license marker. Metadata can be
structurally valid while still lacking the real upstream identity and screenshots
required for a polished software-center listing. Never mistake local validation for
Factory acceptance. The spec is architecture-specific because its launcher embeds
the system library directory; the application jar itself contains only Java bytecode.

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
a side-by-side composition of two UI snapshots. Project licensing still needs approval.
Do not bundle Java, SQLite JNI binaries, or Maven caches in an OBS source tarball.

## Local Verification Record

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
   project licensing must be confirmed before distribution.
- No OBS upload, Factory submission, performance baseline, or full KDE/GNOME
   session certification has been completed in this workspace.