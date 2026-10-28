package eluajs.ui;

import eluajs.project.ProjectLibraryManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class LibrariesDialog extends JDialog {
    private final Path projectRoot;
    private final Runnable onChanged;
    private final Consumer<Path> openFile;
    private final DefaultListModel<ProjectLibraryManager.Dependency> model = new DefaultListModel<>();
    private final JList<ProjectLibraryManager.Dependency> list = new JList<>(model);
    private final JComboBox<String> configuration = new JComboBox<>(ProjectLibraryManager.configurations().toArray(String[]::new));
    private final JTextField notation = Ui.field();
    private final JCheckBox platform = new JCheckBox("BOM / platform dependency");
    private final JLabel status = Ui.label(t("Gradle bağımlılıkları app/build.gradle dosyasına eklenir."), 11, Font.PLAIN, Theme.MUTED);

    public LibrariesDialog(Window owner, Path projectRoot, Runnable onChanged, Consumer<Path> openFile) {
        super(owner, t("Kütüphaneler — Gradle"), ModalityType.APPLICATION_MODAL);
        this.projectRoot = projectRoot;
        this.onChanged = onChanged;
        this.openFile = openFile;
        setIconImage(Ui.appIcon());
        setSize(820, 610);
        setMinimumSize(new Dimension(700, 520));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setContentPane(content());
        refresh();
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(24, 28, 24, 28));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(Ui.label(t("Kütüphane yöneticisi"), 23, Font.BOLD, Theme.TEXT));
        top.add(Box.createVerticalStrut(5));
        top.add(Ui.label(t("Maven/Google koordinatını ekle; eLuajs kütüphanenin kodunu çevirmeden standart Gradle kullanır."), 12, Font.PLAIN, Theme.MUTED));
        root.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 15));
        center.setOpaque(false);

        JPanel form = Ui.panel(new GridBagLayout());
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(14, 14, 14, 14)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 0;
        g.gridx = 0; g.weightx = 0.0; form.add(Ui.label(t("Tür"), 12, Font.BOLD, Theme.MUTED), g);
        g.gridx = 1; g.weightx = 0.30;
        Ui.styleCombo(configuration);
        configuration.setBackground(Theme.PANEL_2); configuration.setForeground(Theme.TEXT);
        form.add(configuration, g);
        g.gridx = 2; g.weightx = 0.0; form.add(Ui.label(t("Koordinat"), 12, Font.BOLD, Theme.MUTED), g);
        g.gridx = 3; g.weightx = 0.70;
        notation.setToolTipText("Örnek: com.squareup.okhttp3:okhttp:4.12.0");
        form.add(notation, g);
        g.gridx = 4; g.weightx = 0.0;
        JButton add = Ui.button("＋ " + t("Ekle"), true);
        add.addActionListener(e -> addDependency());
        form.add(add, g);
        g.gridy = 1; g.gridx = 1; g.gridwidth = 3;
        platform.setOpaque(false); platform.setForeground(Theme.MUTED); platform.setFont(Theme.font(12, Font.PLAIN));
        form.add(platform, g);
        g.gridx = 4; g.gridwidth = 1;
        form.add(Ui.label(t("Ctrl+Space: editör tamamlama"), 10, Font.PLAIN, Theme.MUTED), g);
        center.add(form, BorderLayout.NORTH);

        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.TEXT);
        list.setFont(Theme.font(12, Font.PLAIN));
        list.setFixedCellHeight(30);
        list.setBorder(new EmptyBorder(7, 8, 7, 8));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        center.add(scroll, BorderLayout.CENTER);

        JPanel under = new JPanel(new BorderLayout(10, 0));
        under.setOpaque(false);
        under.add(status, BorderLayout.CENTER);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        tools.setOpaque(false);
        JButton open = Ui.button(t("build.gradle Aç"), false);
        open.addActionListener(e -> {
            Path p = projectRoot.resolve("app/build.gradle");
            if (openFile != null) openFile.accept(p);
            dispose();
        });
        JButton remove = Ui.button(t("Seçileni Kaldır"), false);
        remove.setForeground(Theme.DANGER);
        remove.addActionListener(e -> removeDependency());
        tools.add(open); tools.add(remove);
        under.add(tools, BorderLayout.EAST);
        center.add(under, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        JLabel note = Ui.label(t("Not: Bazı kütüphaneler ayrıca plugin, JSON dosyası veya özel Gradle ayarı isteyebilir."), 10, Font.PLAIN, Theme.MUTED);
        actions.add(note, BorderLayout.WEST);
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        actions.add(close, BorderLayout.EAST);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private void refresh() {
        try {
            model.clear();
            for (var d : ProjectLibraryManager.list(projectRoot)) model.addElement(d);
            status.setText(t("%d Gradle bağımlılığı bulundu.", model.size()));
        } catch (Exception ex) {
            status.setText(t("Kütüphaneler okunamadı: %s", ex.getMessage()));
        }
    }

    private void addDependency() {
        try {
            var dep = ProjectLibraryManager.add(projectRoot,
                    (String) configuration.getSelectedItem(), notation.getText(), platform.isSelected());
            notation.setText("");
            platform.setSelected(false);
            refresh();
            if (onChanged != null) onChanged.run();
            status.setText(t("Eklendi: %s", dep));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Kütüphane eklenemedi"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeDependency() {
        var dep = list.getSelectedValue();
        if (dep == null) return;
        int answer = JOptionPane.showConfirmDialog(this, t("%s kaldırılsın mı?", dep), t("Kütüphaneyi Kaldır"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            ProjectLibraryManager.remove(projectRoot, dep);
            refresh();
            if (onChanged != null) onChanged.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Kütüphane kaldırılamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
