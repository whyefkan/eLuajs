package eluajs.ui;

import eluajs.util.FileUtil;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class NotesDialog extends JDialog {
    private final Path root;
    private final Path file;
    private final JTextArea area = new JTextArea();

    NotesDialog(Window owner, Path root) {
        super(owner, t("Proje Notları"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        this.root = root;
        this.file = root.resolve(".eluajs/notes.md");
        setIconImage(Ui.appIcon());
        setSize(760, 620);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Theme.TITLE_BAR);
        head.setBorder(new EmptyBorder(10, 16, 10, 8));
        head.add(Ui.label(t("Proje Notları"), 14, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        head.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        add(head, BorderLayout.NORTH);
        area.setFont(Theme.mono(12));
        area.setBackground(Theme.EDITOR);
        area.setForeground(Theme.TEXT);
        area.setCaretColor(Theme.ACCENT);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(12, 14, 12, 14));
        try { if (Files.isRegularFile(file)) area.setText(Files.readString(file, StandardCharsets.UTF_8)); }
        catch (Exception ignored) { }
        add(Ui.scroll(area), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton save = Ui.button(t("Kaydet"), true);
        save.addActionListener(e -> save());
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        actions.add(close); actions.add(save);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Theme.BG);
        bottom.setBorder(new EmptyBorder(8, 16, 12, 16));
        bottom.add(actions, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    private void save() {
        try {
            FileUtil.recoveryCopy(root, file);
            Files.createDirectories(file.getParent());
            FileUtil.writeStringAtomic(file, area.getText());
            FileUtil.secureFile(file);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Kaydetme hatası"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
