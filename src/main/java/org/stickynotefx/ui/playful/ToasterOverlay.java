package org.stickynotefx.ui.playful;

import javafx.animation.Animation;
import javafx.animation.TranslateTransition;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public final class ToasterOverlay extends PlayfulReminderOverlay {
    private final SVGPath toast = shape("M25 27 V15 C17 4 53 4 45 15 V27 Z M29 16 H41 M29 20 H39", "#F4D44D");
    private final SVGPath body = shape("M17 26 Q17 21 22 21 H49 Q54 21 54 26 V41 H17 Z M22 41 V45 M49 41 V45 M54 28 H61 V33 H54", "#8BD1C1");
    private final SVGPath slot = shape("M23 25 H48 M23 36 H40", "#FFFFFF");

    public ToasterOverlay() {
        artwork.getChildren().addAll(toast, body, slot);
        setAccessibleText("Toaster reminder visual");
        resetArtwork();
    }

    @Override protected void resetArtwork() {
        toast.setTranslateY(12);
    }

    @Override protected Animation arrival() {
        TranslateTransition pop = new TranslateTransition(Duration.millis(450), toast);
        pop.setFromY(12);
        pop.setToY(-3);
        pop.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        return pop;
    }

    @Override protected void expiredArtwork() {
        toast.setTranslateY(-3);
    }
}