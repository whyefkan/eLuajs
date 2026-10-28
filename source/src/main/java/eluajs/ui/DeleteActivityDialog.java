package eluajs.ui;

import eluajs.model.ProjectMeta;
import eluajs.project.ProjectActivityManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public final class DeleteActivityDialog extends JDialog {
    private final ProjectMeta meta;
    private final JComboBox<ProjectActivityManager.ActivityInfo> pages = new JComboBox<>();
    private String deletedClassName;

    public DeleteActivityDialog(Window owner, ProjectMeta meta) {
        super(owner, t("Sayfa Sil"), ModalityType.APPLICATION_MODAL);
        this.meta = meta;
        setIconImage(Ui.appIcon());
        setSize(560, 330);
        setMinimumSize(new Dimension(500, 300));
        setLocationRelativeTo(owner);
        setContentPane(content());
        loadPages();
    }

    public String deletedClassName() { return deletedClassName; }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 22));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(26, 28, 24, 28));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(Ui.label(t("Android sayfasını sil"), 23, Font.BOLD, Theme.TEXT));
        top.add(Box.createVerticalStrut(7));
        top.add(Ui.label(t("Java dosyası ve AndroidManifest kaydı birlikte kaldırılır."), 12, Font.PLAIN, Theme.MUTED));
        root.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(Ui.label(t("Silinecek sayfa"), 12, Font.BOLD, Theme.MUTED));
        center.add(Box.createVerticalStrut(8));
        pages.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        Ui.styleCombo(pages);
        center.add(pages);
        center.add(Box.createVerticalStrut(14));
        JLabel warning = Ui.label(t("MainActivity güvenlik için silinemez. Bu sayfaya yazdığınız Intent referansları otomatik silinmez."), 11, Font.PLAIN, Theme.MUTED);
        center.add(warning);
        root.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = Ui.button(t("Vazgeç"), false);
        cancel.addActionListener(e -> dispose());
        JButton delete = Ui.button("−  " + t("Sayfayı Sil"), false);
        delete.setForeground(Theme.DANGER);
        delete.addActionListener(e -> deleteSelected());
        buttons.add(cancel);
        buttons.add(delete);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void loadPages() {
        pages.removeAllItems();
        try {
            List<ProjectActivityManager.ActivityInfo> list = ProjectActivityManager.list(meta);
            for (var page : list) pages.addItem(page);
            if (list.isEmpty()) {
                pages.setEnabled(false);
                pages.addItem(new ProjectActivityManager.ActivityInfo(t("Silinebilecek ek sayfa yok"), null, false));
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Sayfalar okunamadı"), JOptionPane.ERROR_MESSAGE);
            dispose();
        }
    }

    private void deleteSelected() {
        if (!pages.isEnabled()) return;
        Object selected = pages.getSelectedItem();
        if (!(selected instanceof ProjectActivityManager.ActivityInfo info) || info.javaFile() == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                t("%s kalıcı olarak silinsin mi?\n\nJava dosyası ve Manifest kaydı kaldırılacak.", info.className()),
                t("Sayfayı Sil"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            ProjectActivityManager.delete(meta, info.className());
            deletedClassName = info.className();
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Sayfa silinemedi"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
