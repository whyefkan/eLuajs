package eluajs.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.Path2D;

public final class Theme {
    public static final Color BG = new Color(9, 13, 19);
    public static final Color SIDEBAR = new Color(7, 11, 16);
    public static final Color TITLE_BAR = new Color(7, 11, 16);
    public static final Color ACTIVITY_BAR = new Color(5, 8, 12);
    public static final Color PANEL = new Color(15, 21, 29);
    public static final Color PANEL_2 = new Color(20, 28, 38);
    public static final Color PANEL_3 = new Color(27, 37, 49);
    public static final Color PANEL_HOVER = new Color(31, 43, 56);
    public static final Color EDITOR = new Color(11, 16, 23);
    public static final Color CONSOLE = new Color(7, 11, 16);
    public static final Color TAB_BAR = new Color(9, 13, 19);
    public static final Color TAB_ACTIVE = new Color(11, 16, 23);
    public static final Color TAB_INACTIVE = new Color(15, 21, 29);
    public static final Color BORDER = new Color(42, 55, 70);
    public static final Color BORDER_SOFT = new Color(32, 43, 56);
    public static final Color TEXT = new Color(241, 246, 251);
    public static final Color TEXT_SOFT = new Color(199, 211, 224);
    public static final Color MUTED = new Color(132, 149, 167);
    public static final Color ACCENT = new Color(55, 214, 164);
    public static final Color ACCENT_HOVER = new Color(79, 229, 183);
    public static final Color ACCENT_DARK = new Color(17, 73, 57);
    public static final Color STATUS_BG = new Color(28, 158, 125);
    public static final Color STATUS_TEXT = new Color(4, 31, 24);
    public static final Color BLUE = new Color(86, 156, 214);
    public static final Color DANGER = new Color(248, 113, 113);
    public static final Color WARNING = new Color(251, 191, 36);
    public static final Color SELECTION = new Color(34, 75, 65);

    private Theme() {}

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
        UIManager.put("ScrollBar.width", 10);
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

    /** Flat 10px scrollbar so the IDE keeps a VS Code feel without extra deps. */
    public static final class FlatScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(71, 85, 102);
            trackColor = new Color(10, 15, 21);
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
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() || isDragging ? new Color(108, 124, 143) : thumbColor);
            int pad = scrollbar.getOrientation() == JScrollBar.VERTICAL ? 2 : 2;
            g2.fillRoundRect(r.x + pad, r.y + pad, r.width - pad * 2, r.height - pad * 2, 8, 8);
            g2.dispose();
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == JScrollBar.VERTICAL
                    ? new Dimension(10, 48) : new Dimension(48, 10);
        }
    }
}
