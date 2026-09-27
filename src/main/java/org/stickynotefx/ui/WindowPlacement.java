package org.stickynotefx.ui;

import javafx.geometry.Rectangle2D;
import java.util.List;

final class WindowPlacement {
    static Rectangle2D choose(Rectangle2D screen, List<Rectangle2D> occupied) {
        double width = Math.min(320, screen.getWidth());
        double height = Math.min(300, screen.getHeight());
        Rectangle2D best = new Rectangle2D(screen.getMinX(), screen.getMinY(), width, height);
        double smallestOverlap = Double.MAX_VALUE;
        for (double top = screen.getMinY(); top <= screen.getMaxY() - height; top += 24) {
            for (double left = screen.getMinX(); left <= screen.getMaxX() - width; left += 24) {
                Rectangle2D candidate = new Rectangle2D(left, top, width, height);
                double overlap = 0;
                for (Rectangle2D window : occupied) {
                    overlap += Math.max(0, Math.min(candidate.getMaxX(), window.getMaxX()) - Math.max(left, window.getMinX()))
                            * Math.max(0, Math.min(candidate.getMaxY(), window.getMaxY()) - Math.max(top, window.getMinY()));
                }
                if (overlap < smallestOverlap) {
                    smallestOverlap = overlap;
                    best = candidate;
                }
                if (overlap == 0) return best;
            }
        }
        return best;
    }
}