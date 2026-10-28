package eluajs.ui;

import eluajs.project.ProjectAssetManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AssetsDialog extends JDialog {
    private final Path projectRoot;
    private final Runnable onImported;
    private final DefaultListModel<Path> filesModel = new DefaultListModel<>();
    private final JList<Path> files = new JList<>(filesModel);
    private final JComboBox<ProjectAssetManager.Destination> destination = new JComboBox<>(ProjectAssetManager.Destination.values());
    private final JLabel status = Ui.label(t("Dosya seçin ve projeye ekleyin."), 11, Font.PLAIN, Theme.MUTED);

    public AssetsDialog(Window owner, Path projectRoot, Runnable onImported) {
        super(owner, "Assets — " + t("Projeye Dosya Ekle"), ModalityType.APPLICATION_MODAL);
        this.projectRoot = projectRoot;
        this.onImported = onImported;
        setIconImage(Ui.appIcon());
        setSize(760, 560);
        setMinimumSize(new Dimension(660, 500));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(content(), BorderLayout.CENTER);
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(25, 28, 24, 28));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Ui.label(t("Asset yöneticisi"), 23, Font.BOLD, Theme.TEXT));
        titles.add(Box.createVerticalStrut(5));
        titles.add(Ui.label(t("Görsel, ses, font, JSON veya başka dosyaları Android projenize ekleyin."), 12, Font.PLAIN, Theme.MUTED));
        header.add(titles, BorderLayout.CENTER);
        JButton choose = Ui.button("＋  " + t("Dosya Seç"), true);
        choose.addActionListener(e -> chooseFiles());
        header.add(choose, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        Ui.styleCombo(destination);
        destination.setFont(Theme.font(13, Font.PLAIN));
        destination.setFocusable(false);
        center.add(Ui.formRow(t("Hedef"), destination), BorderLayout.NORTH);

        files.setBackground(Theme.PANEL);
        files.setForeground(Theme.TEXT);
        files.setSelectionBackground(Theme.SELECTION);
        files.setSelectionForeground(Theme.TEXT);
        files.setFont(Theme.font(12, Font.PLAIN));
        files.setFixedCellHeight(28);
        files.setBorder(new EmptyBorder(7, 9, 7, 9));
        files.setCellRenderer(new FileRenderer());
        JScrollPane scroll = new JScrollPane(files);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        center.add(scroll, BorderLayout.CENTER);

        JPanel tools = new JPanel(new BorderLayout());
        tools.setOpaque(false);
        tools.add(status, BorderLayout.WEST);
        JButton remove = Ui.button(t("Seçileni Kaldır"), false);
        remove.addActionListener(e -> removeSelected());
        tools.add(remove, BorderLayout.EAST);
        center.add(tools, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        JButton add = Ui.button(t("Assets'e Ekle"), true);
        add.addActionListener(e -> importAssets());
        actions.add(close);
        actions.add(add);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(t("Projeye eklenecek dosyaları seçin"));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        for (File f : chooser.getSelectedFiles()) {
            Path p = f.toPath();
            boolean exists = false;
            for (int i = 0; i < filesModel.size(); i++) if (filesModel.get(i).equals(p)) { exists = true; break; }
            if (!exists) filesModel.addElement(p);
        }
        autoDestination();
        status.setText(t("%d dosya seçildi.", filesModel.size()));
    }

    private void autoDestination() {
        if (filesModel.isEmpty()) return;
        String name = filesModel.get(0).getFileName().toString().toLowerCase();
        if (name.matches(".*\\.(png|jpg|jpeg|webp|gif|xml)$")) destination.setSelectedItem(ProjectAssetManager.Destination.DRAWABLE);
        else if (name.matches(".*\\.(ttf|otf)$")) destination.setSelectedItem(ProjectAssetManager.Destination.FONT);
        else if (name.matches(".*\\.(mp3|wav|ogg|json|csv)$")) destination.setSelectedItem(ProjectAssetManager.Destination.RAW);
        else destination.setSelectedItem(ProjectAssetManager.Destination.ASSETS);
    }

    private void removeSelected() {
        int[] indexes = files.getSelectedIndices();
        for (int i = indexes.length - 1; i >= 0; i--) filesModel.remove(indexes[i]);
        status.setText(filesModel.isEmpty() ? t("Dosya seçin ve projeye ekleyin.") : t("%d dosya seçildi.", filesModel.size()));
    }

    private void importAssets() {
        if (filesModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, t("Önce en az bir dosya seçin."), "Assets", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<Path> selected = new ArrayList<>();
        for (int i = 0; i < filesModel.size(); i++) selected.add(filesModel.get(i));
        try {
            var dest = (ProjectAssetManager.Destination) destination.getSelectedItem();
            var imported = ProjectAssetManager.importFiles(projectRoot, selected, dest);
            if (onImported != null) onImported.run();
            StringBuilder msg = new StringBuilder(t("%d asset projeye eklendi.\n\n", imported.size()));
            int shown = 0;
            for (var item : imported) {
                if (shown++ >= 8) { msg.append("…\n"); break; }
                msg.append(projectRoot.relativize(item.target())).append('\n');
            }
            JOptionPane.showMessageDialog(this, msg.toString(), t("Assets eklendi"), JOptionPane.INFORMATION_MESSAGE);
            filesModel.clear();
            status.setText(t("Assetler eklendi. Yeni dosyalar seçebilirsiniz."));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Asset ekleme hatası"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private static final class FileRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
            Path p = (Path) value;
            l.setText(p.getFileName().toString() + "   —   " + p.getParent());
            l.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
            l.setForeground(selected ? Theme.TEXT : Theme.TEXT);
            l.setBorder(new EmptyBorder(3, 6, 3, 6));
            return l;
        }
    }
}
