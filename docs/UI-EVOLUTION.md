# Notesy UI evolution

Status: revised design proposal, 2026-10-16. The overall architecture is approved
in principle; this revision incorporates the subsequent UX steering. Phase 1
source implementation was subsequently authorized and is recorded below.
Phases 2-5 remain unimplemented.

## Core UX principle

**Closing is not navigation. Browsing notes is navigation. Expanding is a
workspace transition.**

```text
Quick Note --Notes--> Browser --Expand--> Notebook

_                    minimize the current window
X                    save and close/hide the current window, nothing else
Show all sticky notes explicitly show individual sticky windows
Quit                 save and exit Notesy
```

These actions must remain independent. In particular, closing a window must not
open, reveal, focus, or reopen any other Notesy window.

## Saved requirements

- Add a minimize line immediately beside the X used to close a note.
- Preserve the lightweight desktop sticky note as the default Quick Note mode.
- Give every note persistent, unobtrusive Notes/List and Expand controls. Access
  to saved notes must not depend on closing the current note.
- Notes Browser mode should expand the current window horizontally, revealing a
  vertically scrollable notes sidebar on the left and an editor on the right.
  Do not replace this with a separate modal popup.
- List meaningful titles or first-line previews, make recent notes easy to reach,
  allow opening and editing older notes, and expose an obvious New Note action.
  Design for eventual search.
- Full Notebook mode should expand into a large notebook-style workspace with a
  persistent sidebar, search, a scrollable collection, New Note, and a large
  editor. Selecting notes must not spawn additional popup windows.
- These are three states of one application:
  **Quick capture -> Browse notes -> Full writing workspace.**
- Reveal advanced controls progressively; do not cover the compact note with
  application toolbars.
- X saves and closes/hides only the current window. If saving fails, keep its
  text visible and report the failure. No browser or management-window recovery
  is triggered by closing, including closing the last visible note.
- Minimize uses a horizontal line immediately beside X and iconifies only the
  current window after any required save succeeds.
- Keep MainWindow as transitional compatibility functionality. Retirement is
  optional cleanup after activation/navigation replacements are proven, not a
  prerequisite for the first milestone.
- Preserve the relative reminder choices, explicit date/time and timezone
  behavior, Egg/Bomb/Toaster visuals, status, cleanup and persistence. No visual
  reminder redesign is part of this work.
- Inspect JavaFX views, stage management, persistence, note data, and current
  Show All/close behavior before implementation. Review the architecture and
  layouts before making code changes.

The supplied screenshot shows the existing separate All Notes window, not the
custom sticky-note title bar. Native title-bar icons vary with KDE/GNOME themes;
the new sticky-note minimize control should explicitly use a horizontal line.

## Existing implementation

