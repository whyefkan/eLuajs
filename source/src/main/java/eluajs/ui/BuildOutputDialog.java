package eluajs.ui;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Small output chooser that keeps the build window focused. */
final class BuildOutputDialog extends JDialog {
    BuildOutputDialog(Window owner, Runnable apk, Runnable aab) {
        super(owner, t("Çıktı Al"), ModalityType.APPLICATION_MODAL);
        setIconImage(Ui.appIcon());
        setSize(560, 390);
        setMinimumSize(new Dimension(500, 350));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

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

        JPanel choices = new JPanel(new GridLayout(1, 2, 12, 0));
        choices.setOpaque(false);
        choices.setPreferredSize(new Dimension(1, 180));
        choices.add(choice("▶", t("Debug APK"), t("Hızlı test ve cihaza kurulum"), true, () -> {
            dispose();
            if (apk != null) apk.run();
        }));
        choices.add(choice("◆", t("Play Store AAB"), t("İmzalı Google Play çıktısı"), false, () -> {
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

    private JButton choice(String icon, String title, String subtitle, boolean primary, Runnable action) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout(0, 8));
        button.setBackground(Theme.PANEL_2);
        button.setForeground(Theme.TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? Theme.ACCENT : Theme.BORDER),
                new EmptyBorder(18, 16, 16, 16)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel iconLabel = Ui.label(icon, 26, Font.BOLD, primary ? Theme.ACCENT : Theme.BLUE);
        iconLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JLabel titleLabel = Ui.label(title, 15, Font.BOLD, Theme.TEXT);
        JLabel subtitleLabel = Ui.label(subtitle, 10, Font.PLAIN, Theme.MUTED);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(titleLabel);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitleLabel);
        button.add(iconLabel, BorderLayout.NORTH);
        button.add(text, BorderLayout.CENTER);
        button.addActionListener(e -> action.run());
        return button;
    }
}
