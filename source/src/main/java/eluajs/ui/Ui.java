package eluajs.ui;

import eluajs.util.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class Ui {
    private Ui() {}

    public static JButton button(String text, boolean primary) { return new ModernButton(text, primary, false); }
    public static JButton dangerButton(String text) { return new ModernButton(text, false, true); }

    public static JButton iconButton(String text, String iconName, boolean primary) {
        JButton b = new ModernButton(text, primary, false);
        b.setIcon(Icons.icon(iconName, primary ? new Color(5, 36, 28) : Theme.TEXT_SOFT, 14));
        b.setIconTextGap(6);
        b.setBorder(new EmptyBorder(9, 12, 9, 12));
        return b;
    }

    public static JScrollPane scroll(java.awt.Component view) {
        JScrollPane pane = new JScrollPane(view);
        applyScrollTheme(pane);
        return pane;
    }

    public static void applyScrollTheme(JScrollPane pane) {
        if (pane == null) return;
        for (JScrollBar bar : new JScrollBar[]{pane.getVerticalScrollBar(), pane.getHorizontalScrollBar()}) {
            if (bar == null) continue;
            bar.setUI(new Theme.FlatScrollBarUI());
            bar.setOpaque(false);
            bar.setUnitIncrement(18);
            bar.setBorder(null);
        }
    }

    public static JLabel label(String text, int size, int style, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(size, style));
        l.setForeground(c);
        return l;
    }

    public static JLabel badge(String text, Color color) {
        return new BadgeLabel(text, color);
    }

    public static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(Theme.PANEL);
        return p;
    }

    public static JPanel card(LayoutManager layout) { return new RoundedPanel(layout, Theme.PANEL, Theme.BORDER_SOFT, 10); }
    public static JPanel softCard(LayoutManager layout) { return new RoundedPanel(layout, Theme.PANEL_2, Theme.BORDER_SOFT, 8); }

    public static JLabel sectionLabel(String text) {
        return label(text, 10, Font.BOLD, Theme.MUTED);
    }

    public static JLabel statusLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(10, Font.PLAIN));
        l.setForeground(Theme.STATUS_TEXT);
        return l;
    }

    /** FlowLayout aligns its single row to the top. Wrap it so toolbars center. */
    public static JComponent centerVertically(JComponent child) {
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.add(child);
        return wrap;
    }

    public static void restartUi() {
        SwingUtilities.invokeLater(() -> {
            for (Window w : Window.getWindows()) {
                if (w.isDisplayable()) w.dispose();
            }
            I18n.load();
            Theme.load();
            Theme.install();
            new HubFrame().setVisible(true);
        });
    }

    public static void styleCombo(JComboBox<?> combo) {
        combo.setUI(new Theme.FlatComboBoxUI());
        combo.setFont(Theme.font(13, Font.PLAIN));
        combo.setBackground(Theme.PANEL_2);
        combo.setForeground(Theme.TEXT);
    }

    public static JPanel activityBar() {
        JPanel bar = new JPanel();
        bar.setBackground(Theme.ACTIVITY_BAR);
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setPreferredSize(new Dimension(54, 1));
        bar.setBorder(new EmptyBorder(8, 1, 8, 1));
        return bar;
    }

    public static ActivityButton activityButton(String glyph, String tooltip, Runnable action) {
        ActivityButton b = new ActivityButton(glyph, tooltip);
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    public static JTextField field() {
        JTextField f = new JTextField();
        f.setFont(Theme.font(14, Font.PLAIN));
        f.setBackground(Theme.PANEL_2);
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.ACCENT);
        f.setSelectionColor(Theme.SELECTION);
        f.setSelectedTextColor(Theme.TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(9, 11, 9, 11)));
        return f;
    }

    public static JPasswordField passwordField() {
        JPasswordField f = new JPasswordField();
        f.setFont(Theme.font(14, Font.PLAIN));
        f.setBackground(Theme.PANEL_2);
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.ACCENT);
        f.setSelectionColor(Theme.SELECTION);
        f.setSelectedTextColor(Theme.TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(9, 11, 9, 11)));
        return f;
    }

    public static void styleSpinner(JSpinner spinner) {
        spinner.setFont(Theme.font(14, Font.PLAIN));
        spinner.setBackground(Theme.PANEL_2);
        spinner.setForeground(Theme.TEXT);
        spinner.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            JFormattedTextField tf = de.getTextField();
            tf.setBackground(Theme.PANEL_2);
            tf.setForeground(Theme.TEXT);
            tf.setCaretColor(Theme.ACCENT);
            tf.setSelectionColor(Theme.SELECTION);
            tf.setSelectedTextColor(Theme.TEXT);
            tf.setBorder(new EmptyBorder(8, 10, 8, 10));
        }
        for (Component c : spinner.getComponents()) {
            if (c instanceof JButton b) {
                b.setBackground(Theme.PANEL_3);
                b.setForeground(Theme.TEXT);
                b.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
                b.setFocusPainted(false);
            }
        }
    }

    public static JPanel formRow(String label, JComponent component) {
        JPanel p = new JPanel(new BorderLayout(0, 7));
        p.setOpaque(false);
        p.add(Ui.label(label, 12, Font.BOLD, Theme.MUTED), BorderLayout.NORTH);
        p.add(component, BorderLayout.CENTER);
        return p;
    }

    public static Image appIcon() {
        int s = 64;
        BufferedImage image = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, Theme.ACCENT_HOVER, s, s, new Color(37, 154, 125));
        g.setPaint(gp);
        g.fillRoundRect(3, 3, 58, 58, 18, 18);
        g.setColor(new Color(5, 32, 25));
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        FontMetrics fm = g.getFontMetrics();
        String text = "eL";
        int x = (s - fm.stringWidth(text)) / 2;
        int y = (s - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
        g.dispose();
        return image;
    }

    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final Color stroke;
        private final int radius;
        RoundedPanel(LayoutManager layout, Color fill, Color stroke, int radius) {
            super(layout); this.fill = fill; this.stroke = stroke; this.radius = radius;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            if (stroke != null) {
                g2.setColor(stroke);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class BadgeLabel extends JLabel {
        private final Color color;
        BadgeLabel(String text, Color color) {
            super(text); this.color = color;
            setForeground(color);
            setFont(Theme.font(11, Font.BOLD));
            setBorder(new EmptyBorder(5, 9, 5, 9));
            setOpaque(false);
        }

        // BoxLayout may otherwise stretch a JLabel vertically up to Short.MAX_VALUE.
        // Badges are compact UI chips and should always stay at their preferred size.
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 35));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static final class ActivityButton extends JButton {
        private boolean active;
        private final String iconName;

        ActivityButton(String iconName, String tooltip) {
            super("");
            this.iconName = iconName;
            setToolTipText(tooltip);
            setForeground(Theme.MUTED);
            setFocusable(false);
            setBorder(new EmptyBorder(0, 0, 0, 0));
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Dimension size = new Dimension(52, 44);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        public void setActive(boolean active) {
            this.active = active;
            setForeground(active ? Theme.TEXT : Theme.MUTED);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active) {
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(0, 10, 3, getHeight() - 20, 3, 3);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(255, 255, 255, 8));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            int iconSize = 21;
            Icons.draw(g2, iconName, (getWidth() - iconSize) / 2, (getHeight() - iconSize) / 2, iconSize, getForeground());
            g2.dispose();
        }
    }

    private static final class ModernButton extends JButton {
        private final boolean primary;
        private final boolean danger;

        ModernButton(String text, boolean primary, boolean danger) {
            super(text);
            this.primary = primary;
            this.danger = danger;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.font(13, Font.BOLD));
            setForeground(primary ? new Color(5, 36, 28) : (danger ? Theme.DANGER : Theme.TEXT));
            setBorder(new EmptyBorder(10, 15, 10, 15));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel m = getModel();
            Color bg;
            if (!isEnabled()) bg = new Color(35, 42, 52);
            else if (primary) bg = m.isPressed() ? Theme.ACCENT.darker() : (m.isRollover() ? Theme.ACCENT_HOVER : Theme.ACCENT);
            else if (danger) bg = m.isPressed() ? new Color(78, 33, 37) : (m.isRollover() ? new Color(66, 31, 35) : new Color(43, 27, 31));
            else bg = m.isPressed() ? Theme.PANEL_HOVER.brighter() : (m.isRollover() ? Theme.PANEL_HOVER : Theme.PANEL_2);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            if (!primary) {
                g2.setColor(danger ? new Color(100, 49, 54) : Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