| Component | Current responsibility | Reuse / change |
| --- | --- | --- |
| [StickyNoteView](../src/main/java/org/stickynotefx/ui/StickyNoteView.java) | Owns an undecorated stage, compact header, text editor, menus, status, resize grip and playful overlay. Default size 320 x 300, minimum 260 x 220. No minimize button. | Retain styling, controls and editor behavior; separate reusable editor content from window ownership. |
| [StickyNoteController](../src/main/java/org/stickynotefx/ui/StickyNoteController.java) | Owns a Note snapshot and 400 ms autosave, plus stage geometry, dragging and resizing. | Separate shared editing state from window-specific behavior before allowing a host to switch notes. |
| [NoteManager](../src/main/java/org/stickynotefx/ui/NoteManager.java) | Owns repository, one controller/stage per note, preferences, reminders and the All Notes window. | Keep application coordination; distinguish note sessions from open window hosts. |
| [MainWindow](../src/main/java/org/stickynotefx/ui/MainWindow.java) | Separate non-modal list stage with New note, Open note, Show all, Quit and Preferences. | Retain during development. Reuse previews, selection, New Note, Preferences and activation behavior in the sidebar. Consider retirement only in Phase 5 after replacement paths pass validation. |
| [Note](../src/main/java/org/stickynotefx/model/Note.java) | Immutable record: ID, content, x/y/width/height, color, reminder timestamp and theme. | Keep the content model and existing compact-note bounds. No explicit title or recency timestamps exist. |
| [NoteRepository](../src/main/java/org/stickynotefx/persistence/NoteRepository.java) | SQLite CRUD; loads all notes ordered by ID. Updates write all fields. | Keep existing storage API initially. Do not let multiple independent editor snapshots overwrite one another. |
| [DatabaseProvider](../src/main/java/org/stickynotefx/persistence/DatabaseProvider.java) | XDG/profile database placement, WAL and nine-column notes table. | No schema migration needed for the initial navigation design. |
| [ProfilePreferences](../src/main/java/org/stickynotefx/ui/preferences/ProfilePreferences.java) | Theme and per-note visual state in profile-local visuals.properties. | Preserve visual keys; defer persistent navigation preferences until their semantics are approved. |
| [ReminderService](../src/main/java/org/stickynotefx/reminder/ReminderService.java) | Schedules by note ID; obtains current note through a lookup callback. | Keep scheduling/delivery behavior; change lookup to shared sessions rather than window controllers. |
| [StickyNoteApplication](../src/main/java/org/stickynotefx/StickyNoteApplication.java) / [ApplicationInstance](../src/main/java/org/stickynotefx/ApplicationInstance.java) | Application lifecycle, profile lock and activation of an existing process. | Preserve one writer per profile; route activation into an existing integrated browser. |

### Current lifecycle and terminology

- Startup loads every saved note and shows its sticky window, creating a blank
  note only when the database is empty. Hidden/open state is not persisted.
- A note's X flushes edits, hides that note and shows the All Notes window.
  It does not delete the note or quit Notesy.
- The note menu's **All notes** opens the list. The list toolbar's **Show all**
  shows every sticky window and attempts to preserve the focused note on top.
  These are different operations.
- The All Notes window's X hides it when notes exist; otherwise it quits.
  Explicit Quit flushes pending edits and exits.
- An existing-process activation and `--all-notes` both open the list.
- Deletion is confirmed, cancels reminders and disposes the note controller.
- Themes, reminders, snooze and playful state already exist and must survive
  navigation. Current hidden-stage handling stops playful effects.

The lifecycle above describes existing code, not the desired behavior. In
particular, both close-triggered management-window activation and the empty
MainWindow close-to-quit behavior conflict with the revised X semantics.

## Proposed architecture

Use one JavaFX stage per window host, with three presentation states. Changing
mode changes the layout and size of that stage, not the database or application.
Multiple existing sticky notes remain supported; this is not a forced
single-window conversion.

```text
NoteManager
  +-- NoteRepository / ReminderService / preferences
  +-- NoteSession per note ID
  |     current Note, shared observable content, autosave, save status
  +-- NoteWindowController per open window host
        selected note ID, presentation mode, compact/browser bounds
        +-- NoteWindowView (one Stage)
              +-- compact window header
              +-- NotesSidebarView (Browser / Notebook only)
              +-- NoteEditorView (selected NoteSession)
```

Proposed components are responsibilities, not a requirement for an additional
framework:

- **NoteSession:** extract the content/update/autosave responsibilities currently
  inside StickyNoteController. Keep one authoritative session per ID even when
  two windows show the same note. All text, color and reminder edits use it.
  Editing synchronization must avoid recursive listeners and preserve cursor/
  selection where practical. No competing debounced writers.
- **NoteEditorView:** extract the editor, note color, reminder status and playful
  footer from StickyNoteView. Each host gets its own view; JavaFX nodes must not
  be attached to multiple parents. Detaching it removes listeners and stops its
  animations, without deleting or disposing the shared session.
- **NotesSidebarView:** extract MainWindow's virtualized ListView, color marker,
  first-nonblank-line preview and empty state. Add New Note, selection and,
  for Notebook, search. Bind to the shared collection rather than rebuilding
  the entire list after each autosave.
