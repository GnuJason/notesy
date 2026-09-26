package org.stickynotefx.ui.playful;

import java.util.Objects;

public record PlayfulSettings(boolean playfulModeEnabled, PlayfulMode playfulModeType) {
    public static final PlayfulSettings DEFAULT = new PlayfulSettings(false, PlayfulMode.NONE);

    public PlayfulSettings {
        Objects.requireNonNull(playfulModeType);
    }

    public PlayfulMode activeMode() {
        return playfulModeEnabled ? playfulModeType : PlayfulMode.NONE;
    }
}