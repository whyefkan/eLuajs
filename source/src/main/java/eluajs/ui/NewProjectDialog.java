package eluajs.ui;

import eluajs.model.ProjectMeta;
import eluajs.project.ProjectTemplate;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Path;

public final class NewProjectDialog extends JDialog {
    private final JTextField appName = Ui.field();
    private final JTextField versionName = Ui.field();
    private final JSpinner versionCode = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JTextField packageName = Ui.field();
    private final JTextField logo = Ui.field();
    private final Path projectsRoot;
    private ProjectMeta created;

    public NewProjectDialog(Frame owner, Path projectsRoot) {
        super(owner, t("Yeni eLuajs Projesi"), true);
        this.projectsRoot = projectsRoot;
        setIconImage(Ui.appIcon());
        setSize(680, 640);
        setMinimumSize(new Dimension(620, 600));
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

    private void addRow(JPanel root, String name, JComponent comp) {
        JPanel row = Ui.formRow(name, comp);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        root.add(row);
        root.add(Box.createVerticalStrut(12));
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
            ProjectTemplate.create(meta);
            created = new ProjectMeta(meta.appName(), meta.versionName(), meta.versionCode(), meta.packageName(), null, meta.rootDir());
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Proje oluşturulamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public ProjectMeta created() { return created; }
}
