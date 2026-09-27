package org.stickynotefx.theme;

import javafx.scene.image.Image;
import javafx.stage.Stage;

public final class WindowIcons {
    public static void apply(Stage stage) {
        if (!stage.getIcons().isEmpty()) return;
        for (int size : new int[]{32, 64, 128, 256}) {
            var resource = WindowIcons.class.getResource("/org/stickynotefx/icons/notesy-" + size + ".png");
            if (resource != null) {
                Image icon = new Image(resource.toExternalForm());
                if (!icon.isError()) stage.getIcons().add(icon);
            }
        }
    }
}