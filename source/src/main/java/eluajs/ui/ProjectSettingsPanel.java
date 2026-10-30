package eluajs.ui;

import eluajs.project.ProjectGradleManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;

/** Embeddable project settings form. */
final class ProjectSettingsPanel extends JPanel {
    private final Path root;
    private final Runnable onSaved;
    private final JTextField versionName = Ui.field();
    private final JSpinner versionCode = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JSpinner compileSdk = new JSpinner(new SpinnerNumberModel(36, 21, 99, 1));
    private final JSpinner minSdk = new JSpinner(new SpinnerNumberModel(23, 21, 99, 1));
    private final JSpinner targetSdk = new JSpinner(new SpinnerNumberModel(36, 21, 99, 1));
    private final JLabel applicationId = Ui.label("—", 12, Font.PLAIN, Theme.TEXT_SOFT);
    private final JLabel namespace = Ui.label("—", 12, Font.PLAIN, Theme.TEXT_SOFT);
    private final JCheckBox parallel = new JCheckBox(t("Paralel derleme"));
    private final JCheckBox caching = new JCheckBox(t("Build cache"));
    private final JCheckBox daemon = new JCheckBox(t("Gradle daemon"));
    private final JCheckBox configurationCache = new JCheckBox(t("Configuration cache"));
    private final JTextField jvmArgs = Ui.field();
    private final JLabel status = Ui.label("", 11, Font.PLAIN, Theme.MUTED);

    ProjectSettingsPanel(Path root, Runnable onSaved) {
        this.root = root;
        this.onSaved = onSaved;
        setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(content(), BorderLayout.CENTER);
        load();
    }

    private JComponent content() {
        JPanel page = new JPanel();
        page.setBackground(Theme.BG);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(18, 22, 16, 22));

        JPanel app = Ui.card(new BorderLayout(0, 10));
        app.setAlignmentX(Component.LEFT_ALIGNMENT);
        app.setBorder(new EmptyBorder(14, 16, 14, 16));
        app.add(Ui.sectionLabel(t("UYGULAMA")), BorderLayout.NORTH);
        JPanel appBody = new JPanel();
        appBody.setOpaque(false);
        appBody.setLayout(new BoxLayout(appBody, BoxLayout.Y_AXIS));
        addRow(appBody, t("Sürüm adı"), versionName);
        addRow(appBody, t("Sürüm kodu"), versionCode);
        Ui.styleSpinner(versionCode);
        JPanel ids = new JPanel(new GridLayout(2, 2, 10, 6));
        ids.setOpaque(false);
        ids.add(Ui.label(t("Application ID"), 10, Font.PLAIN, Theme.MUTED));
        ids.add(applicationId);
        ids.add(Ui.label(t("Namespace"), 10, Font.PLAIN, Theme.MUTED));
        ids.add(namespace);
        appBody.add(ids);
        app.add(appBody, BorderLayout.CENTER);
        page.add(app);
        page.add(Box.createVerticalStrut(12));

        JPanel sdk = Ui.card(new BorderLayout(0, 10));
        sdk.setAlignmentX(Component.LEFT_ALIGNMENT);
        sdk.setBorder(new EmptyBorder(14, 16, 14, 16));
        sdk.add(Ui.sectionLabel(t("ANDROID SDK")), BorderLayout.NORTH);
        JPanel sdkBody = new JPanel(new GridLayout(1, 3, 12, 0));
        sdkBody.setOpaque(false);
        sdkBody.add(spinnerField(t("Compile SDK"), compileSdk));
        sdkBody.add(spinnerField(t("Min SDK"), minSdk));
        sdkBody.add(spinnerField(t("Target SDK"), targetSdk));
        sdk.add(sdkBody, BorderLayout.CENTER);
        page.add(sdk);
        page.add(Box.createVerticalStrut(12));

