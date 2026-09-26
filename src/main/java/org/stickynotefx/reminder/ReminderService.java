package org.stickynotefx.reminder;

import org.stickynotefx.model.Note;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongFunction;

public final class ReminderService implements AutoCloseable {
    private final Clock clock;
    private final Consumer<Runnable> dispatch;
    private final LongFunction<Note> lookup;
    private final BiConsumer<Long, Long> delivered;
    private final Consumer<String> error;
    private final DesktopNotifier notifier;
    private final ReminderScheduler scheduler;
    private final Map<Long, Long> active = new HashMap<>();
    private final ExecutorService notifications = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sticky-notifications");
        thread.setDaemon(true);
        return thread;
    });
    private boolean closed;

    public ReminderService(Clock clock, Consumer<Runnable> dispatch, LongFunction<Note> lookup,
                           BiConsumer<Long, Long> delivered, Consumer<String> error) {
        this.clock = clock;
        this.dispatch = dispatch;
        this.lookup = lookup;
        this.delivered = delivered;
        this.error = error;
        notifier = new DesktopNotifier();
        scheduler = new ReminderScheduler(clock, dispatch);
        scheduler.start();
    }

    public void refresh(Note note) {
        Long timestamp = note.reminderTimestamp();
        if (Objects.equals(active.get(note.id()), timestamp)) return;
        cancel(note.id());
        if (timestamp != null) {
            active.put(note.id(), timestamp);
            scheduler.schedule(note.id(), Instant.ofEpochMilli(timestamp), () -> fire(note.id(), timestamp));
        }
    }

    public void cancel(long id) {
        active.remove(id);
        scheduler.cancel(id);
    }

    private void fire(long id, long timestamp) {
        Note note = lookup.apply(id);
        if (closed || note == null || !Objects.equals(note.reminderTimestamp(), timestamp)) return;
        notifications.submit(() -> {
            try {
                notifier.send(note.content());
                dispatch.accept(() -> complete(id, timestamp));
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                dispatch.accept(() -> retry(id, timestamp));
            }
        });
    }

    private void complete(long id, long timestamp) {
        if (closed || !Objects.equals(active.get(id), timestamp)) return;
        try {
            delivered.accept(id, timestamp);
            active.remove(id, timestamp);
        } catch (RuntimeException failure) {
            retry(id, timestamp);
        }
    }

    private void retry(long id, long timestamp) {
        if (closed || !Objects.equals(active.get(id), timestamp)) return;
        scheduler.schedule(id, clock.instant().plusSeconds(60), () -> fire(id, timestamp));
        error.accept("Reminder delivery failed. Check notify-send and your desktop notification service. Retrying in one minute.");
    }

    @Override public void close() {
        closed = true;
        scheduler.close();
        notifications.shutdownNow();
        active.clear();
    }
}