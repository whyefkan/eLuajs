package eluajs.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Theme {
    public static Color BG = new Color(9, 13, 19);
    public static Color SIDEBAR = new Color(7, 11, 16);
    public static Color TITLE_BAR = new Color(7, 11, 16);
    public static Color ACTIVITY_BAR = new Color(5, 8, 12);
    public static Color PANEL = new Color(15, 21, 29);
    public static Color PANEL_2 = new Color(20, 28, 38);
    public static Color PANEL_3 = new Color(27, 37, 49);
    public static Color PANEL_HOVER = new Color(31, 43, 56);
    public static Color EDITOR = new Color(11, 16, 23);
    public static Color CONSOLE = new Color(7, 11, 16);
    public static Color TAB_BAR = new Color(9, 13, 19);
    public static Color TAB_ACTIVE = new Color(11, 16, 23);
    public static Color TAB_INACTIVE = new Color(15, 21, 29);
    public static Color BORDER = new Color(42, 55, 70);
    public static Color BORDER_SOFT = new Color(32, 43, 56);
    public static Color TEXT = new Color(241, 246, 251);
    public static Color TEXT_SOFT = new Color(199, 211, 224);
    public static Color MUTED = new Color(132, 149, 167);
    public static Color ACCENT = new Color(55, 214, 164);
    public static Color ACCENT_HOVER = new Color(79, 229, 183);
    public static Color ACCENT_DARK = new Color(17, 73, 57);
    public static Color STATUS_BG = new Color(28, 158, 125);
    public static Color STATUS_TEXT = new Color(4, 31, 24);
    public static Color BLUE = new Color(86, 156, 214);
    public static Color DANGER = new Color(248, 113, 113);
    public static Color WARNING = new Color(251, 191, 36);
    public static Color SELECTION = new Color(34, 75, 65);

    private static boolean light;
    private static boolean pureBlack;
    private static String mode = "dark";
    private static String accent = "mint";

    private Theme() {}

    public static boolean isLight() { return light; }
    public static boolean isPureBlack() { return pureBlack; }
    public static String mode() { return mode; }
    public static String accent() { return accent; }

    public static void load() {
        Properties p = new Properties();
        Path file = settingsFile();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            catch (IOException ignored) { }
        }
        accent = p.getProperty("accent", "mint");
        setMode(p.getProperty("theme", "dark"));
    }

    public static void setAccent(String value) {
        accent = normalizeAccent(value);
        applyPalette();
        Properties p = new Properties();
        Path file = settingsFile();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            catch (IOException ignored) { }
        }
        p.setProperty("theme", mode);
        p.setProperty("accent", accent);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) { p.store(out, "eLuajs settings"); }
        } catch (IOException ignored) { }
    }

    private static String normalizeAccent(String value) {
        String next = value == null ? "mint" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (next) { case "blue", "purple", "orange", "pink" -> next; default -> "mint"; };
    }

    public static void setLight(boolean value) {
        setMode(value ? "light" : "dark");
    }

    public static void setMode(String value) {
        String next = value == null ? "dark" : value.trim().toLowerCase(java.util.Locale.ROOT);
        mode = next.equals("light") || next.equals("black") ? next : "dark";
        light = mode.equals("light");
        pureBlack = mode.equals("black");
        applyPalette();
        Properties p = new Properties();
        Path file = settingsFile();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            catch (IOException ignored) { }
        }
        p.setProperty("theme", mode);
        p.setProperty("accent", accent);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) { p.store(out, "eLuajs settings"); }
        } catch (IOException ignored) { }
    }

    private static Path settingsFile() {
        return eluajs.util.AppPaths.config().resolve("settings.properties");
    }

    private static void applyPalette() {
        if (pureBlack) {
            BG = new Color(0, 0, 0);
            SIDEBAR = new Color(0, 0, 0);
            TITLE_BAR = new Color(0, 0, 0);
            ACTIVITY_BAR = new Color(0, 0, 0);
            PANEL = new Color(10, 10, 10);
            PANEL_2 = new Color(18, 18, 18);
            PANEL_3 = new Color(26, 26, 26);
            PANEL_HOVER = new Color(34, 34, 34);
            EDITOR = new Color(0, 0, 0);
            CONSOLE = new Color(0, 0, 0);
            TAB_BAR = new Color(0, 0, 0);
            TAB_ACTIVE = new Color(10, 10, 10);
            TAB_INACTIVE = new Color(5, 5, 5);
            BORDER = new Color(42, 42, 42);
            BORDER_SOFT = new Color(26, 26, 26);
            TEXT = new Color(255, 255, 255);
            TEXT_SOFT = new Color(208, 208, 208);
            MUTED = new Color(138, 138, 138);
            ACCENT = new Color(55, 214, 164);
            ACCENT_HOVER = new Color(79, 229, 183);
            ACCENT_DARK = new Color(11, 59, 46);
            STATUS_BG = new Color(15, 157, 118);
            STATUS_TEXT = new Color(255, 255, 255);
            BLUE = new Color(86, 156, 214);
            DANGER = new Color(248, 113, 113);
            WARNING = new Color(251, 191, 36);
            SELECTION = new Color(31, 74, 61);
        } else if (!light) {
            BG = new Color(9, 13, 19);
            SIDEBAR = new Color(7, 11, 16);
            TITLE_BAR = new Color(7, 11, 16);
            ACTIVITY_BAR = new Color(5, 8, 12);
            PANEL = new Color(15, 21, 29);
            PANEL_2 = new Color(20, 28, 38);
            PANEL_3 = new Color(27, 37, 49);
            PANEL_HOVER = new Color(31, 43, 56);
            EDITOR = new Color(11, 16, 23);
            CONSOLE = new Color(7, 11, 16);
            TAB_BAR = new Color(9, 13, 19);
            TAB_ACTIVE = new Color(11, 16, 23);
            TAB_INACTIVE = new Color(15, 21, 29);
            BORDER = new Color(42, 55, 70);
            BORDER_SOFT = new Color(32, 43, 56);
            TEXT = new Color(241, 246, 251);
            TEXT_SOFT = new Color(199, 211, 224);
            MUTED = new Color(132, 149, 167);
            ACCENT = new Color(55, 214, 164);
            ACCENT_HOVER = new Color(79, 229, 183);
            ACCENT_DARK = new Color(17, 73, 57);
            STATUS_BG = new Color(28, 158, 125);
            STATUS_TEXT = new Color(4, 31, 24);
            BLUE = new Color(86, 156, 214);
            DANGER = new Color(248, 113, 113);
            WARNING = new Color(251, 191, 36);
            SELECTION = new Color(34, 75, 65);
        } else {
            BG = new Color(245, 247, 250);
            SIDEBAR = new Color(238, 242, 247);
            TITLE_BAR = new Color(232, 237, 243);
            ACTIVITY_BAR = new Color(223, 230, 238);
            PANEL = new Color(255, 255, 255);
            PANEL_2 = new Color(240, 243, 247);
            PANEL_3 = new Color(230, 235, 241);
            PANEL_HOVER = new Color(221, 229, 238);
            EDITOR = new Color(255, 255, 255);
            CONSOLE = new Color(247, 249, 251);
            TAB_BAR = new Color(238, 242, 247);
            TAB_ACTIVE = new Color(255, 255, 255);
            TAB_INACTIVE = new Color(231, 237, 244);
            BORDER = new Color(199, 210, 223);
            BORDER_SOFT = new Color(219, 227, 236);
            TEXT = new Color(23, 33, 43);
            TEXT_SOFT = new Color(52, 73, 94);
            MUTED = new Color(107, 124, 147);
            ACCENT = new Color(15, 157, 118);
            ACCENT_HOVER = new Color(11, 138, 104);
            ACCENT_DARK = new Color(217, 245, 236);
            STATUS_BG = new Color(15, 157, 118);
            STATUS_TEXT = new Color(255, 255, 255);
            BLUE = new Color(37, 99, 235);
            DANGER = new Color(220, 38, 38);
            WARNING = new Color(180, 83, 9);
            SELECTION = new Color(205, 238, 227);
        }
        applyAccent();
    }

    private static void applyAccent() {
        Color base = switch (normalizeAccent(accent)) {
            case "blue" -> new Color(86, 156, 214);
            case "purple" -> new Color(181, 140, 255);
            case "orange" -> new Color(255, 173, 92);
            case "pink" -> new Color(255, 122, 182);
            default -> new Color(55, 214, 164);
        };
        ACCENT = light ? base.darker() : base;
        ACCENT_HOVER = light ? base : base.brighter();
        ACCENT_DARK = blend(light ? new Color(255, 255, 255) : new Color(0, 0, 0), ACCENT, light ? .82f : .36f);
        STATUS_BG = light ? ACCENT : ACCENT.darker();
        STATUS_TEXT = light ? new Color(255, 255, 255) : new Color(4, 31, 24);
        SELECTION = blend(light ? new Color(255, 255, 255) : PANEL, ACCENT, light ? .82f : .30f);
    }

    private static Color blend(Color a, Color b, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        return new Color(
                Math.round(a.getRed() * (1 - r) + b.getRed() * r),
                Math.round(a.getGreen() * (1 - r) + b.getGreen() * r),
                Math.round(a.getBlue() * (1 - r) + b.getBlue() * r));
    }

    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}

        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.background", PANEL_2);
        UIManager.put("Button.foreground", TEXT);

        UIManager.put("TextField.background", PANEL_2);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.selectionBackground", SELECTION);
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(9, 11, 9, 11)));

        UIManager.put("FormattedTextField.background", PANEL_2);
        UIManager.put("FormattedTextField.foreground", TEXT);
        UIManager.put("FormattedTextField.caretForeground", ACCENT);
        UIManager.put("FormattedTextField.selectionBackground", SELECTION);
        UIManager.put("FormattedTextField.selectionForeground", TEXT);

        UIManager.put("TextArea.background", PANEL);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", ACCENT);
        UIManager.put("TextArea.selectionBackground", SELECTION);
        UIManager.put("TextArea.selectionForeground", TEXT);

        UIManager.put("Tree.background", PANEL);
        UIManager.put("Tree.foreground", TEXT);
        UIManager.put("Tree.textBackground", PANEL);
        UIManager.put("Tree.textForeground", TEXT);
        UIManager.put("Tree.selectionBackground", SELECTION);
        UIManager.put("Tree.selectionForeground", TEXT);
        UIManager.put("Tree.selectionBorderColor", SELECTION);
        UIManager.put("Tree.hash", BORDER_SOFT);

        UIManager.put("List.background", PANEL);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", SELECTION);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("ComboBox.background", PANEL_2);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", SELECTION);
        UIManager.put("ComboBox.selectionForeground", TEXT);

        UIManager.put("TabbedPane.background", TAB_BAR);
        UIManager.put("TabbedPane.foreground", TEXT_SOFT);
        UIManager.put("TabbedPane.selected", TAB_ACTIVE);
        UIManager.put("TabbedPane.contentAreaColor", EDITOR);
        UIManager.put("TabbedPane.focus", ACCENT);
        UIManager.put("TabbedPane.borderHightlightColor", BORDER);
        UIManager.put("TabbedPane.darkShadow", BORDER);
        UIManager.put("TabbedPane.light", PANEL_2);

        UIManager.put("CheckBox.background", BG);
        UIManager.put("CheckBox.foreground", MUTED);
        UIManager.put("Spinner.background", PANEL_2);
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("ScrollBar.background", PANEL);
        UIManager.put("ScrollBarUI", FlatScrollBarUI.class.getName());
        UIManager.put("ScrollBar.width", 9);
        UIManager.put("ComboBoxUI", FlatComboBoxUI.class.getName());
        UIManager.put("Separator.foreground", BORDER_SOFT);
        UIManager.put("ToolTip.background", PANEL_3);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("ProgressBar.background", PANEL_2);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.selectionBackground", BG);
        UIManager.put("ProgressBar.selectionForeground", TEXT);
        UIManager.put("FileChooser.background", PANEL);
        UIManager.put("Menu.background", PANEL_2);
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("MenuItem.background", PANEL_2);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("PopupMenu.background", PANEL_2);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
    }

    public static Font font(int size, int style) { return new Font("SansSerif", style, size); }
    public static Font mono(int size) { return new Font(Font.MONOSPACED, Font.PLAIN, size); }

    /** Flat combo box with a drawn chevron so dropdowns match the dark shell. */
    public static final class FlatComboBoxUI extends BasicComboBoxUI {
        @Override protected JButton createArrowButton() {
            JButton button = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.MUTED);
                    g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int w = getWidth();
                    int h = getHeight();
                    Path2D.Float chevron = new Path2D.Float();
                    chevron.moveTo(w * .36, h * .42);
                    chevron.lineTo(w * .50, h * .58);
                    chevron.lineTo(w * .64, h * .42);
                    g2.draw(chevron);
                    g2.dispose();
                }
            };
            button.setBorder(null);
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setFocusable(false);
            button.setPreferredSize(new Dimension(24, 24));
            return button;
        }

        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            g.setColor(Theme.PANEL_2);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }

    /** Flat, borderless editor tabs with a clear active underline. */
    public static final class FlatTabbedPaneUI extends BasicTabbedPaneUI {
        @Override protected int calculateTabHeight(int placement, int index, int fontHeight) {
            return Math.max(36, super.calculateTabHeight(placement, index, fontHeight) + 6);
        }

        @Override protected int calculateTabWidth(int placement, int index, FontMetrics metrics) {
            return Math.max(136, super.calculateTabWidth(placement, index, metrics) + 22);
        }

        @Override protected Insets getTabInsets(int placement, int index) {
            return new Insets(7, 12, 7, 10);
        }

        @Override protected Insets getSelectedTabPadInsets(int placement) {
            return new Insets(0, 0, 0, 0);
        }

        @Override protected void paintTabArea(Graphics g, int placement, int selectedIndex) {
            g.setColor(TAB_BAR);
            g.fillRect(0, 0, tabPane.getWidth(),
                    calculateTabAreaHeight(placement, runCount, maxTabHeight));
            super.paintTabArea(g, placement, selectedIndex);
        }

        @Override protected void paintTabBackground(Graphics g, int placement, int index,
                int x, int y, int w, int h, boolean selected) {
            g.setColor(selected ? TAB_ACTIVE : TAB_BAR);
            g.fillRect(x, y, w, h);
        }

        @Override protected void paintTabBorder(Graphics g, int placement, int index,
                int x, int y, int w, int h, boolean selected) {
            if (selected) {
                g.setColor(ACCENT);
                g.fillRect(x, y, w, 2);
            }
            g.setColor(BORDER_SOFT);
            g.drawLine(x + w - 1, y + 8, x + w - 1, y + h - 8);
        }

        @Override protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
            int area = calculateTabAreaHeight(placement, runCount, maxTabHeight);
            g.setColor(BORDER_SOFT);
            g.drawLine(0, area - 1, tabPane.getWidth(), area - 1);
        }

        @Override protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects,
                int index, Rectangle icon, Rectangle text, boolean selected) { }
    }

    /** Flat, theme-aware scrollbar so the IDE keeps a VS Code feel without extra deps. */
    public static final class FlatScrollBarUI extends BasicScrollBarUI {
        @Override protected void installDefaults() {
            super.installDefaults();
            scrollbar.setOpaque(false);
            scrollbar.setUnitIncrement(18);
        }

        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        private static JButton zeroButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            b.setBorder(null);
            return b;
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color normal = Theme.isLight() ? new Color(151, 169, 188) : new Color(71, 85, 102);
            Color hover = Theme.isLight() ? new Color(105, 131, 156) : new Color(108, 124, 143);
            g2.setColor(isThumbRollover() || scrollbar.getValueIsAdjusting() ? hover : normal);
            int pad = scrollbar.getOrientation() == JScrollBar.VERTICAL ? 2 : 2;
            g2.fillRoundRect(r.x + pad, r.y + pad, Math.max(3, r.width - pad * 2),
                    Math.max(3, r.height - pad * 2), 8, 8);
            g2.dispose();
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == JScrollBar.VERTICAL
                    ? new Dimension(9, 48) : new Dimension(48, 9);
        }
    }
}