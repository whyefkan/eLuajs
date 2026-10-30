package eluajs.ui;

import eluajs.util.AppPaths;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;

import static eluajs.util.I18n.t;

/** Short first-run tour; shown once per installation. */
final class WelcomeTourDialog extends JDialog {
    WelcomeTourDialog(Window owner) {
        super(owner, t("eLuajs'a hoş geldin"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        setIconImage(Ui.appIcon());
        setSize(620, 460);
        setMinimumSize(new Dimension(540, 400));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setBackground(Theme.BG);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(24, 32, 18, 32));
        JLabel title = Ui.label(t("Hızlı başlangıç"), 24, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(6));
        JLabel sub = Ui.label(t("eLuajs hafif kalır: tek tıkla proje, kod, doğrulama ve APK/AAB."), 12, Font.PLAIN, Theme.MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(sub);
        body.add(Box.createVerticalStrut(18));
        tip(body, "1", t("Yeni proje oluştur"), t("Hub'dan New Project ile gerçek Gradle projesi aç."));
        tip(body, "2", t("Dosyaları düzenle"), t("FILES ve OUTLINE sekmelerini kullan; Ctrl+P ile hızlı dosya aç."));
        tip(body, "3", t("AI ile üret"), t("Ctrl+Alt+A asistanı aç; /page, /fix ve /explain komutlarını dene."));
        tip(body, "4", t("Çıktı al"), t("Validate ve Get output ile APK veya AAB üret."));
        body.add(Box.createVerticalGlue());
        add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 24, 18, 24));
        JButton start = Ui.button(t("Başla"), true);
        start.addActionListener(e -> dispose());
        footer.add(start);
        add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(start);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                try { Files.createDirectories(AppPaths.config()); Files.writeString(AppPaths.config().resolve("onboarding.done"), "1"); }
                catch (Exception ignored) { }
            }
        });
    }

    private void tip(JPanel body, String number, String title, String text) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        JLabel badge = Ui.badge(number, Theme.ACCENT);
        badge.setPreferredSize(new Dimension(26, 26));
        row.add(badge, BorderLayout.WEST);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(Ui.label(title, 12, Font.BOLD, Theme.TEXT));
        copy.add(Box.createVerticalStrut(2));
        copy.add(Ui.label(text, 10, Font.PLAIN, Theme.MUTED));
        row.add(copy, BorderLayout.CENTER);
        body.add(row);
        body.add(Box.createVerticalStrut(12));
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 54));
        bar.setBorder(new EmptyBorder(0, 16, 0, 8));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(24, 24, Image.SCALE_SMOOTH))));
        brand.add(Ui.label(t("eLuajs'a hoş geldin"), 15, Font.BOLD, Theme.TEXT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }
}
