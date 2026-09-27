import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

class GenerateVisualAssets {
    public static void main(String[] arguments) throws Exception {
        Path resources = Path.of("src/main/resources/org/stickynotefx");
        Files.createDirectories(resources.resolve("icons"));
        for (int size : new int[]{32, 64, 128, 256}) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.scale(size / 128.0, size / 128.0);
                graphics.setColor(new Color(0, 0, 0, 31));
                graphics.fill(polygon(23, 17, 105, 17, 105, 97, 85, 116, 23, 116));
                graphics.setColor(Color.decode("#fff176"));
                graphics.fill(polygon(18, 12, 106, 12, 106, 89, 81, 113, 18, 113));
                graphics.setColor(Color.decode("#fff8b7"));
                graphics.fillRect(18, 12, 88, 15);
                graphics.setColor(Color.decode("#dec956"));
                graphics.fill(polygon(81, 89, 106, 89, 81, 113));
                graphics.setColor(Color.decode("#635c35"));
                graphics.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.drawLine(35, 44, 88, 44);
                graphics.drawLine(35, 60, 80, 60);
                graphics.drawLine(35, 76, 65, 76);
            } finally {
                graphics.dispose();
            }
            ImageIO.write(image, "png", resources.resolve("icons/notesy-" + size + ".png").toFile());
        }
        BufferedImage texture = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        var random = new java.util.Random(1202);
        for (int row = 0; row < 32; row++) {
            for (int column = 0; column < 32; column++) {
                int shade = 235 + random.nextInt(12);
                texture.setRGB(column, row, 0xff000000 | (shade << 16) | ((shade + 3) << 8) | shade);
            }
        }
        ImageIO.write(texture, "png", resources.resolve("paper-texture.png").toFile());
    }

    private static Path2D polygon(double... coordinates) {
        Path2D path = new Path2D.Double();
        path.moveTo(coordinates[0], coordinates[1]);
        for (int index = 2; index < coordinates.length; index += 2) path.lineTo(coordinates[index], coordinates[index + 1]);
        path.closePath();
        return path;
    }
}