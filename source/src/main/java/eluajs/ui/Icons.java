package eluajs.ui;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Dependency-free, crisp vector icon pack for the eLuajs shell.
 * Everything is painted with Java2D so the app stays lightweight.
 */
public final class Icons {
    private Icons() {}

    public static Icon icon(String name, Color color, int size) {
        return new DrawIcon(name, color, size);
    }

    public static void draw(Graphics2D g, String name, int x, int y, int size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create(x, y, size, size);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(color);
        float s = size;
        g2.setStroke(new BasicStroke(Math.max(1.35f, s / 13f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
            case "files" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .27f, s * .12f);
                p.lineTo(s * .60f, s * .12f);
                p.lineTo(s * .78f, s * .30f);
                p.lineTo(s * .78f, s * .88f);
                p.lineTo(s * .27f, s * .88f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .60f, s * .12f, s * .60f, s * .30f));
                g2.draw(new Line2D.Float(s * .60f, s * .30f, s * .78f, s * .30f));
                g2.draw(new Line2D.Float(s * .38f, s * .47f, s * .67f, s * .47f));
                g2.draw(new Line2D.Float(s * .38f, s * .60f, s * .67f, s * .60f));
                g2.draw(new Line2D.Float(s * .38f, s * .73f, s * .57f, s * .73f));
            }
            case "pages" -> {
                RoundRectangle2D back = new RoundRectangle2D.Float(s * .20f, s * .18f, s * .46f, s * .56f, s * .10f, s * .10f);
                RoundRectangle2D front = new RoundRectangle2D.Float(s * .34f, s * .28f, s * .46f, s * .56f, s * .10f, s * .10f);
                g2.draw(back);
                g2.draw(front);
            }
            case "search" -> {
                g2.draw(new Ellipse2D.Float(s * .20f, s * .18f, s * .48f, s * .48f));
                g2.draw(new Line2D.Float(s * .60f, s * .60f, s * .82f, s * .82f));
            }
            case "assets" -> {
                g2.draw(new RoundRectangle2D.Float(s * .16f, s * .20f, s * .68f, s * .60f, s * .10f, s * .10f));
                g2.draw(new Ellipse2D.Float(s * .28f, s * .30f, s * .13f, s * .13f));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .22f, s * .72f);
                p.lineTo(s * .42f, s * .50f);
                p.lineTo(s * .56f, s * .64f);
                p.lineTo(s * .66f, s * .54f);
                p.lineTo(s * .78f, s * .70f);
                g2.draw(p);
            }
            case "library" -> {
                g2.draw(new RoundRectangle2D.Float(s * .16f, s * .16f, s * .28f, s * .28f, s * .06f, s * .06f));
                g2.draw(new RoundRectangle2D.Float(s * .56f, s * .16f, s * .28f, s * .28f, s * .06f, s * .06f));
                g2.draw(new RoundRectangle2D.Float(s * .16f, s * .56f, s * .28f, s * .28f, s * .06f, s * .06f));
                g2.draw(new RoundRectangle2D.Float(s * .56f, s * .56f, s * .28f, s * .28f, s * .06f, s * .06f));
            }
            case "permissions" -> {
                g2.draw(new RoundRectangle2D.Float(s * .25f, s * .45f, s * .50f, s * .42f, s * .10f, s * .10f));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .34f, s * .45f);
                p.lineTo(s * .34f, s * .33f);
                p.curveTo(s * .34f, s * .16f, s * .66f, s * .16f, s * .66f, s * .33f);
                p.lineTo(s * .66f, s * .45f);
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .50f, s * .58f, s * .50f, s * .72f));
            }
            case "home" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .14f, s * .48f);
                p.lineTo(s * .50f, s * .16f);
                p.lineTo(s * .86f, s * .48f);
                p.lineTo(s * .78f, s * .48f);
                p.lineTo(s * .78f, s * .84f);
                p.lineTo(s * .58f, s * .84f);
                p.lineTo(s * .58f, s * .62f);
                p.lineTo(s * .42f, s * .62f);
                p.lineTo(s * .42f, s * .84f);
                p.lineTo(s * .22f, s * .84f);
                p.lineTo(s * .22f, s * .48f);
                p.closePath();
                g2.draw(p);
            }
            case "folder" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .14f, s * .30f);
                p.lineTo(s * .42f, s * .30f);
                p.lineTo(s * .50f, s * .40f);
                p.lineTo(s * .86f, s * .40f);
                p.lineTo(s * .86f, s * .82f);
                p.lineTo(s * .14f, s * .82f);
                p.closePath();
                g2.draw(p);
            }
            case "plus" -> {
                g2.draw(new Line2D.Float(s * .50f, s * .22f, s * .50f, s * .78f));
                g2.draw(new Line2D.Float(s * .22f, s * .50f, s * .78f, s * .50f));
            }
            case "refresh" -> {
                g2.draw(new Arc2D.Float(s * .20f, s * .20f, s * .60f, s * .60f, 40, 280, Arc2D.OPEN));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .79f, s * .22f);
                p.lineTo(s * .82f, s * .42f);
                p.lineTo(s * .62f, s * .39f);
                g2.draw(p);
            }
            case "settings" -> {
                g2.draw(new Ellipse2D.Float(s * .32f, s * .32f, s * .36f, s * .36f));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    float x1 = (float) (s * .50f + Math.cos(a) * s * .26f);
                    float y1 = (float) (s * .50f + Math.sin(a) * s * .26f);
                    float x2 = (float) (s * .50f + Math.cos(a) * s * .39f);
                    float y2 = (float) (s * .50f + Math.sin(a) * s * .39f);
                    g2.draw(new Line2D.Float(x1, y1, x2, y2));
                }
            }
            case "ai" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .52f, s * .10f);
                p.lineTo(s * .61f, s * .38f);
                p.lineTo(s * .90f, s * .47f);
                p.lineTo(s * .61f, s * .56f);
                p.lineTo(s * .52f, s * .86f);
                p.lineTo(s * .43f, s * .56f);
                p.lineTo(s * .14f, s * .47f);
                p.lineTo(s * .43f, s * .38f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .76f, s * .72f, s * .76f, s * .88f));
                g2.draw(new Line2D.Float(s * .68f, s * .80f, s * .84f, s * .80f));
            }
            case "about" -> {
                g2.draw(new Ellipse2D.Float(s * .14f, s * .14f, s * .72f, s * .72f));
                g2.draw(new Line2D.Float(s * .50f, s * .43f, s * .50f, s * .70f));
                g2.fill(new Ellipse2D.Float(s * .47f, s * .27f, s * .06f, s * .06f));
            }
            case "history" -> {
                g2.draw(new Ellipse2D.Float(s * .18f, s * .18f, s * .64f, s * .64f));
                g2.draw(new Line2D.Float(s * .50f, s * .32f, s * .50f, s * .52f));
                g2.draw(new Line2D.Float(s * .50f, s * .52f, s * .66f, s * .60f));
            }
            case "undo" -> {
                g2.draw(new Arc2D.Float(s * .18f, s * .20f, s * .64f, s * .64f, 45, 270, Arc2D.OPEN));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .22f, s * .30f);
                p.lineTo(s * .12f, s * .52f);
                p.lineTo(s * .34f, s * .58f);
                g2.draw(p);
            }
            case "user" -> {
                g2.draw(new Ellipse2D.Float(s * .36f, s * .18f, s * .28f, s * .28f));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .20f, s * .84f);
                p.curveTo(s * .22f, s * .58f, s * .78f, s * .58f, s * .80f, s * .84f);
                g2.draw(p);
            }
            case "send" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .16f, s * .50f);
                p.lineTo(s * .84f, s * .22f);
                p.lineTo(s * .62f, s * .80f);
                p.lineTo(s * .48f, s * .58f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .48f, s * .58f, s * .84f, s * .22f));
            }
            case "check" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .20f, s * .54f);
                p.lineTo(s * .42f, s * .76f);
                p.lineTo(s * .82f, s * .26f);
                g2.draw(p);
            }
            case "minimize" -> g2.draw(new Line2D.Float(s * .24f, s * .50f, s * .76f, s * .50f));
            case "maximize" -> g2.draw(new RoundRectangle2D.Float(s * .24f, s * .24f, s * .52f, s * .52f, s * .06f, s * .06f));
            case "restore" -> {
                g2.draw(new RoundRectangle2D.Float(s * .18f, s * .30f, s * .46f, s * .42f, s * .05f, s * .05f));
                g2.draw(new RoundRectangle2D.Float(s * .36f, s * .18f, s * .46f, s * .42f, s * .05f, s * .05f));
            }
            case "close" -> {
                g2.draw(new Line2D.Float(s * .28f, s * .28f, s * .72f, s * .72f));
                g2.draw(new Line2D.Float(s * .72f, s * .28f, s * .28f, s * .72f));
            }
            case "run" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .32f, s * .20f);
                p.lineTo(s * .78f, s * .50f);
                p.lineTo(s * .32f, s * .80f);
                p.closePath();
                g2.draw(p);
            }
            case "save" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .22f, s * .16f);
                p.lineTo(s * .70f, s * .16f);
                p.lineTo(s * .82f, s * .28f);
                p.lineTo(s * .82f, s * .84f);
                p.lineTo(s * .22f, s * .84f);
                p.closePath();
                g2.draw(p);
                g2.draw(new RoundRectangle2D.Float(s * .34f, s * .52f, s * .34f, s * .24f, s * .04f, s * .04f));
            }
            case "terminal" -> {
                g2.draw(new RoundRectangle2D.Float(s * .12f, s * .20f, s * .76f, s * .60f, s * .10f, s * .10f));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .28f, s * .40f);
                p.lineTo(s * .40f, s * .50f);
                p.lineTo(s * .28f, s * .60f);
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .48f, s * .60f, s * .66f, s * .60f));
            }
            default -> g2.draw(new Ellipse2D.Float(s * .26f, s * .26f, s * .48f, s * .48f));
        }
        g2.dispose();
    }

    public static Image projectIcon(String appName, String packageName, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int hash = (appName + "|" + packageName).hashCode();
        float hue = ((hash & 0x7fffffff) % 360) / 360f;
        Color a = Color.getHSBColor(hue, 0.52f, 0.86f);
        Color b = Color.getHSBColor((hue + 0.04f) % 1f, 0.72f, 0.52f);
        g.setPaint(new GradientPaint(0, 0, a, size, size, b));
        int pad = Math.max(1, size / 16);
        g.fillRoundRect(pad, pad, size - pad * 2, size - pad * 2, Math.max(6, size / 4), Math.max(6, size / 4));
        g.setColor(new Color(255, 255, 255, 32));
        g.fillRoundRect(size / 8, size / 10, size - size / 4, Math.max(2, size / 10), size / 8, size / 8);
        String initial = initial(appName);
        int fontSize = Math.max(9, (int) (size * 0.48f));
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int tx = (size - fm.stringWidth(initial)) / 2;
        int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(new Color(8, 22, 20, 190));
        g.drawString(initial, tx + Math.max(1, size / 48), ty + Math.max(1, size / 48));
        g.setColor(new Color(10, 32, 28));
        g.drawString(initial, tx, ty);
        g.dispose();
        return image;
    }

    public static Icon fileIcon(Path path, boolean directory, boolean expanded) {
        if (directory) return new TreeIcon("folder", new Color(226, 184, 107), 16);
        String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        String type;
        Color color;
        if (name.endsWith(".java")) { type = "J"; color = new Color(224, 108, 117); }
        else if (name.endsWith(".xml")) { type = "X"; color = new Color(86, 156, 214); }
        else if (name.endsWith(".gradle") || name.endsWith(".kts")) { type = "G"; color = new Color(87, 186, 120); }
        else if (name.endsWith(".properties")) { type = "#"; color = new Color(150, 160, 175); }
        else if (name.endsWith(".json")) { type = "{}"; color = new Color(214, 186, 92); }
        else if (name.matches(".*\\.(png|jpg|jpeg|webp|gif|svg)$")) { type = "I"; color = new Color(176, 128, 214); }
        else if (name.endsWith(".kt")) { type = "K"; color = new Color(170, 130, 240); }
        else { type = "·"; color = new Color(120, 136, 154); }
        return new TreeIcon("file:" + type, color, 16);
    }

    private static String initial(String text) {
        if (text == null || text.isBlank()) return "A";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) return String.valueOf(Character.toUpperCase(c));
        }
        return "A";
    }

    private static final class DrawIcon implements Icon {
        private final String name;
        private final Color color;
        private final int size;
        DrawIcon(String name, Color color, int size) { this.name = name; this.color = color; this.size = size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) { draw((Graphics2D) g, name, x, y, size, color); }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }

    private static final class TreeIcon implements Icon {
        private final String kind;
        private final Color color;
        private final int size;
        TreeIcon(String kind, Color color, int size) { this.kind = kind; this.color = color; this.size = size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create(x, y, size, size);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if ("folder".equals(kind)) {
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(1.5f, 4f); p.lineTo(6f, 4f); p.lineTo(7.6f, 6f); p.lineTo(14.5f, 6f);
                p.lineTo(14.5f, 13.5f); p.lineTo(1.5f, 13.5f); p.closePath();
                g2.draw(p);
            } else {
                String glyph = kind.substring(5);
                g2.setColor(color);
                g2.fillRoundRect(1, 1, size - 2, size - 2, 4, 4);
                g2.setColor(Color.WHITE);
                int fs = glyph.length() > 1 ? 6 : 9;
                g2.setFont(new Font("SansSerif", Font.BOLD, fs));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (size - fm.stringWidth(glyph)) / 2;
                int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(glyph, tx, ty);
            }
            g2.dispose();
        }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }
}
