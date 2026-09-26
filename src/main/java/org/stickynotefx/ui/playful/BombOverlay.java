package org.stickynotefx.ui.playful;

import javafx.animation.Animation;
import javafx.animation.ScaleTransition;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public final class BombOverlay extends PlayfulReminderOverlay {
    private final SVGPath body = shape("M30 14 L32 9 L39 12 L37 17 C58 28 43 49 26 39 C13 31 17 19 30 14 Z", "#41464D");
    private final SVGPath fuse = shape("M36 11 C35 2 49 2 48 10", "#F4D44D");
    private final SVGPath spark = shape("M49 3 L50 8 L56 7 L52 11 L55 15 L49 13 L46 17 L46 11 L42 9 L47 8 Z", "#F4D44D");
    private final SVGPath burst = shape("M36 4 L41 14 L52 8 L50 21 L63 24 L52 30 L56 41 L42 37 L36 46 L30 36 L17 42 L20 29 L9 24 L22 20 L19 8 L30 14 Z", "#F4D44D");
    private final SVGPath note = shape("M29 17 H43 V32 H29 Z M32 22 H40 M32 26 H38", "#FFFFFF");

    public BombOverlay() {
        artwork.getChildren().addAll(body, fuse, spark, burst, note);
        setAccessibleText("Bomb reminder visual");
        resetArtwork();
    }

    @Override protected void resetArtwork() {
        body.setVisible(true);
        fuse.setVisible(true);
        spark.setVisible(true);
        burst.setVisible(false);
        note.setVisible(false);
        burst.setScaleX(1);
        burst.setScaleY(1);
    }

    @Override protected Animation arrival() {
        body.setVisible(false);
        fuse.setVisible(false);
        spark.setVisible(false);
        burst.setVisible(true);
        note.setVisible(true);
        ScaleTransition pop = new ScaleTransition(Duration.millis(400), burst);
        pop.setFromX(0.3);
        pop.setFromY(0.3);
        pop.setToX(1);
        pop.setToY(1);
        return pop;
    }
}