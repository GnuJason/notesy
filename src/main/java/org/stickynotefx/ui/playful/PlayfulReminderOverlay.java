package org.stickynotefx.ui.playful;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.SequentialTransition;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public abstract class PlayfulReminderOverlay extends Pane implements AutoCloseable {
    public enum State { NONE, SCHEDULED, PENDING, FIRED }

    protected final Group artwork = new Group();
    private final PauseTransition pending = new PauseTransition();
    private Animation motion;
    private State state = State.NONE;

    protected PlayfulReminderOverlay() {
        setMinSize(72, 48);
        setPrefSize(72, 48);
        setMaxSize(72, 48);
        setMouseTransparent(true);
        setFocusTraversable(false);
        setClip(new Rectangle(72, 48));
        getChildren().add(artwork);
        setVisible(false);
    }

    public static PlayfulReminderOverlay create(PlayfulMode mode) {
        return switch (mode) {
            case EGG -> new EggOverlay();
            case BOMB -> new BombOverlay();
            case TOASTER -> new ToasterOverlay();
            case NONE -> throw new IllegalArgumentException("No overlay for standard reminders");
        };
    }

    protected static SVGPath shape(String path, String fill) {
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.setFill(Color.web(fill));
        shape.setStroke(Color.web("#30343B"));
        shape.setStrokeWidth(1.6);
        return shape;
    }

    public final void scheduled(Long timestamp) {
        close();
        if (timestamp == null) return;
        resetArtwork();
        artwork.setOpacity(1);
        artwork.setRotate(0);
        setVisible(true);
        state = State.SCHEDULED;
        pending.setDuration(Duration.millis(Math.max(1, timestamp - System.currentTimeMillis() - 60_000)));
        pending.setOnFinished(event -> {
            state = State.PENDING;
            RotateTransition wobble = new RotateTransition(Duration.millis(220), artwork);
            wobble.setFromAngle(-4);
            wobble.setToAngle(4);
            wobble.setAutoReverse(true);
            wobble.setCycleCount(6);
            wobble.setOnFinished(done -> artwork.setRotate(0));
            motion = wobble;
            motion.play();
        });
        pending.playFromStart();
    }

    public final void fired() {
        close();
        resetArtwork();
        artwork.setOpacity(1);
        artwork.setRotate(0);
        setVisible(true);
        state = State.FIRED;
        FadeTransition fade = new FadeTransition(Duration.millis(450), artwork);
        fade.setFromValue(1);
        fade.setToValue(0);
        motion = new SequentialTransition(arrival(), new PauseTransition(Duration.seconds(2)), fade);
        motion.setOnFinished(event -> close());
        motion.play();
    }

    protected abstract void resetArtwork();
    protected abstract Animation arrival();

    public final State state() { return state; }
    public final boolean isAnimating() {
        return pending.getStatus() == Animation.Status.RUNNING
                || motion != null && motion.getStatus() == Animation.Status.RUNNING;
    }

    @Override public final void close() {
        pending.stop();
        pending.setOnFinished(null);
        if (motion != null) {
            motion.stop();
            motion.setOnFinished(null);
            motion = null;
        }
        setVisible(false);
        state = State.NONE;
    }
}