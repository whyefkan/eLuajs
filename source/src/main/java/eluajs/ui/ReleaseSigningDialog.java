package eluajs.ui;

import eluajs.android.ReleaseSigningConfig;
import eluajs.android.ToolchainManager;
import eluajs.project.ProjectReleaseManager;
import eluajs.util.AppPaths;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.*;
import java.util.function.Consumer;

/** Kullanıcı dostu Play Store upload-key / AAB imzalama ekranı. */
public final class ReleaseSigningDialog extends JDialog {
    private final Path projectRoot;
    private final Consumer<ReleaseSigningConfig> onBuild;
    private final Runnable onOpenSettings;

    private final JTextField storeFile = Ui.field();
    private final JTextField alias = Ui.field();
    private final JPasswordField storePassword = Ui.passwordField();
    private final JPasswordField keyPassword = Ui.passwordField();
    private final JCheckBox remember = new JCheckBox(t("Keystore yolu + alias’ı bu projede hatırla"));
    private final JCheckBox rememberPasswords = new JCheckBox(t("Şifreleri de kaydet (önerilmez)"));
    private final JLabel state = Ui.label(t("Upload key bilgilerini girin veya yeni keystore oluşturun."), 11, Font.PLAIN, Theme.MUTED);

    public ReleaseSigningDialog(Dialog owner, Path projectRoot, Consumer<ReleaseSigningConfig> onBuild, Runnable onOpenSettings) {
        super(owner, t("eLuajs — Play Store AAB"), true);
        this.projectRoot = projectRoot;
        this.onBuild = onBuild;
        this.onOpenSettings = onOpenSettings;
        setIconImage(Ui.appIcon());
        setSize(690, 650);
        setMinimumSize(new Dimension(620, 600));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(content(), BorderLayout.CENTER);
        loadSaved();
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(22, 26, 22, 26));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        title.setOpaque(false);
        title.add(Ui.label(t("Play Store release AAB"), 23, Font.BOLD, Theme.TEXT));
        title.add(Ui.badge("SIGNED RELEASE", Theme.ACCENT));
        heading.add(title);
        heading.add(Box.createVerticalStrut(5));
        heading.add(Ui.label(t("AAB, seçtiğiniz upload keystore ile imzalanır. Debug imzası kullanılmaz."), 12, Font.PLAIN, Theme.MUTED));
        heading.add(Box.createVerticalStrut(12));

        JPanel notice = Ui.softCard(new BorderLayout());
        notice.setBorder(new EmptyBorder(11, 13, 11, 13));
        JLabel note = Ui.label(t("Önemli: Bu keystore'un güvenli bir yedeğini alın. Play App Signing kullanırken bu anahtar upload key olarak kullanılır."), 11, Font.PLAIN, Theme.TEXT_SOFT);
        notice.add(note, BorderLayout.CENTER);
        heading.add(notice);
        root.add(heading, BorderLayout.NORTH);

        JPanel form = Ui.card(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 12, 0);

        JPanel fileLine = new JPanel(new BorderLayout(7, 0));
        fileLine.setOpaque(false);
        fileLine.add(storeFile, BorderLayout.CENTER);
        JButton choose = Ui.button(t("Seç"), false);
        choose.addActionListener(e -> chooseKeystore());
        fileLine.add(choose, BorderLayout.EAST);
        form.add(Ui.formRow(t("Keystore / upload key dosyası"), fileLine), c);

        c.gridy++;
        form.add(Ui.formRow(t("Key alias"), alias), c);
        c.gridy++;
        form.add(Ui.formRow(t("Keystore şifresi"), storePassword), c);
        c.gridy++;
        form.add(Ui.formRow(t("Anahtar şifresi"), keyPassword), c);

        c.gridy++;
        JPanel memory = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        memory.setOpaque(false);
        styleCheck(remember); styleCheck(rememberPasswords);
        rememberPasswords.setEnabled(false);
        remember.addActionListener(e -> rememberPasswords.setEnabled(remember.isSelected()));
        memory.add(remember); memory.add(rememberPasswords);
        form.add(memory, c);

        c.gridy++;
        JPanel helpers = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        helpers.setOpaque(false);
        JButton create = Ui.button("＋ " + t("Yeni Keystore Oluştur"), false);
        create.addActionListener(e -> createKeystore());
        JButton settings = Ui.button("</> " + t("signing.properties Aç"), false);
        settings.setToolTipText(t("İsterseniz aynı bilgileri proje içindeki ayar dosyasına elle yazabilirsiniz."));
        settings.addActionListener(e -> openSettings());
        helpers.add(create); helpers.add(settings);
        form.add(helpers, c);