        JPanel gradle = Ui.card(new BorderLayout(0, 10));
        gradle.setAlignmentX(Component.LEFT_ALIGNMENT);
        gradle.setBorder(new EmptyBorder(14, 16, 14, 16));
        gradle.add(Ui.sectionLabel(t("GRADLE PERFORMANSI")), BorderLayout.NORTH);
        JPanel gradleBody = new JPanel();
        gradleBody.setOpaque(false);
        gradleBody.setLayout(new BoxLayout(gradleBody, BoxLayout.Y_AXIS));
        for (JCheckBox box : new JCheckBox[]{parallel, caching, daemon, configurationCache}) {
            box.setOpaque(false);
            box.setForeground(Theme.TEXT_SOFT);
            box.setFont(Theme.font(12, Font.PLAIN));
            box.setFocusPainted(false);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            gradleBody.add(box);
            gradleBody.add(Box.createVerticalStrut(3));
        }
        gradleBody.add(Box.createVerticalStrut(5));
        JLabel jvmLabel = Ui.label(t("JVM argümanları"), 10, Font.PLAIN, Theme.MUTED);
        jvmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        gradleBody.add(jvmLabel);
        jvmArgs.setAlignmentX(Component.LEFT_ALIGNMENT);
        jvmArgs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        gradleBody.add(jvmArgs);
        gradle.add(gradleBody, BorderLayout.CENTER);
        page.add(gradle);
        page.add(Box.createVerticalStrut(10));

        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(status);
        page.add(Box.createVerticalGlue());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton reload = Ui.button(t("Yenile"), false);
        reload.addActionListener(e -> load());
        JButton save = Ui.button(t("Kaydet"), true);
        save.addActionListener(e -> save());
        footer.add(reload);
        footer.add(save);
        page.add(footer);

        JScrollPane scroll = Ui.scroll(page);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JPanel spinnerField(String label, JSpinner spinner) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        p.add(Ui.label(label, 10, Font.PLAIN, Theme.MUTED), BorderLayout.NORTH);
        Ui.styleSpinner(spinner);
        p.add(spinner, BorderLayout.CENTER);
        return p;
    }

    private void addRow(JPanel parent, String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel name = Ui.label(label, 11, Font.PLAIN, Theme.TEXT_SOFT);
        name.setPreferredSize(new Dimension(130, 28));
        row.add(name, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createVerticalStrut(7));
    }

    private void load() {
        try {
            ProjectGradleManager.Versions versions = ProjectGradleManager.read(root);
            versionName.setText(versions.versionName());
            versionCode.setValue(Math.max(1, versions.versionCode()));
            compileSdk.setValue(versions.compileSdk());
            minSdk.setValue(versions.minSdk());
            targetSdk.setValue(versions.targetSdk());
            applicationId.setText(versions.applicationId().isBlank() ? "—" : versions.applicationId());
            namespace.setText(versions.namespace().isBlank() ? "—" : versions.namespace());
            ProjectGradleManager.Performance performance = ProjectGradleManager.readPerformance(root);
            parallel.setSelected(performance.parallel());
            caching.setSelected(performance.caching());
            daemon.setSelected(performance.daemon());
            configurationCache.setSelected(performance.configurationCache());
            jvmArgs.setText(performance.jvmArgs());
            status.setForeground(Theme.MUTED);
            status.setText(t("Değişiklikler build.gradle ve gradle.properties dosyalarına yazılır."));
        } catch (Exception ex) {
            status.setForeground(Theme.DANGER);
            status.setText(t("Ayarlar okunamadı: %s", ex.getMessage()));
        }
    }

    private void save() {
        int code = ((Number) versionCode.getValue()).intValue();
        int compile = ((Number) compileSdk.getValue()).intValue();
        int min = ((Number) minSdk.getValue()).intValue();
        int target = ((Number) targetSdk.getValue()).intValue();
        if (versionName.getText().isBlank()) {
            status.setForeground(Theme.DANGER);
            status.setText(t("Sürüm adı boş olamaz."));
            return;
        }
        if (min > target || target > compile) {
            status.setForeground(Theme.DANGER);
            status.setText(t("SDK seviyeleri min ≤ target ≤ compile olmalıdır."));
            return;
        }
        try {
            ProjectGradleManager.Versions current = ProjectGradleManager.read(root);
            ProjectGradleManager.write(root, new ProjectGradleManager.Versions(
                    versionName.getText().trim(), code, current.applicationId(), current.namespace(),
                    compile, min, target));
            ProjectGradleManager.writePerformance(root, new ProjectGradleManager.Performance(
                    jvmArgs.getText(), parallel.isSelected(), caching.isSelected(),
                    daemon.isSelected(), configurationCache.isSelected()));
            if (onSaved != null) onSaved.run();
            status.setForeground(Theme.ACCENT);
            status.setText(t("Proje ayarları kaydedildi."));
        } catch (Exception ex) {
            status.setForeground(Theme.DANGER);
            status.setText(t("Kaydedilemedi: %s", ex.getMessage()));
        }
    }
}
