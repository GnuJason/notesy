package org.stickynotefx.reminder;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReminderSchedulerTest {
    private final Instant now = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    void overdueFiresOnceAndFutureWaits() {
        AtomicInteger calls = new AtomicInteger();
        try (var scheduler = new ReminderScheduler(Clock.fixed(now, ZoneOffset.UTC), Runnable::run)) {
            scheduler.schedule(1, now.minusSeconds(60), calls::incrementAndGet);
            scheduler.schedule(2, now.plusSeconds(60), calls::incrementAndGet);
            scheduler.poll();
            scheduler.poll();
            assertEquals(1, calls.get());
        }
    }

    @Test
    void cancellationAndReplacementInvalidateAlreadyQueuedCallbacks() {
        List<Runnable> queue = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        try (var scheduler = new ReminderScheduler(Clock.fixed(now, ZoneOffset.UTC), queue::add)) {
            scheduler.schedule(1, now, calls::incrementAndGet);
            scheduler.schedule(2, now, calls::incrementAndGet);
            scheduler.poll();
            scheduler.cancel(1);
            scheduler.schedule(2, now.plusSeconds(60), calls::incrementAndGet);
            queue.forEach(Runnable::run);
            assertEquals(0, calls.get());
        }
    }

    @Test
    void queuedCallbacksDoNotAccumulateAndShutdownInvalidatesThem() {
        List<Runnable> queue = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        var scheduler = new ReminderScheduler(Clock.fixed(now, ZoneOffset.UTC), queue::add);
        scheduler.schedule(1, now, calls::incrementAndGet);
        scheduler.poll();
        scheduler.poll();
        assertEquals(1, queue.size());
        scheduler.close();
        queue.forEach(Runnable::run);
        assertEquals(0, calls.get());
    }

    @Test
    void presetsUseCalendarArithmetic() {
        ZonedDateTime january = ZonedDateTime.parse("2024-01-31T12:00:00+01:00[Europe/Berlin]");
        assertEquals(29, ReminderPreset.ONE_MONTH.after(january).getDayOfMonth());
        assertEquals(28, ReminderPreset.ONE_YEAR.after(ReminderPreset.ONE_MONTH.after(january)).getDayOfMonth());
        assertEquals(january.plusMinutes(15), ReminderPreset.FIFTEEN_MINUTES.after(january));
        assertEquals(january.plusHours(1), ReminderPreset.ONE_HOUR.after(january));
        ZonedDateTime beforeDst = ZonedDateTime.parse("2026-03-28T12:00:00+01:00[Europe/Berlin]");
        assertEquals(12, ReminderPreset.ONE_DAY.after(beforeDst).getHour());
    }

    @Test
    void notificationContentIsLiteralAndEscaped() {
        List<String> command = DesktopNotifier.command("<b>$(touch /tmp/nope)</b> & hello\nworld");
        assertEquals("notify-send", command.getFirst());
        assertEquals("--", command.get(3));
        assertEquals("Notesy Reminder", command.get(4));
        assertEquals("&lt;b&gt;$(touch /tmp/nope)&lt;/b&gt; &amp; hello world", command.getLast());
        assertEquals("Your note is due.", DesktopNotifier.command("  ").getLast());
        assertEquals(163, DesktopNotifier.command("x".repeat(200)).getLast().length());
    }
}