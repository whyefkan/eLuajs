package eluajs.ui;

import eluajs.project.ProjectCheckpoints;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

final class CheckpointsDialog extends JDialog {
    private final Path root;
    private final Runnable afterRestore;
    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);

    CheckpointsDialog(Window owner, Path root, Runnable afterRestore) {
        super(owner, t("Proje Checkpoint'leri"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        this.root = root;
        this.afterRestore = afterRestore;
        setIconImage(Ui.appIcon());
        setSize(720, 540);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        add(titleBar(), BorderLayout.NORTH);
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.TEXT);
        list.setFont(Theme.mono(11));
        list.setFixedCellHeight(30);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                if (value instanceof Path path) label.setText(path.getFileName().toString());
                label.setBorder(new EmptyBorder(3, 6, 3, 6));
                label.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                return label;
            }
        });
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBackground(Theme.BG);
        body.setBorder(new EmptyBorder(14, 18, 16, 18));
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        body.add(scroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton create = Ui.button(t("Checkpoint Oluştur"), true);
        create.addActionListener(e -> create());
        JButton restore = Ui.button(t("Geri Yükle"), false);
        restore.addActionListener(e -> restore());
        JButton delete = Ui.dangerButton(t("Sil"));
        delete.addActionListener(e -> delete());
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        actions.add(create);
        actions.add(restore);
        actions.add(delete);
        actions.add(close);
        body.add(actions, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
        refresh();
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 50));
        bar.setBorder(new EmptyBorder(0, 16, 0, 8));
        JLabel title = Ui.label(t("Proje Checkpoint'leri"), 14, Font.BOLD, Theme.TEXT);
        bar.add(Ui.centerVertically(title), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private void refresh() {
        model.clear();
        ProjectCheckpoints.list(root).forEach(model::addElement);
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private void create() {
        String label = JOptionPane.showInputDialog(this, t("Checkpoint adı"), "checkpoint");
        if (label == null) return;
        try {
            ProjectCheckpoints.create(root, label);
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Checkpoint oluşturulamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restore() {
        Path selected = list.getSelectedValue();
        if (selected == null) return;
        if (JOptionPane.showConfirmDialog(this, t("Proje bu checkpoint'e geri yüklensin mi?"),
                t("Geri Yükle"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            ProjectCheckpoints.restore(root, selected);
            if (afterRestore != null) afterRestore.run();
            JOptionPane.showMessageDialog(this, t("Checkpoint geri yüklendi."), t("Geri Yükle"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Geri yüklenemedi"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void delete() {
        Path selected = list.getSelectedValue();
        if (selected == null) return;
        try {
            ProjectCheckpoints.delete(root, selected);
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Silme hatası"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
