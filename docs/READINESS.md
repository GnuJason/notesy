# Notesy 1.0 Factory Readiness

Assessment: 2026-09-26. Decision: **NO, not ready for openSUSE:Factory**.
The approved version remains 1.0. The v1.2 features are staged, not released.
No OpenJFX package repair, native preload workaround, OBS upload or Factory
submission was performed during this corrective pass.

## Corrections And Verified Gates

- GPL-3.0-or-later is declared in the spec, AppStream and project documentation.
  LICENSE is the complete canonical GNU GPLv3 text. The spec installs it using
  `%license`; the source archive includes it. ARTWORK.md records project-created
  assets. AppStream metadata remains separately licensed CC0-1.0.
- Maven, Make, the spec, source archive name and AppStream release history all
  retain version 1.0. There is no v1.2 release entry.
- `appstream-util validate-relax --nonet` and `desktop-file-validate` pass,
  including during RPM `%check`. The single main category is Office; icon and
  launcher names consistently use notesy.
- The launcher uses system Java, system JavaFX and system sqlite-jdbc. Runtime
  library-directory selection makes the application package genuinely noarch.
  The notification dependency is `/usr/bin/notify-send`. No debugsource package
  is produced for Java-only bytecode.
- The offline Makefile compiles successfully with installed JDK 21 and OpenJFX.
  It invokes no Maven resolver or downloader. Application-jar inspection found
  only project classes/resources, with no embedded JDK, third-party classes or
  native libraries. Source archive inspection found no bundled jars, shared
  libraries or fonts. Runtime source inspection found no network requests;
  activation uses local filesystem events, not sockets.
- Existing-instance activation retains the exclusive profile lock and signals
  the manager on the JavaFX thread. Headless tests cover repeated requests,
  separate profiles, shutdown/reacquisition and a real second JVM. The manager
  restores its minimized state and requests focus. Actual compositor activation
  still requires the native runtime gate below.
- A real SQLite fixture was generated using model/persistence classes from
  foundation commit `af4482d8ab45afca4534670a18646c19302a9526` and system SQLite.
  It contains synthetic notes, not user data. Current tests read/write it, retain
  the original table SQL, check integrity and save the visual sidecar. Historical
  v1.0 classes then successfully read and wrote the updated database. See the
  fixture README for provenance and regeneration instructions.
- Model, persistence and reminder source files remain unchanged from that
  foundation commit. No destructive migration was introduced.
- Sixteen headless tests pass against the installed system libraries, with zero
  skipped tests. This is not a successful native UI suite.
- Fresh local source and binary RPM builds pass `%install`, `%files`, `%license`
  and metadata checks. The RPM contains the launcher, metainfo, desktop entry,
  scalable SVG and 32/64/128/256 PNG icons under hicolor, plus project docs/license.
- Actual openSUSE rpmlint 2.10.0 package code and its unmodified distribution
  configuration report zero errors. Two warnings are duplicate checks of the
  unpublished Source0 URL. No project-specific lint filters were added.
  Spellchecking is unavailable in the temporary lint environment.

## Reproducibility Procedure

Two clean Make builds and two source archives compare byte-for-byte. Complete
source and binary RPMs also compare byte-for-byte when SOURCE_DATE_EPOCH
(1790294400), build host and build paths are fixed, with RPM's
`use_source_date_epoch_as_buildtime` enabled. Each build reruns prep/build/install.
The source-dist recipe normalizes all staged source timestamps; preserve them
when copying sources and build the staged spec.

Varying the build directory produces identical installed payloads but changes
RPM's expanded SPEC header and the derived SOURCEPKGID. Therefore complete-RPM
build-path independence is not claimed. Evidence logs, final RPMs and checksums
are retained under `dist/verification/` when the local audit finishes. Controlled
local double-builds do not substitute for independent OBS architecture builds.

## Native Runtime Blocker

The system verification command is:

```sh
JAVA_HOME=/usr/lib64/jvm/java-21-openjdk NOTESY_SYSTEM_JAVAFX=1 \
  STICKYNOTEFX_UI_TEST=1 mvn -o clean verify
```

The opt-in Maven profile uses installed JavaFX and SQLite jars, not Maven JavaFX.
Tested packages are java-21-openjdk 21.0.12.1, openjfx 17.0.20.0-2.1 and
sqlite-jdbc 3.53.4.0-1.1. DISPLAY=:0 and actual notify-send delivery work.
Native toolkit setup fails with:

```text
UnsatisfiedLinkError: /usr/lib64/openjfx/libglass.so: undefined symbol: XOpenDisplay
```

`ldd -r` also reports XCloseDisplay unresolved. Test cleanup was corrected so
failed toolkit initialization does not hang in Platform.exit(). The run is a
failure, not a skipped or passing native suite. Do not replace system JavaFX with
Maven binaries and call this gate passed. No OpenJFX repair was attempted.

## External And Remaining Gates

- All six screenshot URLs in AppStream returned HTTP 404, although the image
  files exist locally and are included in the source archive. Publish them at
  those public URLs or supply an approved public image host, then revalidate.
- Publish the upstream project/release source and replace the local Source0 name
  with the actual public release URL. Do not invent a working release URL.
- The supplied OBS target was interpreted as `home:gnujason:notesy-test` (removing
  the stray `<`). The public OBS API returned 404 Project not found, and neither
  standard local osc configuration file exists. No authenticated upload or build
  could be made. Confirm/create the authorized project and architecture pair,
  configure authentication outside chat, then run both architecture builds.
- Fresh installed-RPM native launch, all native UI tests with zero skips, desktop
  action focus, KDE/GNOME X11/Wayland and multi-monitor checks remain required.
  Soak/performance and suspend/resume gates in TASKS.md remain open.
- Live openSUSE policy wiki retrieval failed (403/content extraction). Distribution
  rpmlint and payload inspection supply local evidence, not Factory acceptance or
  a legal/devel-project review. Confirm upstream identity and obtain that review.

Licensing and local packaging corrections do not override these blockers.