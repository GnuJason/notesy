# Copilot Engineering Tasks

Each task has an owner boundary and a falsifiable acceptance check. Implement the
v1.0 foundation before enabling any animated theme. VS Code's task runner exposes
the corresponding local build and test commands.

## v1.0 Implementation

- [x] UI-01: Implement `StickyNoteView` and controller. Verify text editing, minimum
  window dimensions, drag, resize, five swatches, autosave and reminder indicator.
- [x] DATA-01: Implement SQLite CRUD and XDG placement. Round-trip all nine fields
  across connection restart; prevent autosave from resurrecting deleted notes.
- [x] APP-01: Restore notes, create/delete multiple notes, expose all-notes view,
  stack windows, flush pending edits on quit, reject a second database writer.
- [x] TIMER-01: Add presets and custom local date/time. Test overdue execution,
  calendar month/year boundaries, replacement, cancellation and queued callbacks.
- [x] NOTIFY-01: Invoke `notify-send` without a shell, bound process lifetime,
  escape markup, retain failures for retry and clear successful reminders.
- [x] THEME-00: Add empty interface/registry and disabled selector. No timelines,
  assets, or concrete animation classes may enter the foundation release.
- [x] PKG-01: Add offline system-library build, launcher, spec, desktop entry,
  AppStream XML, icon, changelog and deterministic OBS source archive recipe.

## v1.0 Release Gates

- [ ] QA-01: Complete KDE/GNOME, X11/Wayland and multi-monitor hands-on checks.
- [ ] QA-02: Verify real desktop notifications, suspend/resume and notification
  failure/retry behavior in an installed RPM session.
- [x] LEGAL-01: Approve GPL-3.0-or-later, add LICENSE, declare `%license`, and
  document project-created artwork. Public upstream identity/publication remains PKG-02.
- [ ] PKG-02: Build in OBS, inspect RPM with rpmlint, test installed launcher,
  validate production AppStream identity/screenshots, compare reproducible builds.
- [ ] PKG-03: Resolve the extracted Tumbleweed OpenJFX native-library linkage failure
  and rerun the UI test with the installed system JDK and OpenJFX before release.
- [ ] RELEASE-01: Obtain devel-project review and submit to Factory with an
  authorized maintainer account. A local build does not satisfy this gate.

## v1.2 Opt-In Visuals

- [x] THEME-01: Define NONE/EGG/BOMB/TOASTER, disabled by default, with no changes
  to scheduling, note storage or notification content. No release branch is created.
- [x] THEME-02: Implement disposable, mouse-transparent JavaFX footer overlays.
  Cancel/hide/disable/dispose stops transitions; completed effects hide themselves.
- [x] THEME-03: Implement original vector egg artwork with crack and note reveal.
- [x] THEME-04: Implement a cartoon fuse and finite, non-flashing burst effect.
- [x] THEME-05: Implement original toaster artwork and toast-pop transition.
- [x] THEME-06: Persist per-note opt-in selection and expired state in a separate
  profile-scoped sidecar; global defaults never replace explicit note choices.
- [x] THEME-07: Connect pending and successful-delivery states without changing
  scheduler algorithms. Verify stale callbacks, cancellation, hide/show and restart.
- [x] QA-PLAYFUL: Test preferences, mode selection, focus, unclipped rendering,
  finite transitions, removal and real desktop notification delivery; capture PNGs.
- [x] CSS-01: Activate the theme boundary with Light/Dark/Paper CSS, themed dialogs
  and artwork, immediate preference saves and debounced note autosave.
- [x] UX-01: Add UI-only short presets, snooze and time-zone previews without
  modifying scheduler algorithms or notification payloads.
- [x] WINDOWS-01: Avoid initial overlap, preserve stacking focus, observe native
  geometry changes, add optional session-only snap and the All Notes desktop action.
- [x] ICONS-01: Generate 32/64/128/256-pixel icons using only the JDK, retaining SVG.
- [x] QA-NATIVE-12: Add twelve native UI cases including expiry restart, independent
  per-note modes, themes, icon loading, snooze, placement and geometry persistence.
- [ ] SOAK-01: Run the new themed/per-note build for multiple hours; record CPU,
  memory, compositor behavior and clean shutdown. Earlier-build uptime is not proof.
- [ ] PERF-01: Profile idle CPU, heap and frame pacing with 1/20/100 notes; define
  budgets from the v1.0 baseline. The opt-out disables all visual motion.
- [ ] QA-03: Complete hands-on KDE/GNOME, multi-monitor, suspend/resume and
  long-running desktop checks before approving v1.2.
- [ ] RELEASE-PLAYFUL: Publish the screenshot URLs, validate AppStream online,
  resolve the system OpenJFX RPM failure, and pass all foundation release gates.