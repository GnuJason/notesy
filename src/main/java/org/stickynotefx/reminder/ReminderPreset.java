package org.stickynotefx.reminder;

import java.time.ZonedDateTime;

public enum ReminderPreset {
    FIFTEEN_MINUTES("15 minutes"), ONE_HOUR("1 hour"), ONE_DAY("1 day"),
    ONE_MONTH("1 month"), ONE_YEAR("1 year");

    private final String label;
    ReminderPreset(String label) { this.label = label; }

    public ZonedDateTime after(ZonedDateTime now) {
        return switch (this) {
            case FIFTEEN_MINUTES -> now.plusMinutes(15);
            case ONE_HOUR -> now.plusHours(1);
            case ONE_DAY -> now.plusDays(1);
            case ONE_MONTH -> now.plusMonths(1);
            case ONE_YEAR -> now.plusYears(1);
        };
    }

    @Override public String toString() { return label; }
}