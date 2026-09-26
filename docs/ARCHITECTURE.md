# Architecture: v1.0 Foundation

## Boundaries

| Layer | Owner | Contract |
| --- | --- | --- |
| Application | `StickyNoteApplication` | XDG data path, single-instance lock, startup, shutdown and error reporting |
| Note windows | `StickyNoteView`, `StickyNoteController` | Transparent JavaFX stage, text, swatches, drag, resize, debounce, flush |
| Multiple notes | `NoteManager`, `MainWindow` | CRUD orchestration, stacking, restoration, deletion, reminder UI integration |
| Model | `Note` | Immutable note snapshots with sanitized colors and minimum dimensions |
| Persistence | `DatabaseProvider`, `NoteRepository` | SQLite schema, parameterized CRUD, conditional reminder clear |
| Reminders | `ReminderService`, `ReminderScheduler`, `DesktopNotifier` | Calendar presets, due dispatch, notification process and retries |
| Themes | `ThemeEngine`, `ThemeRegistry` | Empty interface and immutable empty registry only |

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

No `ThemeAnimation` implementation, timelines, or active theme selector exists in
v1.0. Add the abstract animation lifecycle only when v1.2 starts. Proposed themes:
egg hatch, fuse countdown and toaster pop. Keep rendering on the JavaFX thread and
notification deadlines in the reminder service, independent of animation frame rate.
The current `theme` column reserves storage without committing to asset formats.