- **NoteWindowView / NoteWindowController:** evolve the current sticky view/
  controller into the window shell, drag/resize handling and mode transitions.
  A simple presentation enum is sufficient; no navigation framework is needed.
- **NoteManager:** retain creation, confirmed deletion, persistence, profile
  preferences and reminder ownership. Maintain sessions separately from windows.
  Expose integrated browser activation for menu actions, errors, desktop actions,
  and second-instance activation. Close handlers must never invoke that
  navigation entry point. Retain sessions for hidden notes so reminders and
  explicit recovery work even when no host is visible.

### Mode transitions

| Action | Proposed result |
| --- | --- |
| Notes/List in Quick Note | Keep the selected note and editor, widen the same stage, reveal the left sidebar. |
| Notes/List in Browser | Hide the sidebar and return to a compact note showing the selected note. |
| Expand in Quick Note or Browser | Show sidebar and editor; maximize the same stage into Notebook mode. Not exclusive fullscreen. |
| Restore in Notebook | Return to the prior Quick Note or Browser mode and its bounds. |
| Select a sidebar row | Flush the outgoing session, then edit the selected session in the same stage. No new popup. |
| New Note in Quick Note | Preserve today's action: create another small sticky window. |
| New Note in Browser / Notebook | Create and select a blank note in the current editor; do not spawn another window. |
| Minimize line | Flush edits and iconify the current stage. Do not open the browser, hide/delete the note, or quit. |
| X in any mode | Flush the selected session and, only on success, close/hide this host. No other visual action: no browser, focus transfer, fallback window, deletion or application exit. |
| Explicit Quit | Flush every shared session and exit only after successful saves. |

If a mode/selection change requires a save and that save fails, keep the current
editor and its text visible, report the failure, and abort the transition.
Minimize/close must likewise not silently discard unsaved edits.

**No close recovery window:** remove the earlier proposal to focus another host
or reveal a fallback browser after X. This applies even when the last visible
window closes or the compatibility browser has no notes. Notesy may remain
running without visible windows, with existing reminders active. Recovery is
intentional: use Notes in another note, an already-open Browser/Notebook, or
normal application activation. Only explicit Quit exits the running application.

### Exact action semantics

- **X / Close:** save-and-hide this window only. Do not navigate, focus another
  Notesy host, create a note, change the selected note, delete data or quit.
  The same policy applies to custom X and window-manager close requests.
- **Minimize:** save as required, then iconify only this stage. Do not call the
  close handler, browser activation, All Notes, delete or quit. On failure,
  leave the window unminimized and display the error.
- **Notes / Browse notes:** intentional navigation. In the completed Browser
  milestone, preserve the current editor/content and reveal the sidebar in the
  same stage. No close is necessary. Collapsing Browser returns to Quick Note
  with the selected note.
- **Expand:** intentional workspace transition carrying the current selection
  into Notebook in the same stage. Show the persistent sidebar, maximize or size
  appropriately to the available screen, never enter exclusive fullscreen.
  Restore returns to the preceding mode and meaningful bounds.
- **Show all sticky notes:** explicit bulk-window action. Show/deiconify each
  note's individual compact host, creating/recovering a host where necessary.
  Do not select every sidebar row, convert Browser/Notebook hosts to compact
  mode, or duplicate an existing compact host for the same note. Preserve
  existing focus/stacking intent. Keep this distinct from Browse notes; it may
  move to overflow once integrated navigation exists.
- **Quit:** flush all authoritative sessions, including hidden notes, before
  exiting. A failed save cancels exit. Keep affected text visible, reveal an
  editor for the failing session if none is visible, and report the failure.
  This is failure recovery for an explicit Quit, not navigation caused by X.

Keep the explicit bulk-window action, but label it **Show all sticky notes** in
the browser's overflow menu. The persistent Notes control means **Browse notes**,
not "open a window for every saved note." This avoids changing the existing
bulk action without explanation.

### Data, geometry and recency

