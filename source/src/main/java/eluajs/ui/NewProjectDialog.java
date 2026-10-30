package eluajs.ui;

import eluajs.model.ProjectMeta;
import eluajs.project.ProjectTemplate;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class NewProjectDialog extends JDialog {
    private final JTextField appName = Ui.field();
    private final JTextField versionName = Ui.field();
    private final JSpinner versionCode = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JTextField packageName = Ui.field();
    private final JTextField logo = Ui.field();
    private final JComboBox<ProjectTemplate.Template> template = new JComboBox<>(ProjectTemplate.Template.values());
    private final JLabel templateDescription = Ui.label("", 12, Font.PLAIN, Theme.MUTED);
    private final Map<ProjectTemplate.Template, JToggleButton> templateButtons = new EnumMap<>(ProjectTemplate.Template.class);
    private final Path projectsRoot;
    private ProjectMeta created;

    public NewProjectDialog(Frame owner, Path projectsRoot) {
        super(owner, t("Yeni eLuajs Projesi"), true);
        this.projectsRoot = projectsRoot;
        setIconImage(Ui.appIcon());
        setSize(700, 720);
        setMinimumSize(new Dimension(620, 680));
        setResizable(true);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        JPanel root = new JPanel();
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(30, 36, 28, 36));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JLabel title = Ui.label(t("Yeni Android projesi"), 25, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(6));
        JLabel subtitle = Ui.label(t("Gerçek Java + Android Gradle proje yapısı oluşturulur."), 13, Font.PLAIN, Theme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(subtitle);
        root.add(Box.createVerticalStrut(24));

        appName.setText(t("Benim Uygulamam"));
        versionName.setText("1.0");
        packageName.setText("com.example.myapplication");
        logo.setEditable(false);
        Ui.styleSpinner(versionCode);

        addRow(root, t("Uygulama adı"), appName);
        addRow(root, t("Sürüm"), versionName);
        addRow(root, t("Sürüm kodu"), versionCode);
        addRow(root, t("Paket adı"), packageName);

        Ui.styleCombo(template);
        template.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                if (value instanceof ProjectTemplate.Template item) label.setText(templateLabel(item));
                label.setBackground(selected ? Theme.SELECTION : Theme.PANEL_2);
                label.setForeground(Theme.TEXT);
                label.setBorder(new EmptyBorder(4, 8, 4, 8));
                return label;
            }
        });
        JPanel templateBox = new JPanel(new BorderLayout(0, 6));
        templateBox.setOpaque(false);
        templateBox.add(template, BorderLayout.NORTH);
        templateBox.add(templateDescription, BorderLayout.SOUTH);
        template.addActionListener(e -> {
            templateDescription.setText(templateDescription((ProjectTemplate.Template) template.getSelectedItem()));
            refreshTemplateCards();
        });
        templateDescription.setText(templateDescription(ProjectTemplate.Template.EMPTY));
        refreshTemplateCards();
        JPanel gallery = new JPanel(new GridLayout(1, 5, 8, 0));
        gallery.setOpaque(false);
        gallery.setAlignmentX(Component.LEFT_ALIGNMENT);
        gallery.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (ProjectTemplate.Template item : ProjectTemplate.Template.values()) {
            JToggleButton card = new JToggleButton(templateLabel(item));
            card.setFont(Theme.font(10, Font.BOLD));
            card.setFocusPainted(false);
            card.setOpaque(true);
            card.setBackground(Theme.PANEL_2);
            card.setForeground(Theme.TEXT_SOFT);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER),
                    new EmptyBorder(7, 6, 7, 6)));
            card.setToolTipText(templateDescription(item));
            card.addActionListener(e -> {
                template.setSelectedItem(item);
                templateDescription.setText(templateDescription(item));
                refreshTemplateCards();
            });
            group.add(card);
            templateButtons.put(item, card);
            gallery.add(card);
        }
        root.add(gallery);
        root.add(Box.createVerticalStrut(8));
        addRow(root, t("Şablon"), templateBox, 92);

        JPanel logoLine = new JPanel(new BorderLayout(10, 0));
        logoLine.setOpaque(false);
        logoLine.add(logo, BorderLayout.CENTER);
        JButton choose = Ui.button(t("Logo Seç"), false);
        choose.addActionListener(e -> chooseLogo());
        logoLine.add(choose, BorderLayout.EAST);
        addRow(root, t("Uygulama logosu (isteğe bağlı)"), logoLine);

        root.add(Box.createVerticalGlue());
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        root.add(sep);
        root.add(Box.createVerticalStrut(18));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton cancel = Ui.button(t("Vazgeç"), false);
        cancel.addActionListener(e -> dispose());
        JButton create = Ui.button(t("Projeyi Oluştur"), true);
        create.addActionListener(e -> create());
        buttons.add(cancel);
        buttons.add(create);
        root.add(buttons);
        add(root, BorderLayout.CENTER);
        getRootPane().setDefaultButton(create);
    }

    private void refreshTemplateCards() {
        ProjectTemplate.Template selected = (ProjectTemplate.Template) template.getSelectedItem();
        for (var entry : templateButtons.entrySet()) {
            boolean active = entry.getKey() == selected;
            JToggleButton card = entry.getValue();
            card.setSelected(active);
            card.setBackground(active ? Theme.ACCENT_DARK : Theme.PANEL_2);
            card.setForeground(active ? Theme.ACCENT : Theme.TEXT_SOFT);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(active ? Theme.ACCENT : Theme.BORDER),
                    new EmptyBorder(7, 6, 7, 6)));
        }
    }

    private void addRow(JPanel root, String name, JComponent comp) {
        addRow(root, name, comp, 70);
    }

    private void addRow(JPanel root, String name, JComponent comp, int maxHeight) {
        JPanel row = Ui.formRow(name, comp);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
        root.add(row);
        root.add(Box.createVerticalStrut(12));
    }

    private String templateLabel(ProjectTemplate.Template template) {
        return switch (template) {
            case LOGIN -> t("Giriş ekranı");
            case WEBVIEW -> t("WebView uygulaması");
            case NOTES -> t("Notlar");
            case MATERIAL -> t("Panel / Kartlar");
            default -> t("Boş uygulama");
        };
    }

    private String templateDescription(ProjectTemplate.Template template) {
        if (template == null) return "";
        return switch (template) {
            case LOGIN -> t("E-posta ve parola alanları olan giriş ekranı.");
            case WEBVIEW -> t("Çevrimdışı HTML ile hazır WebView demosu.");
            case NOTES -> t("Kalıcı not / yapılacaklar ekranı.");
            case MATERIAL -> t("Yuvarlatılmış kartlar ve anahtar içeren panel.");
            default -> t("Uygulama adınızla temiz bir başlangıç ekranı.");
        };
    }

    private void chooseLogo() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(t("Uygulama logosu seç"));
        fc.setFileFilter(new FileNameExtensionFilter(t("Görsel dosyaları (PNG/JPG)"), "png", "jpg", "jpeg"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) logo.setText(fc.getSelectedFile().getAbsolutePath());
    }

    private void create() {
        try {
            String name = appName.getText().trim();
            String folder = name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
            if (folder.isBlank()) folder = "AndroidProject";
            Path target = projectsRoot.resolve(folder);
            int suffix = 2;
            while (target.toFile().exists()) target = projectsRoot.resolve(folder + "-" + suffix++);
            Path logoPath = logo.getText().isBlank() ? null : Path.of(logo.getText());
            ProjectMeta meta = new ProjectMeta(name, versionName.getText().trim(), ((Number)versionCode.getValue()).intValue(), packageName.getText().trim(), logoPath, target);
            ProjectTemplate.create(meta, (ProjectTemplate.Template) template.getSelectedItem());
            created = new ProjectMeta(meta.appName(), meta.versionName(), meta.versionCode(), meta.packageName(), null, meta.rootDir());
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Proje oluşturulamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public ProjectMeta created() { return created; }
}
