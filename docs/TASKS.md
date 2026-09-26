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
- [ ] LEGAL-01: Approve license, add license file and public upstream identity.
- [ ] PKG-02: Build in OBS, inspect RPM with rpmlint, test installed launcher,
  validate production AppStream identity/screenshots, compare reproducible builds.
- [ ] PKG-03: Resolve the extracted Tumbleweed OpenJFX native-library linkage failure
  and rerun the UI test with the installed system JDK and OpenJFX before release.
- [ ] RELEASE-01: Obtain devel-project review and submit to Factory with an
  authorized maintainer account. A local build does not satisfy this gate.

## v1.2 Deferred Tasks

- [ ] THEME-01: Create the authorized v1.2 branch after foundation release.
- [ ] THEME-02: Define abstract `ThemeAnimation` start/pause/seek/dispose lifecycle
  and registry factories. Disposing a note must release every timeline/listener.
- [ ] THEME-03: Implement egg hatch with licensed assets and deadline-based stages.
- [ ] THEME-04: Implement fuse countdown and a non-disruptive completion animation.
- [ ] THEME-05: Implement toaster warming and toast pop with licensed assets.
- [ ] THEME-06: Enable per-note selection and persistence; unknown themes fall back
  to plain notes without losing content or reminders.
- [ ] THEME-07: Resynchronize after suspend, reminder edits, cancellation and
  restoration. Animations must never control or delay reminder delivery.
- [ ] PERF-01: Profile idle CPU, heap and frame pacing with 1/20/100 notes; define
  budgets from the v1.0 baseline, pause hidden themes and support reduced motion.
- [ ] QA-03: Test theme disposal, resource loading, resize, reduced motion and
  long-running deadlines on KDE and GNOME before the v1.2 release.