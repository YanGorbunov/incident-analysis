package io.github.yangorbunov.incidents.report;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Круговая диаграмма в PNG средствами Java2D — без JFreeChart и без шрифтов:
 * подписи и легенда выводятся таблицей в документе, а не на картинке
 * (в контейнере без шрифтов текст на картинке превращается в квадраты).
 */
public final class PieChartRenderer {

    public record Slice(double value, String hexColor) {
    }

    private PieChartRenderer() {
    }

    public static byte[] render(List<Slice> slices, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double total = slices.stream().mapToDouble(Slice::value).sum();
            int pad = Math.max(2, size / 40);
            int d = size - 2 * pad;
            if (total <= 0) {
                g.setColor(new Color(0xDDDDDD));
                g.fillOval(pad, pad, d, d);
            } else {
                double start = 90;
                for (Slice s : slices) {
                    if (s.value() <= 0) {
                        continue;
                    }
                    double extent = -360.0 * s.value() / total;
                    var arc = new Arc2D.Double(pad, pad, d, d, start, extent, Arc2D.PIE);
                    g.setColor(Color.decode(s.hexColor()));
                    g.fill(arc);
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(Math.max(1f, size / 200f)));
                    g.draw(arc);
                    start += extent;
                }
            }
        } finally {
            g.dispose();
        }
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
