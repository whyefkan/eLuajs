package eluajs.ui;

import eluajs.project.ProjectAssetManager;
import eluajs.project.ProjectLibraryManager;
import eluajs.project.ProjectPermissionManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Inline asset, library and permission managers for the tools window. */
final class ManagerPanels {
    private ManagerPanels() {}

    static JPanel assets(Path root, Runnable onImported) {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setBackground(Theme.BG);
        page.setBorder(new EmptyBorder(20, 24, 16, 24));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Ui.label(t("Asset yöneticisi"), 21, Font.BOLD, Theme.TEXT));
        titles.add(Box.createVerticalStrut(5));
        titles.add(Ui.label(t("Görsel, ses, font, JSON veya başka dosyaları Android projenize ekleyin."), 11, Font.PLAIN, Theme.MUTED));
        header.add(titles, BorderLayout.CENTER);
        JButton choose = Ui.button("＋  " + t("Dosya Seç"), true);
        header.add(choose, BorderLayout.EAST);
        page.add(header, BorderLayout.NORTH);

        DefaultListModel<Path> filesModel = new DefaultListModel<>();
        JList<Path> files = new JList<>(filesModel);
        JComboBox<ProjectAssetManager.Destination> destination = new JComboBox<>(ProjectAssetManager.Destination.values());
        JLabel status = Ui.label(t("Dosya seçin ve projeye ekleyin."), 10, Font.PLAIN, Theme.MUTED);
        files.setBackground(Theme.PANEL);
        files.setForeground(Theme.TEXT);
        files.setSelectionBackground(Theme.SELECTION);
        files.setSelectionForeground(Theme.TEXT);
        files.setFont(Theme.font(12, Font.PLAIN));
        files.setFixedCellHeight(28);
        files.setBorder(new EmptyBorder(7, 9, 7, 9));
        files.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                Path p = (Path) value;
                l.setText(p.getFileName().toString() + "   —   " + p.getParent());
                l.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
                l.setForeground(Theme.TEXT);
                l.setBorder(new EmptyBorder(3, 6, 3, 6));
                return l;
            }
        });
        Ui.styleCombo(destination);
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(Ui.formRow(t("Hedef"), destination), BorderLayout.NORTH);
        JScrollPane scroll = Ui.scroll(files);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        center.add(scroll, BorderLayout.CENTER);
        JPanel tools = new JPanel(new BorderLayout());
        tools.setOpaque(false);
        tools.add(status, BorderLayout.WEST);
        JButton remove = Ui.button(t("Seçileni Kaldır"), false);
        tools.add(remove, BorderLayout.EAST);
        center.add(tools, BorderLayout.SOUTH);
        page.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton add = Ui.button(t("Assets'e Ekle"), true);
        actions.add(add);
        page.add(actions, BorderLayout.SOUTH);

        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(t("Projeye eklenecek dosyaları seçin"));
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(page) != JFileChooser.APPROVE_OPTION) return;
            for (File f : chooser.getSelectedFiles()) {
                Path p = f.toPath();
                boolean exists = false;
                for (int i = 0; i < filesModel.size(); i++) if (filesModel.get(i).equals(p)) { exists = true; break; }
                if (!exists) filesModel.addElement(p);
            }
            if (!filesModel.isEmpty()) {
                String name = filesModel.get(0).getFileName().toString().toLowerCase();
                if (name.matches(".*\\.(png|jpg|jpeg|webp|gif|xml)$")) destination.setSelectedItem(ProjectAssetManager.Destination.DRAWABLE);
                else if (name.matches(".*\\.(ttf|otf)$")) destination.setSelectedItem(ProjectAssetManager.Destination.FONT);
                else if (name.matches(".*\\.(mp3|wav|ogg|json|csv)$")) destination.setSelectedItem(ProjectAssetManager.Destination.RAW);
                else destination.setSelectedItem(ProjectAssetManager.Destination.ASSETS);
            }
            status.setText(t("%d dosya seçildi.", filesModel.size()));
        });
        remove.addActionListener(e -> {
            int[] indexes = files.getSelectedIndices();
            for (int i = indexes.length - 1; i >= 0; i--) filesModel.remove(indexes[i]);
            status.setText(filesModel.isEmpty() ? t("Dosya seçin ve projeye ekleyin.") : t("%d dosya seçildi.", filesModel.size()));
        });
        add.addActionListener(e -> {
            if (filesModel.isEmpty()) {
                JOptionPane.showMessageDialog(page, t("Önce en az bir dosya seçin."), "Assets", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            List<Path> selected = new ArrayList<>();
            for (int i = 0; i < filesModel.size(); i++) selected.add(filesModel.get(i));
            try {
                var dest = (ProjectAssetManager.Destination) destination.getSelectedItem();
                var imported = ProjectAssetManager.importFiles(root, selected, dest);
                if (onImported != null) onImported.run();
                StringBuilder msg = new StringBuilder(t("%d asset projeye eklendi.\n\n", imported.size()));
                int shown = 0;
                for (var item : imported) {
                    if (shown++ >= 8) { msg.append("…\n"); break; }
                    msg.append(root.relativize(item.target())).append('\n');
                }
                JOptionPane.showMessageDialog(page, msg.toString(), t("Assets eklendi"), JOptionPane.INFORMATION_MESSAGE);
                filesModel.clear();
                status.setText(t("Assetler eklendi. Yeni dosyalar seçebilirsiniz."));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(page, ex.getMessage(), t("Asset ekleme hatası"), JOptionPane.ERROR_MESSAGE);
            }
        });
        return page;
    }

    static JPanel libraries(Path root, Runnable onChanged, Consumer<Path> openFile) {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setBackground(Theme.BG);
        page.setBorder(new EmptyBorder(20, 24, 16, 24));
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(Ui.label(t("Kütüphane yöneticisi"), 21, Font.BOLD, Theme.TEXT));
        top.add(Box.createVerticalStrut(5));
        top.add(Ui.label(t("Maven/Google koordinatını ekle; standart Gradle kullanılır."), 11, Font.PLAIN, Theme.MUTED));
        page.add(top, BorderLayout.NORTH);

        DefaultListModel<ProjectLibraryManager.Dependency> model = new DefaultListModel<>();
        JList<ProjectLibraryManager.Dependency> list = new JList<>(model);
        JComboBox<String> configuration = new JComboBox<>(ProjectLibraryManager.configurations().toArray(String[]::new));
        JTextField notation = Ui.field();
        JCheckBox platform = new JCheckBox(t("BOM / platform dependency"));
        JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        JPanel form = Ui.panel(new GridBagLayout());
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(12, 12, 12, 12)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 0;
        g.gridx = 0; form.add(Ui.label(t("Tür"), 11, Font.BOLD, Theme.MUTED), g);
        g.gridx = 1; g.weightx = 0.3; Ui.styleCombo(configuration); form.add(configuration, g);
        g.gridx = 2; g.weightx = 0; form.add(Ui.label(t("Koordinat"), 11, Font.BOLD, Theme.MUTED), g);
        g.gridx = 3; g.weightx = 0.7; form.add(notation, g);
        g.gridx = 4; g.weightx = 0;
        JButton add = Ui.button("＋ " + t("Ekle"), true);
        form.add(add, g);
        g.gridy = 1; g.gridx = 1; g.gridwidth = 3;
        platform.setOpaque(false); platform.setForeground(Theme.MUTED); platform.setFont(Theme.font(11, Font.PLAIN));
        form.add(platform, g);
        center.add(form, BorderLayout.NORTH);

        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.TEXT);
        list.setFont(Theme.font(12, Font.PLAIN));
        list.setFixedCellHeight(30);
        list.setBorder(new EmptyBorder(7, 8, 7, 8));
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        center.add(scroll, BorderLayout.CENTER);
        JPanel under = new JPanel(new BorderLayout(10, 0));
        under.setOpaque(false);
        under.add(status, BorderLayout.CENTER);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        tools.setOpaque(false);
        JButton open = Ui.button(t("build.gradle Aç"), false);
        JButton remove = Ui.button(t("Seçileni Kaldır"), false);
        tools.add(open); tools.add(remove);
        under.add(tools, BorderLayout.EAST);
        center.add(under, BorderLayout.SOUTH);
        page.add(center, BorderLayout.CENTER);

        Runnable refresh = () -> {
            try {
                model.clear();
                for (var d : ProjectLibraryManager.list(root)) model.addElement(d);
                status.setText(t("%d Gradle bağımlılığı bulundu.", model.size()));
            } catch (Exception ex) {
                status.setText(t("Kütüphaneler okunamadı: %s", ex.getMessage()));
            }
        };
        add.addActionListener(e -> {
            try {
                var dep = ProjectLibraryManager.add(root, (String) configuration.getSelectedItem(), notation.getText(), platform.isSelected());
                notation.setText("");
                platform.setSelected(false);
                refresh.run();
                if (onChanged != null) onChanged.run();
                status.setText(t("Eklendi: %s", dep));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(page, ex.getMessage(), t("Kütüphane eklenemedi"), JOptionPane.ERROR_MESSAGE);
            }
        });
        remove.addActionListener(e -> {
            var dep = list.getSelectedValue();
            if (dep == null) return;
            if (JOptionPane.showConfirmDialog(page, t("%s kaldırılsın mı?", dep), t("Kütüphaneyi Kaldır"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            try {
                ProjectLibraryManager.remove(root, dep);
                refresh.run();
                if (onChanged != null) onChanged.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(page, ex.getMessage(), t("Kütüphane kaldırılamadı"), JOptionPane.ERROR_MESSAGE);
            }
        });
        open.addActionListener(e -> { if (openFile != null) openFile.accept(root.resolve("app/build.gradle")); });
        refresh.run();
        return page;
    }

    static JPanel permissions(Path root, Runnable onChanged, Consumer<Path> openFile) {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setBackground(Theme.BG);
        page.setBorder(new EmptyBorder(20, 24, 16, 24));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(Ui.label(t("Android izinleri"), 21, Font.BOLD, Theme.TEXT));
        header.add(Box.createVerticalStrut(5));
        header.add(Ui.label(t("Sık kullanılan izinleri yönet; AndroidManifest.xml düzenlenir."), 11, Font.PLAIN, Theme.MUTED));
        page.add(header, BorderLayout.NORTH);

        Map<String, JCheckBox> boxes = new LinkedHashMap<>();
        JPanel list = new JPanel();
        list.setBackground(Theme.PANEL);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(8, 10, 8, 10));
        for (var permission : ProjectPermissionManager.COMMON) {
            JCheckBox box = new JCheckBox(permission.title());
            box.setOpaque(false);
            box.setForeground(Theme.TEXT);
            box.setFont(Theme.font(12, Font.BOLD));
            box.setToolTipText(permission.name());
            boxes.put(permission.name(), box);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(4, 2, 4, 2));
            row.add(box, BorderLayout.WEST);
            row.add(Ui.label(permission.detail() + "   •   " + permission.name(), 10, Font.PLAIN, Theme.MUTED), BorderLayout.CENTER);
            list.add(row);
        }
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);
        JPanel customPanel = Ui.panel(new BorderLayout(8, 0));
        customPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(8, 10, 8, 10)));
        JTextField custom = Ui.field();
        JButton addCustom = Ui.button("＋ " + t("Ekle"), false);
        customPanel.add(Ui.label(t("Özel izin"), 11, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        customPanel.add(custom, BorderLayout.CENTER);
        customPanel.add(addCustom, BorderLayout.EAST);
        center.add(customPanel, BorderLayout.SOUTH);
        page.add(center, BorderLayout.CENTER);

        JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(status, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton open = Ui.button(t("Manifest'i Aç"), false);
        JButton save = Ui.button(t("İzinleri Uygula"), true);
        actions.add(open); actions.add(save);
        bottom.add(actions, BorderLayout.EAST);
        page.add(bottom, BorderLayout.SOUTH);

        Runnable load = () -> {
            try {
                Set<String> current = ProjectPermissionManager.list(root);
                for (var entry : boxes.entrySet()) entry.getValue().setSelected(current.contains(entry.getKey()));
                long customCount = current.stream().filter(p -> !boxes.containsKey(p)).count();
                status.setText(t("%d manifest izni var", current.size()) + (customCount > 0 ? t(" • %d özel", customCount) : "") + ".");
            } catch (Exception ex) {
                status.setText(ex.getMessage());
            }
        };
        save.addActionListener(e -> {
            try {
                Set<String> selected = new LinkedHashSet<>();
                for (var entry : boxes.entrySet()) if (entry.getValue().isSelected()) selected.add(entry.getKey());
                ProjectPermissionManager.applyCommon(root, selected);
                if (onChanged != null) onChanged.run();
                load.run();
                status.setText(t("İzinler AndroidManifest.xml dosyasına uygulandı."));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(page, ex.getMessage(), t("İzinler uygulanamadı"), JOptionPane.ERROR_MESSAGE);
            }
        });
        addCustom.addActionListener(e -> {
            try {
                ProjectPermissionManager.addCustom(root, custom.getText());
                custom.setText("");
                if (onChanged != null) onChanged.run();
                load.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(page, ex.getMessage(), t("İzin eklenemedi"), JOptionPane.ERROR_MESSAGE);
            }
        });
        open.addActionListener(e -> { if (openFile != null) openFile.accept(root.resolve("app/src/main/AndroidManifest.xml")); });
        load.run();
        return page;
    }
}
