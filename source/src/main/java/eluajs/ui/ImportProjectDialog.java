package eluajs.ui;

import eluajs.model.ProjectMeta;
import eluajs.project.ProjectImporter;
import eluajs.project.ProjectStore;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ImportProjectDialog extends JDialog {
    private final JTextField folder = Ui.field();
    private final JTextField appName = Ui.field();
    private final JTextField versionName = Ui.field();
    private final JSpinner versionCode = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JTextField packageName = Ui.field();
    private final JLabel detected = Ui.label("", 12, Font.PLAIN, Theme.MUTED);
    private final JLabel status = Ui.label("", 11, Font.PLAIN, Theme.MUTED);
    private final JButton importButton;
    private ProjectImporter.Detected project;
    private ProjectMeta imported;

    public ImportProjectDialog(Frame owner) {
        super(owner, t("Proje İçe Aktar"), true);
        setIconImage(Ui.appIcon());
        setSize(720, 690);
        setMinimumSize(new Dimension(640, 620));
        setResizable(true);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        JPanel root = new JPanel();
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(30, 36, 28, 36));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JLabel title = Ui.label(t("Mevcut Android projesi"), 25, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(6));
        JLabel subtitle = Ui.label(t("Android Studio / Gradle projesi seçin; eLuajs onu kopyalamadan listesine ekler."), 13, Font.PLAIN, Theme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(subtitle);
        root.add(Box.createVerticalStrut(22));

        folder.setEditable(true);
        folder.addActionListener(e -> detectFolder());
        JPanel folderLine = new JPanel(new BorderLayout(10, 0));
        folderLine.setOpaque(false);
        folderLine.add(folder, BorderLayout.CENTER);
        JButton choose = Ui.button(t("Klasör Seç"), false);
        choose.addActionListener(e -> chooseFolder());
        folderLine.add(choose, BorderLayout.EAST);
        addRow(root, t("Proje klasörü"), folderLine);

        JPanel info = new JPanel();
        info.setOpaque(true);
        info.setBackground(Theme.PANEL);
        info.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(12, 14, 12, 14)));
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        JLabel infoTitle = Ui.label(t("Algılanan proje"), 11, Font.BOLD, Theme.ACCENT);
        infoTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        detected.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(infoTitle);
        info.add(Box.createVerticalStrut(5));
        info.add(detected);
        info.add(Box.createVerticalStrut(4));
        info.add(status);
        root.add(info);
        root.add(Box.createVerticalStrut(12));

        appName.setText("");
        versionName.setText("1.0");
        packageName.setText("");
        Ui.styleSpinner(versionCode);
        addRow(root, t("Uygulama adı"), appName);
        addRow(root, t("Sürüm"), versionName);
        addRow(root, t("Sürüm kodu"), versionCode);
        addRow(root, t("Paket adı"), packageName);

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
        importButton = Ui.button(t("İçe Aktar"), true);
        importButton.setEnabled(false);
        importButton.addActionListener(e -> importProject());
        buttons.add(cancel);
        buttons.add(importButton);
        root.add(buttons);
        add(root, BorderLayout.CENTER);
        getRootPane().setDefaultButton(importButton);
    }

    private void addRow(JPanel root, String name, JComponent comp) {
        JPanel row = Ui.formRow(name, comp);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        root.add(row);
        root.add(Box.createVerticalStrut(12));
    }

    private void chooseFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(t("İçe aktarılacak klasörü seç"));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        if (!folder.getText().isBlank()) {
            Path current = Path.of(folder.getText());
            if (Files.isDirectory(current)) fc.setCurrentDirectory(current.toFile());
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folder.setText(fc.getSelectedFile().getAbsolutePath());
            detectFolder();
        }
    }

    private void detectFolder() {
        status.setText("");
        status.setForeground(Theme.MUTED);
        project = null;
        importButton.setEnabled(false);
        if (folder.getText().isBlank()) return;
        try {
            ProjectImporter.Detected found = ProjectImporter.detect(Path.of(folder.getText().trim()));
            project = found;
            detected.setText(found.summary());
            appName.setText(found.appName());
            versionName.setText(found.versionName());
            versionCode.setValue(Math.max(1, found.versionCode()));
            packageName.setText(found.packageName());
            importButton.setEnabled(true);
            if (ProjectStore.isImported(found.root())) {
                status.setText(t("Bu proje zaten eLuajs listesinde; bilgiler güncellenecek."));
                status.setForeground(Theme.WARNING);
            } else {
                status.setText(t("Proje kopyalanmaz; mevcut klasöründe açılır."));
            }
        } catch (Exception ex) {
            detected.setText(t("Android projesi algılanamadı."));
            status.setText(ex.getMessage() == null ? t("Seçilen klasör geçerli bir Android projesi değil.") : ex.getMessage());
            status.setForeground(Theme.DANGER);
        }
    }

    private void importProject() {
        if (project == null) {
            detectFolder();
            if (project == null) return;
        }
        try {
            int code = ((Number) versionCode.getValue()).intValue();
            ProjectImporter.install(project, appName.getText(), versionName.getText(), code, packageName.getText());
            imported = new ProjectMeta(appName.getText().trim(), versionName.getText().trim(), code,
                    packageName.getText().trim(), null, project.root());
            dispose();
        } catch (Exception ex) {
            status.setText(ex.getMessage());
            status.setForeground(Theme.DANGER);
        }
    }

    public ProjectMeta imported() { return imported; }
}
