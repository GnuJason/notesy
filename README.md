# Notesy

A Java 21 / JavaFX desktop sticky-note application with SQLite storage and optional
desktop reminders. No Electron, embedded browser, bundled JRE, or animation engine.

## Development

Install a JDK 21+, Maven, GTK 3 and the desktop notification command. On Tumbleweed:

```sh
sudo zypper install java-21-openjdk-devel maven libgtk-3-0 libgthread-2_0-0 libnotify-tools
export JAVA_HOME=/usr/lib64/jvm/java-21-openjdk
mvn test
mvn javafx:run
```

The Maven development build downloads JavaFX and SQLite JDBC. The RPM instead
compiles offline against system OpenJFX and uses system SQLite JDBC at runtime.
The application jar does not embed any runtime or third-party libraries.
VS Code build, run, test, metadata and archive tasks are in `.vscode/tasks.json`.

## Notes And Reminders

- Drag the top strip; resize with the bottom-right grip.
- Use `+` for another note, the timer icon for a reminder, and the options menu
  for color swatches, keep-on-top, handwritten fonts, all notes, deletion and quit.
- Handwritten text is enabled when Chilanka, Patrick Hand, or Comic Sans MS is installed.
- Changes autosave after 400 ms; pending edits are flushed when a window closes or
  the app quits. Closing a note hides it; deletion requires confirmation.
- Reminders support 15 minutes, one hour, one day, one calendar month, one calendar
  year, or a future local date/time. Removing a reminder does not delete its note.
- Reminders require the app to be running. Overdue reminders fire on the next launch.
  The app does not wake a sleeping computer or install an autostart/background daemon.
- Failed notifications retain the reminder and retry after one minute. Successfully
  accepted notifications clear it. Desktop notification settings can still suppress display.
- Timer themes are disabled placeholders in v1.0. No animations or theme assets ship.

Data is stored in `$XDG_DATA_HOME/sticky-notefx/notes.db`, falling back to
`~/.local/share/sticky-notefx/notes.db`. Notes are not encrypted. Back up the data
directory while the app is closed. A file lock prevents two instances sharing it.
For an isolated development profile, use
`mvn -Djavafx.options=-Dstickynotefx.dataDir=/tmp/sticky-test javafx:run`.

## Validation

```sh
mvn verify
STICKYNOTEFX_UI_TEST=1 xvfb-run -a mvn test
make check-metadata
make source-dist
```

UI tests are opt-in because they require a real or virtual X display. JavaFX 21
on Wayland currently uses XWayland; final placement and always-on-top behavior
depend on the compositor. Test KDE and GNOME before release.

## Packaging Status

`make source-dist` creates `dist/Notesy/` with the source tarball, spec,
desktop entry, AppStream metadata, and changelog for an OBS package checkout.
The spec is a draft, **not a Factory-approved release**. A license has not been
chosen, so its `LicenseRef-UNLICENSED` marker must be replaced with the approved
SPDX identifier and an accompanying license file before distribution. A real
upstream URL, developer identity, screenshots, OBS build and Factory review are
also release gates. No OBS upload or Factory submission is automated.

The extracted Tumbleweed OpenJFX 17.0.20.0-2.1 native libraries also failed local
startup with unresolved X11/C++ symbols. This must be resolved and the installed
RPM tested before release. The Maven JavaFX 21 UI tests pass.

See [architecture](docs/ARCHITECTURE.md), [maintainer steps](docs/MAINTAINERS.md),
and the [versioned engineering task list](docs/TASKS.md).