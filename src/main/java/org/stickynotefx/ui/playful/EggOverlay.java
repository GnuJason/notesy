package org.stickynotefx.ui.playful;

import javafx.animation.Animation;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public final class EggOverlay extends PlayfulReminderOverlay {
    private final SVGPath whole = shape("M20 26 C20 13 29 3 36 3 C43 3 52 13 52 26 C52 47 20 47 20 26 Z", "#FFFDF5");
    private final SVGPath top = shape("M20 24 C21 13 29 3 36 3 C43 3 51 13 52 24 L44 21 L39 27 L32 21 L26 27 Z", "#FFFDF5");
    private final SVGPath bottom = shape("M20 24 L26 27 L32 21 L39 27 L44 21 L52 24 C54 47 18 47 20 24 Z", "#FFFDF5");
    private final SVGPath reveal = shape("M28 18 H44 V37 H28 Z M31 24 H41 M31 28 H39", "#F4D44D");

    public EggOverlay() {
        artwork.getChildren().addAll(reveal, whole, top, bottom);
        setAccessibleText("Egg reminder visual");
        resetArtwork();
    }

    @Override protected void resetArtwork() {
        whole.setVisible(true);
        top.setVisible(false);
        bottom.setVisible(false);
        reveal.setVisible(false);
        top.setTranslateX(0);
        top.setTranslateY(0);
        bottom.setTranslateY(0);
    }

    @Override protected Animation arrival() {
        whole.setVisible(false);
        top.setVisible(true);
        bottom.setVisible(true);
        reveal.setVisible(true);
        TranslateTransition crack = new TranslateTransition(Duration.millis(450), top);
        crack.setToY(0);
        crack.setToX(12);
        TranslateTransition hatch = new TranslateTransition(Duration.millis(450), bottom);
        hatch.setToY(3);
        return new ParallelTransition(crack, hatch);
    }
}