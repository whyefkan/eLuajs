package eluajs.ui;

import eluajs.git.GitManager;
import eluajs.project.ProjectGradleManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;

final class ReleaseNotesDialog extends JDialog {
    private final Path root;
    private final JTextArea notes = new JTextArea();

    ReleaseNotesDialog(Window owner, Path root) {
        super(owner, t("Sürüm ve Sürüm Notları"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        this.root = root;
        setIconImage(Ui.appIcon());
        setSize(780, 620);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Theme.TITLE_BAR);
        head.setBorder(new EmptyBorder(10, 16, 10, 8));
        head.add(Ui.label(t("Sürüm ve Sürüm Notları"), 14, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        head.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        add(head, BorderLayout.NORTH);
        notes.setFont(Theme.font(12, Font.PLAIN));
        notes.setBackground(Theme.PANEL);
        notes.setForeground(Theme.TEXT);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setBorder(new EmptyBorder(12, 14, 12, 14));
        add(Ui.scroll(notes), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton reload = Ui.button(t("Yenile"), false);
        reload.addActionListener(e -> load());
        JButton copy = Ui.button(t("Kopyala"), false);
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(notes.getText()), null));
        JButton bump = Ui.button(t("Sürüm +1"), false);
        bump.addActionListener(e -> {
            try { ProjectGradleManager.incrementVersionCode(root); load(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), t("Hata"), JOptionPane.ERROR_MESSAGE); }
        });
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        actions.add(reload); actions.add(copy); actions.add(bump); actions.add(close);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Theme.BG);
        bottom.setBorder(new EmptyBorder(8, 16, 12, 16));
        bottom.add(actions, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        load();
    }

    private void load() {
        try {
            ProjectGradleManager.Versions version = ProjectGradleManager.read(root);
            GitManager.Result log = GitManager.log(root, 40);
            notes.setText(t("Yeni sürüm: %s (%d)", version.versionName(), version.versionCode()) + "\n\n"
                    + t("Değişiklikler:") + "\n" + (log.ok() ? log.output() : t("Git geçmişi yok.")));
        } catch (Exception ex) {
            notes.setText(t("Sürüm bilgisi okunamadı: %s", ex.getMessage()));
        }
    }
}
