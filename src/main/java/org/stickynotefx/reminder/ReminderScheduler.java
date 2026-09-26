package org.stickynotefx.reminder;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ReminderScheduler implements AutoCloseable {
    private record Pending(Instant at, Runnable callback, AtomicBoolean queued) { }

    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "sticky-reminders");
        thread.setDaemon(true);
        return thread;
    });
    private final Clock clock;
    private final Consumer<Runnable> dispatch;

    public ReminderScheduler(Clock clock, Consumer<Runnable> dispatch) {
        this.clock = clock;
        this.dispatch = dispatch;
    }

    public void start() { worker.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS); }

    public void schedule(long id, Instant at, Runnable callback) {
        pending.put(id, new Pending(at, callback, new AtomicBoolean()));
    }

    public void cancel(long id) { pending.remove(id); }

    void poll() {
        Instant now = clock.instant();
        pending.forEach((id, entry) -> {
            if (!entry.at().isAfter(now) && entry.queued().compareAndSet(false, true)) {
                try {
                    dispatch.accept(() -> {
                        if (pending.remove(id, entry)) entry.callback().run();
                    });
                } catch (RuntimeException failure) {
                    entry.queued().set(false);
                }
            }
        });
    }

    @Override public void close() {
        pending.clear();
        worker.shutdownNow();
    }
}