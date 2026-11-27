package eluajs.ui;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Small output chooser that keeps the build window focused. */
final class BuildOutputDialog extends JDialog {
    BuildOutputDialog(Window owner, Runnable apk, Runnable aab) {
        super(owner, t("Çıktı Al"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        setIconImage(Ui.appIcon());
        setSize(600, 440);
        setMinimumSize(new Dimension(540, 400));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(24, 28, 22, 28));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = Ui.label(t("Ne üretmek istiyorsun?"), 23, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = Ui.label(t("APK test içindir; AAB Google Play yüklemesi içindir."), 11, Font.PLAIN, Theme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);
        root.add(header, BorderLayout.NORTH);

        JPanel choices = new JPanel(new GridLayout(1, 2, 14, 0));
        choices.setOpaque(false);
        choices.setPreferredSize(new Dimension(1, 180));
        choices.add(choice("device", t("Debug APK"), t("Hızlı test ve cihaza kurulum"), Theme.ACCENT, true, () -> {
            dispose();
            if (apk != null) apk.run();
        }));
        choices.add(choice("package", t("Play Store AAB"), t("İmzalı Google Play çıktısı"), Theme.BLUE, false, () -> {
            dispose();
            if (aab != null) aab.run();
        }));
        root.add(choices, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        JButton cancel = Ui.button(t("Vazgeç"), false);
        cancel.addActionListener(e -> dispose());
        footer.add(cancel);
        root.add(footer, BorderLayout.SOUTH);

        add(root, BorderLayout.CENTER);
        getRootPane().setDefaultButton(cancel);
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 54));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(0, 16, 0, 8)));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(24, 24, Image.SCALE_SMOOTH))));
        brand.add(Ui.label(t("Çıktı Al"), 15, Font.BOLD, Theme.TEXT));
        brand.add(Ui.badge("v" + HubFrame.VERSION, Theme.ACCENT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JButton choice(String iconName, String title, String subtitle, Color accent, boolean primary, Runnable action) {
        JButton button = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = getModel().isRollover();
                boolean pressed = getModel().isPressed();
                g2.setColor(pressed ? Theme.PANEL_HOVER : (hover ? Theme.PANEL_2 : Theme.PANEL));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                Color borderColor = (primary || hover)
                        ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), hover ? 220 : 140)
                        : Theme.BORDER;
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(hover ? 1.5f : 1.0f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setLayout(new BorderLayout(0, 12));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(18, 18, 18, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JComponent badge = new JComponent() {
            {
                Dimension d = new Dimension(40, 40);
                setPreferredSize(d);
                setMinimumSize(d);
                setMaximumSize(d);
                setOpaque(false);
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 34));
                g2.fillRoundRect(0, 0, 40, 40, 11, 11);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 100));
                g2.drawRoundRect(0, 0, 39, 39, 11, 11);
                Icons.draw(g2, iconName, 10, 10, 20, accent);
                g2.dispose();
            }
        };
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(badge, BorderLayout.WEST);
        topRow.add(new JLabel(Icons.icon("arrow-right", Theme.MUTED, 15)), BorderLayout.EAST);

        JLabel titleLabel = Ui.label(title, 15, Font.BOLD, Theme.TEXT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitleLabel = Ui.label(subtitle, 11, Font.PLAIN, Theme.MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(titleLabel);
        text.add(Box.createVerticalStrut(5));
        text.add(subtitleLabel);
        button.add(topRow, BorderLayout.NORTH);
        button.add(text, BorderLayout.CENTER);
        button.addActionListener(e -> action.run());
        return button;
    }
}