- Browser selection is UI state, not a new note or a database copy.
- Preserve compact bounds in the existing Note fields. Browser resizing and
  notebook maximization must never overwrite those bounds through the current
  stage listeners. Capture compact geometry only while in Quick Note mode.
- Keep browser bounds, divider position, maximized state and prior mode in
  per-host session state initially. Restore the selected note's compact bounds
  when returning to Quick Note. Retain existing screen-clamping behavior.
- Preserve today's startup behavior initially: saved notes reopen as compact
  notes. Persisting workspace mode/open-window membership is a separate,
  reviewable behavior change.
- Reuse the first nonblank content line as the title, with "Blank note" fallback.
  No title column or schema change is necessary.
- Initially sort newer IDs first for newly created notes and maintain a
  session-local recent-selection order. Freeze ordering while typing; do not
  move the selected row on every autosave. This is not persistent last-edit
  ordering: the model has no created_at or updated_at fields.
- Persistent last-used/last-edited ordering requires an explicit later choice
  between profile UI metadata and a versioned database migration. Do not infer
  modification times from IDs.
- Browser can ship before search. Notebook should include case-insensitive
  content filtering of the loaded collection, with no-results and Clear search
  affordances. Keep a selected editor visible if filtering excludes its row.
  SQLite full-text search is not required for this initial scope.
- Reminder deadlines/delivery are independent of visible editors. Refactor
  callbacks to update sessions first, then notify attached views. Hidden or
  iconified hosts must not animate or steal focus.
- Preserve note-level pin preferences in session state. Temporarily release
  always-on-top in Notebook mode, then restore it on return to Quick Note.
  This is intentional mode behavior, not a change to the note's pin preference.
  Do not let stage always-on-top listeners overwrite that preference during the
  temporary suppression.

### MainWindow transition and activation audit

Keep MainWindow available as a non-modal compatibility surface through the early
phases. It is not the destination of X. Reuse its useful list, selection,
creation, Preferences and commands instead of deleting it to force migration.
Route activation through a coordinator so callers do not need to know which
browser implementation is currently ready.

| Route / event | Current behavior | Transitional behavior | Integrated target |
| --- | --- | --- | --- |
| Cold normal startup | Restore all saved sticky windows; create one blank note if empty. | Preserve this default. | Same compact default; do not force Notebook on startup. |
| Application-menu / normal desktop activation while running | Second process signals showManager(). | Keep explicit activation reaching MainWindow until Browser is ready. | Focus/deiconify an existing Browser/Notebook, or deliberately open Browser with an existing note; empty collection shows New Note. |
| Second-instance activation | Profile lock and activation request reach showManager(). | Preserve the lock, request delivery and a usable fallback. | Use the same integrated activation entry point; never create another database writer. |
| `--all-notes` / desktop Open Notesy action | Opens MainWindow. | Preserve CLI/action compatibility. | Open integrated Browser/Notebook; this flag means browse, not show every sticky. |
| Notes control / existing All notes menu item | Menu opens MainWindow; no persistent Notes button yet. | Persistent Notes intentionally opens the non-modal compatibility list in Phase 1. | Phase 3 replaces that temporary destination with same-stage Browser and keeps the old menu route working. |
| Show all | Shows all sticky controllers. | Label explicitly Show all sticky notes. | Explicitly recover/show compact hosts independently of collection navigation. |
| X, including last visible note | Hides note and shows MainWindow. | Remove management activation in Phase 1. Compatibility-window X also hides without implicit quit. | Save and hide only; no navigation, even when no windows remain. |
| Confirmed last-note deletion | Opens MainWindow after disposing the note. | Retain an accessible empty collection/New Note surface for this explicit delete action. | Keep the current Browser/Notebook with an empty editor, or deliberately show an empty Browser if a compact host was removed. This is deletion recovery, never X behavior. |
| Reminder-delivery or visual-state failure | Delivery error sets status and shows MainWindow; visual-state errors set its status and log. | Preserve visible/logged error reporting through the existing fallback. | Report in an appropriate integrated status/error surface, retaining retry behavior. Do not route a successful close through this path. |
| Startup/storage/required-save failure | Application alert or controller status/exception handling. | Keep failed-save text visible; no success-shaped fallback. | Abort the required transition, report explicitly, and keep the failing editor accessible. |
| Preferences / New Note / legacy list selection | Commands live in MainWindow or sticky menus. | Keep working while extracting reusable pieces. | Preferences remains accessible; creation follows mode; sidebar selection edits in place. |

