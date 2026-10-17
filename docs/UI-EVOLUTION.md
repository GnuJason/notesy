# Notesy UI evolution

Status: proposal for review, saved 2026-10-16. No application changes approved
or implemented. The minimize control is included in this proposal, not implemented.

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
| [MainWindow](../src/main/java/org/stickynotefx/ui/MainWindow.java) | Separate non-modal list stage with New note, Open note, Show all, Quit and Preferences. | Reuse list-cell/preview behavior in a sidebar; retire the separate list stage once all activation paths reach the integrated browser. |
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
  and second-instance activation.

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
| X | Preserve the current save-and-hide intent; reveal/focus an integrated browser host as a recovery route. Do not delete or quit. |
| Explicit Quit | Flush every shared session and exit only after successful saves. |

If a mode/selection change requires a save and that save fails, keep the current
editor and its text visible, report the failure, and abort the transition.
Minimize/close must likewise not silently discard unsaved edits.

**Close recovery proposal:** focus another existing Browser/Notebook host, or
show the primary stage as a non-modal integrated browser/editor host when no
such host exists. Never immediately reopen the exact window the user just closed.
Closing that fallback browser can hide it, matching today's manager behavior;
application-menu activation recovers it. This policy needs review.

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
  always-on-top in Notebook mode, then restore it on return; approve this
  intentional change before implementation.

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
| Notesy                         Quick Note / Restore        -  [] X|
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

## Review and acceptance gates

Before implementation, approve:

1. The same-stage expansion and shared-session architecture.
2. X recovery behavior, distinction between Minimize / Close / Quit, and the
   explicit "Show all sticky notes" label.
3. New Note creating a window only in Quick Note mode; selecting another note
   edits within Browser/Notebook even if another host already displays it.
4. Session-only workspace layout/recency for the first release, unchanged compact
   startup, and temporarily disabling always-on-top in Notebook.
5. Search arriving with Notebook rather than blocking the smaller Browser step.

After approval, deliver in reviewable steps: minimize and persistent navigation
controls; shared sessions/editor extraction; integrated Browser; then Notebook
and search. Do not wire incomplete navigation controls into a released UI.

Required implementation checks:

- Minimize is beside X, recoverable through the desktop, and never opens All
  Notes. Explicit show operations deiconify the host.
- Quick -> Browser -> Notebook -> Restore preserves text, selection, reminders,
  colors, theme and compact bounds. Test transitions within the autosave delay.
- Shared views cannot overwrite one another's text or reminder changes. Closing
  one host does not dispose a session still used elsewhere.
- Failed saves block destructive navigation/quit and leave text available.
- Selecting, creating, deleting, filtering and clearing search retain valid
  selection/empty states without new popup windows or autosave resurrection.
- Desktop launch, `--all-notes`, second-instance activation, last-note deletion
  and reminder-error reporting all reach an accessible integrated view.
- Light/Dark/Paper, playful visuals, hidden/minimized animation cleanup,
  multi-monitor placement, keyboard focus, tooltips and accessible names survive.
- Exercise the 260-pixel compact minimum, display scaling, KDE/GNOME and
  X11/Wayland. Reuse and extend the existing repository, reminder and native
  [UI tests](../src/test/java/org/stickynotefx/ui/StickyNoteUiTest.java).

This document records a design only. No Java, CSS, database, launcher or installed
application files have been changed.