        c.gridy++; c.insets = new Insets(8, 0, 0, 0);
        form.add(state, c);
        root.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = Ui.button(t("Vazgeç"), false);
        cancel.addActionListener(e -> dispose());
        JButton build = Ui.button("▶  " + t("İmzalı AAB Oluştur"), true);
        build.addActionListener(e -> submit());
        actions.add(cancel); actions.add(build);
        root.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(build);
        return root;
    }

    private void styleCheck(JCheckBox c) {
        c.setOpaque(false); c.setForeground(Theme.MUTED); c.setFont(Theme.font(11, Font.PLAIN)); c.setFocusPainted(false);
    }

    private void loadSaved() {
        var saved = ProjectReleaseManager.load(projectRoot);
        if (saved.storeFile() != null) storeFile.setText(saved.storeFile().toString());
        alias.setText(saved.keyAlias() == null || saved.keyAlias().isBlank() ? "upload" : saved.keyAlias());
        storePassword.setText(saved.storePassword());
        keyPassword.setText(saved.keyPassword());
        if (!saved.storePassword().isEmpty() || !saved.keyPassword().isEmpty()) {
            remember.setSelected(true); rememberPasswords.setEnabled(true); rememberPasswords.setSelected(true);
            state.setText(t("Kaydedilmiş signing bilgileri yüklendi."));
        } else if (saved.storeFile() != null) {
            remember.setSelected(true); rememberPasswords.setEnabled(true);
            state.setText(t("Keystore yolu ve alias yüklendi; şifreleri girin."));
        }
    }

    private void chooseKeystore() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(t("Upload keystore seç"));
        String current = storeFile.getText().trim();
        if (!current.isEmpty()) {
            Path p = Path.of(current).toAbsolutePath();
            if (p.getParent() != null && Files.isDirectory(p.getParent())) fc.setCurrentDirectory(p.getParent().toFile());
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) storeFile.setText(fc.getSelectedFile().toPath().toAbsolutePath().toString());
    }

    private void createKeystore() {
        try {
            AppPaths.ensureBaseDirs();
            Path defaultDir = AppPaths.config().resolve("keystores");
            Files.createDirectories(defaultDir);
            JFileChooser fc = new JFileChooser(defaultDir.toFile());
            fc.setDialogTitle(t("Yeni upload keystore kaydet"));
            fc.setSelectedFile(defaultDir.resolve(safeName(projectRoot.getFileName().toString()) + "-upload-key.jks").toFile());
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path target = fc.getSelectedFile().toPath().toAbsolutePath().normalize();
            if (Files.exists(target)) {
                JOptionPane.showMessageDialog(this, t("Güvenlik için mevcut keystore'un üzerine yazılmıyor. Başka bir dosya adı seçin."), t("Dosya zaten var"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            String a = alias.getText().trim();
            if (a.isEmpty()) { a = "upload"; alias.setText(a); }
            String sp = new String(storePassword.getPassword());
            String kp = new String(keyPassword.getPassword());
            if (sp.length() < 6 || kp.length() < 6) {
                JOptionPane.showMessageDialog(this, t("Yeni keystore için iki şifre de en az 6 karakter olmalıdır."), t("Şifre gerekli"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            String cn = JOptionPane.showInputDialog(this, t("Sertifika adı (CN):"), projectRoot.getFileName().toString() + " Upload Key");
            if (cn == null) return;
            state.setText(t("Keystore oluşturuluyor..."));
            final String aliasValue = a;
            final String commonName = cn;
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override protected Void doInBackground() throws Exception {
                    ToolchainManager.generateUploadKeystore(target, aliasValue, sp, kp, commonName, x -> {});
                    return null;
                }
                @Override protected void done() {
                    try {
                        get();
                        storeFile.setText(target.toString());
                        state.setText(t("Keystore hazır. Güvenli bir yedeğini almayı unutmayın."));
                        JOptionPane.showMessageDialog(ReleaseSigningDialog.this,
                                t("Upload keystore oluşturuldu:\n%s\n\nBu dosyanın güvenli bir yedeğini alın.", target),
                                t("Keystore hazır"), JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        state.setText(t("Keystore oluşturulamadı."));
                        JOptionPane.showMessageDialog(ReleaseSigningDialog.this, cause.getMessage(), t("Keystore hatası"), JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Keystore hatası"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSettings() {
        try {
            Path file = ProjectReleaseManager.ensureSettingsFile(projectRoot);
            if (onOpenSettings != null) {
                dispose();
                onOpenSettings.run();
            } else JOptionPane.showMessageDialog(this, t("Ayar dosyası: %s", file), "signing.properties", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Dosya açılamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void submit() {
        try {
            Path file = storeFile.getText().trim().isEmpty() ? null : Path.of(storeFile.getText().trim());
            ReleaseSigningConfig cfg = new ReleaseSigningConfig(file,
                    new String(storePassword.getPassword()), alias.getText(), new String(keyPassword.getPassword()));
            cfg.validate();
            if (remember.isSelected()) ProjectReleaseManager.save(projectRoot, cfg, rememberPasswords.isSelected());
            dispose();
            if (onBuild != null) onBuild.accept(cfg);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("İmzalama bilgileri eksik"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String safeName(String s) {
        String x = s == null ? "android-app" : s.replaceAll("[^A-Za-z0-9._-]+", "-");
        return x.isBlank() ? "android-app" : x;
    }
}