Normal activation is allowed to open/focus a browser because the user requested
the application. X must not simulate activation. With all windows hidden, the
existing process and reminders remain alive until Quit; verify that application-
menu activation reliably recovers it on both KDE and GNOME.

## Mock layouts

Labels below explain the icons; they are not all literal button text.
Use existing icon styling, tooltips and accessible names.

### Quick Note: default 320 x 300

```text
+------------------------------------------------+
| +  Notes     [drag area]  Pin Timer ... Expand - X|
+------------------------------------------------+
|                                                |
| Note text                                      |
|                                                |
|                                                |
| [save/reminder status]         [visual] [resize] |
+------------------------------------------------+
```

Keep the header one unobtrusive row: Notes and Expand use small vector icons.
Minimize is immediately left of X. Retain the existing New Note, pin, reminder
and options actions. The implementation must prove that the controls and a
usable drag target fit at the existing 260-pixel minimum; if they cannot, review
an overflow arrangement rather than silently increasing the minimum or removing
existing controls.

### Notes Browser: approximately 650-760 x 400-500

```text
+----------------------------------------------------------+
| Notes [collapse]         Notesy           ... Expand - X  |
+--------------------+-------------------------------------+
| + New Note         |                                     |
| Recent / All notes | Selected note editor                |
| ------------------ |                                     |
| * Shopping list    | Selecting a row edits here.          |
|   Project ideas    | The outgoing note stays saved.      |
|   Blank note       |                                     |
|   Older note       |                                     |
|       [scroll]     | [status]                    [resize] |
+--------------------+-------------------------------------+
```

Use a SplitPane with a restrained sidebar minimum. Prefer growth to the left
to retain the original editor position when space permits; otherwise reposition/
clamp to the current screen. Constrain growth on small displays.

### Full Notebook: maximized, not exclusive fullscreen

```text
+------------------------------------------------------------------+
| Notesy                         Quick Note / Restore        [] - X|
+------------------------+-----------------------------------------+
| + New Note             | Selected note preview/title             |
| Search notes...        | Reminder / note options                 |
| ---------------------- | --------------------------------------- |
| Recent notes           |                                         |
| * Shopping list        | Large writing workspace                 |
|   Project ideas        |                                         |
|   Meeting notes        |                                         |
|   ...                  |                                         |
|        [scroll]        | [save/reminder status]                  |
+------------------------+-----------------------------------------+
```

No folders, tags, rich-text toolbar, accounts or synchronization are proposed.
Advanced organization can be considered separately without complicating capture.

## Architectural consequences and existing-code concerns

1. Close and navigation must be separate commands. NoteManager.open() currently
   calls main.show() after hiding; remove that coupling, not merely change the
   destination window. MainWindow's empty-list close-to-quit path also conflicts
   with the new distinction between X and explicit Quit.
2. Save must succeed before hiding. StickyNoteController currently flushes both
   before close and in onHidden. A second failing save after hiding cannot
   guarantee visible text; consolidate controlled close/minimize handling so
   post-hide callbacks are not the primary safety mechanism.
3. Stage listeners currently persist every visible geometry change. Guard mode
   transitions and update compact bounds only in Quick Note. Do not accidentally
   record an intermediate maximized/browser size during transition events.
4. The notes map currently conflates sessions, windows, reminder lookup and bulk
   showing. Split those responsibilities before in-place selection. Repository
   updates replace all fields, so stale view snapshots are unsafe even for a
   color or reminder change. Use a single FX-thread session state and autosave
   coordinator per ID.
