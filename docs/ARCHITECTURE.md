# Architecture: Stable Reminders And Optional Visuals

## Boundaries

| Layer | Owner | Contract |
| --- | --- | --- |
| Application | `StickyNoteApplication` | XDG data path, single-instance lock, startup, shutdown and error reporting |
| Note windows | `StickyNoteView`, `StickyNoteController` | Transparent JavaFX stage, text, swatches, drag, resize, debounce, flush |
| Multiple notes | `NoteManager`, `MainWindow` | CRUD orchestration, stacking, restoration, deletion, reminder UI integration |
| Model | `Note` | Immutable note snapshots with sanitized colors and minimum dimensions |
| Persistence | `DatabaseProvider`, `NoteRepository` | SQLite schema, parameterized CRUD, conditional reminder clear |
| Reminders | `ReminderService`, `ReminderScheduler`, `DesktopNotifier` | Calendar presets, due dispatch, notification process and retries |
| Themes | `ThemeEngine`, `ThemeRegistry` | Light/Dark/Paper CSS overlays and resource lookup, no model mutations |
| Playful visuals | `ui.playful` | Fixed-size artwork, finite transitions, static expired state and disposal |
| Profile visuals | `ProfilePreferences` | Atomic sidecar preferences, per-profile theme and per-note visual state |

The JavaFX application thread owns note controllers, the manager and reminder-service
state. A daemon scheduler checks wall-clock due times once a second and dispatches to
the UI thread. A separate single daemon executor runs `notify-send`, bounded by a
10-second process timeout. No shell interprets note text. SQLite operations are small,
synchronous and serialized; autosave is debounced to reduce write frequency.

Each pending timer has its own identity. Cancelling or replacing a timer invalidates
callbacks already queued for UI dispatch. Text autosaves do not restart unchanged
timers. Delivery completion rechecks the current timestamp before clearing it.
Shutdown cancels queued work and terminates notification processes. Notification
delivery is at-least-once across crashes: a crash after desktop acceptance but before
persisting the clear can produce a duplicate on restart. A notification already sent
to the desktop cannot be recalled by cancelling its reminder.

## Persistence

```sql
CREATE TABLE notes (
    id INTEGER PRIMARY KEY,
    content TEXT NOT NULL DEFAULT '',
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    color TEXT NOT NULL DEFAULT '#FFF176',
    reminder_timestamp INTEGER NULL,
    theme TEXT NULL
);
```

Timestamps are epoch milliseconds; bounds are rounded logical screen pixels.
WAL mode and a five-second busy timeout are enabled. Restored windows are clamped to
available visual screen bounds. A deleted note cannot be recreated by an update.
Future migrations must preserve the nullable `theme` field and existing notes.
The v1.0 database is unencrypted and never synced over a network.

## v1.2 Boundary

`PlayfulSettingsStore` uses a separate properties file with atomic replacement,
disabled/NONE defaults and safe handling of invalid profiles. It does not read or
write the note's reserved `theme` column. `NoteManager` loads settings on startup,
saves preference changes before applying them to all controllers, and initializes
new windows with the default selection unless they have a saved per-note choice.
Existing public constructors still work. The production manager places its sidecar
beside `DatabaseProvider.defaultPath()` so identical note IDs in different profiles
cannot share visual state. Tests inject isolated stores explicitly.

`ProfilePreferences` stores `theme` and keys of the form
`note.<id>.playfulModeEnabled`, `note.<id>.playfulModeType`, and
`note.<id>.playfulExpiredState`. Saves replace the sidecar atomically; reads of
missing or malformed settings use safe defaults. This is separate from the SQLite
transaction: a crash or disk failure after notification delivery can lose the
visual marker but never re-arms or suppresses a reminder. Sidecar failures do not
enter notification retry logic or block note creation/deletion. Failed sidecar
cleanup can leave stale on-disk entries; an error is reported, and new note creation
discards any reused ID in memory and attempts to remove its stored visual settings.
Standard reminders with no visual override do not create this file.

`StickyNoteView` owns a mouse-transparent overlay in a 72 x 48 logical-pixel footer
slot, separate from the editor, drag bar, resize grip and reminder dialog. The slot
is absent when disabled or idle without a reminder. `EggOverlay`, `BombOverlay` and
`ToasterOverlay` share a disposable `PlayfulReminderOverlay` lifecycle:

- Scheduled: static artwork and a one-shot wait until near the deadline.
- Pending: one brief wobble; no continuously running countdown or flashing.
- Delivered: finite reveal/pop followed by static expired artwork, with no running animation.
- Cancelled, hidden, disabled or disposed: stop all transitions and callbacks.

The existing `reminderDelivered` callback remains conditional on the note's current
timestamp and successful database clear. Only then does the view animate. A failed
notification retains its existing retry behavior; the visual never calls the
scheduler or notifier. Hidden notes show the static expired pose when reopened;
animations do not replay. Clear, reschedule and snooze reset the sidecar marker.
Rendering failures are caught at the view boundary and remove only the overlay.
The scheduler, reminder service, note schema, and notification content are unchanged.

All visual state belongs to the JavaFX thread. There are no per-note worker threads
or global UI listeners. CSS selectors and looked-up colours carry all theme changes,
including artwork colours; note colour is a CSS token rather than an inline
background override. UI-only duration presets and snooze call the existing setter.
The timezone selector changes display only. Window positions use existing metadata;
native geometry changes feed the existing write-behind timer. Snap and smart initial
placement are session-only and off by default; the original placement remains the default.
Each note header exposes a synchronized pin toggle for `Stage.alwaysOnTop`; the menu
does not duplicate that command. Preferences use an undecorated app-owned header
with one close control, avoiding duplicate native/window-frame close affordances.

## Future Extensions

Sync and encryption are not implemented or enabled. Future adapters should sit
outside `NoteRepository`'s stable CRUD contract and have an explicit migration,
conflict and key-management design before activation. Visual preferences must not
become a second note database or a source of reminder deadlines. The existing
scheduler remains the sole scheduling authority; no consolidation refactor was
performed under the v1.2 core freeze. Window show/hide/delete/close ownership stays
with the existing controller/manager; placement and CSS are separate UI concerns.