5. Several hosts can edit one session, but only Quick hosts can propose compact
   bounds. Keep one canonical compact host per ID; Browser/Notebook views can
   share its session without competing geometry writers. Closing/detaching a
   host must not dispose sessions still used by views or reminders.
6. Reminder lookup and delivered callbacks currently require a sticky controller.
   Adapt them to sessions without changing relative choices, explicit date/time,
   timezone conversion, scheduler/retry logic, status, expired-state cleanup,
   Egg/Bomb/Toaster rendering or stored reminder data. No reminder redesign.
7. Current playful lifecycle observes showing, not necessarily iconified state.
   Verify minimize stops animations without losing reminder state. Existing
   pin listeners couple stage state to selection; preserve the preference
   separately before Notebook suppresses always-on-top.
8. Retiring MainWindow too early would strand activation and error paths. Keep
   its commands and fallback until the audit table is covered by tests. Empty
   collection handling must not accidentally create a blank note on every
   activation or turn X into Quit.
9. Phase 1 asks for persistent Expand before Notebook exists, and persistent
   Notes before integrated Browser exists. Do not fake a Notebook transition or
   start a premature shared-state rewrite. The proposed staging compromise is
   documented below; it requires milestone review.

## Revised implementation phases

### Phase 1: window semantics and controls

Source implementation status:

- Sticky-note X now saves and hides only its stage; the compatibility browser
  also hides without implicitly quitting when empty.
- Minimize saves before iconifying, and explicit show operations deiconify.
  Minimized playful visuals stop and restore without replaying delivery.
- Persistent Notes opens the retained compatibility browser intentionally.
- Persistent Expand is disabled with an explanatory tooltip, also installed on
  its wrapper so it is available when the disabled control is hovered.
- Show all sticky notes is in the compatibility browser's overflow menu.
- All eight controls use 24-pixel-wide, 30-pixel-high targets, with a drag region
  of at least 36 pixels. Minimum-width tests exercise all three themes and
  check control placement and graphic clipping.
- Shared sessions, integrated Browser and Notebook are deliberately not added.
  These source changes do not update the installed application automatically.

Validation: all 18 native UI cases passed on the current desktop with JDK 21,
the repaired system OpenJFX libraries and no LD_PRELOAD. Light/Dark/Paper
minimum-width snapshots were inspected. The local application JAR also builds.
This does not replace the outstanding cross-desktop/display-scaling checks.

```sh
env -u LD_PRELOAD JAVA_HOME=/usr/lib64/jvm/java-21-openjdk \
  MAVEN_OPTS=-Djava.library.path=/usr/lib64/openjfx17-gnujason:/usr/lib64 \
  STICKYNOTEFX_UI_TEST=1 mvn -o -Psystem-javafx \
  -Dsystem.javafx.lib=/usr/share/java/openjfx17-gnujason \
  -DforkCount=0 -Dtest=StickyNoteUiTest test
```

- Add the horizontal-line minimize control immediately left of X.
- X flushes and hides only its host; remove all close-triggered list/focus
  behavior. Keep failed-save text visible. Apply consistent non-quitting X
  semantics to the compatibility window.
- Add persistent Notes, initially reaching the existing non-modal All Notes
  implementation only on an intentional click. This is a temporary compatibility
  destination, not the final same-stage interaction.
- Establish a persistent Expand control in the development UI, disabled with an
  explanatory tooltip/accessibility description until Phase 4. Do not ship an
  enabled no-op, call it Notebook while merely resizing, or expose incomplete
  controls as a completed release. If an inactive control is undesirable, defer
  its visible exposure until Phase 4 rather than rushing Notebook into Phase 1.
- Rename the bulk action Show all sticky notes, retain existing functions and
  MainWindow, and verify deliberate application activation recovers hidden notes.
- Validate control fit, hit targets, tooltips and a usable drag region at 260
  pixels. If necessary, propose moving Pin/Reminder into More at narrow widths
  for review, keeping their actions accessible and Notes/Expand/Minimize/Close
  discoverable; do not silently raise minimum width or remove features.

**Recommended first implementation milestone:** Phase 1 alone, with the staged
Notes/Expand behavior explicitly reviewed. Its acceptance demonstration is:
edit then immediately X -> saved note disappears with no new/focused Notesy
window; minimize -> only that host iconifies; save failure -> text remains
visible; deliberate activation -> notes recover; all compact controls remain
usable at 260 pixels. No session extraction or MainWindow demolition yet.

### Phase 2: shared state extraction

- Introduce one authoritative NoteSession per note ID and extract NoteEditorView
  from stage ownership. Keep the current compact and compatibility interfaces.
- Move autosave/content/reminder updates into sessions; prove simultaneous-view
  synchronization without recursive listeners or stale delayed saves.
- Preserve all reminder, visual, theme, pin and persistence functionality.
  No sidebar dependency until shared state is stable.

### Phase 3: integrated Browser

- Introduce NotesSidebarView, reusing list previews, selection and useful commands.
- Notes expands the current stage and retains its selected editor/content.
- Selecting another note flushes then edits in place; New Note creates/selects in
  the current Browser. No new popup for selection.
- Preserve compact geometry and maintain Show all sticky notes as a separate
  bulk operation. Search does not block this phase.
- Route explicit browsing/activation to the integrated surface as each path is
  verified; retain MainWindow as a compatibility fallback.

### Phase 4: Notebook

- Enable Expand/Restore into a maximized or appropriately large, non-fullscreen
  workspace with a persistent sidebar and larger editor.
- Add case-insensitive filtering across loaded content, no-result and clear
  states, without SQLite FTS or new organization systems.
- Temporarily suppress Notebook always-on-top, restore the prior compact pin
  behavior, and validate restore bounds/mode/selection.

### Phase 5: cleanup after proven migration

- Verify all activation, error, menu and empty-state routes before deciding
  whether to retire MainWindow. Its removal is optional, not assumed.
- Remove obsolete paths carefully, update tests/documentation and preserve
  compatibility, including `--all-notes` and second-instance activation.
- Do not introduce tags, folders, accounts, synchronization, rich text or a
  reminder visual redesign as cleanup.

## Testing and review gates

Retain and extend the existing repository, reminder and native
[UI tests](../src/test/java/org/stickynotefx/ui/StickyNoteUiTest.java):

- Quick -> Browser -> Notebook -> Restore preserves text, selection, compact
  geometry, colors/themes, pin state and reminders.
- Exercise mode change, selection, minimize, close and Quit inside the existing
  400 ms autosave delay; save failures block closing, selection and exit.
- X never opens/reveals/focuses another Notesy window, including the last visible
  window and an empty compatibility browser. Successful close is not navigation.
- Minimize affects only the current host, does not delete or quit, stops hidden
  visual work, and can be explicitly restored/deiconified.
- Multiple sticky windows remain independent; multiple views of one session
  synchronize without recursive listeners or competing autosave writers. Closing
  one host does not dispose a shared session.
- Deletion, New Note by mode, Show all sticky notes, search, selection under
  filtering, and empty/no-result states remain correct; deleted notes cannot be
  resurrected by pending saves.
- Preserve relative reminder presets, explicit date/time, timezone behavior,
  Egg/Bomb/Toaster, reminder status/cleanup/persistence and hidden-note delivery.
- Verify cold startup, normal/desktop activation, second-instance activation,
  `--all-notes`, menu actions, last-note deletion and error/recovery paths.
- Verify multi-monitor placement/clamping, display scaling, KDE/GNOME and
  X11/Wayland where practical, focus preservation, accessible names/tooltips,
  and actual usability at the existing 260-pixel compact minimum.

Phase 1 is authorized; review its results before proceeding to shared-state
extraction. Later phases require their own acceptance evidence rather than one
large rewrite.

This document records the phased design and Phase 1 source implementation status.
The database schema, launchers, packaging and installed application are unchanged.
