package eluajs.ui;

import eluajs.android.ToolchainManager;
import eluajs.git.GitManager;
import eluajs.project.AccessibilityChecker;
import eluajs.project.LocalizationManager;
import eluajs.project.ProjectCheckpoints;
import eluajs.project.ProjectGradleManager;
import eluajs.project.ProjectSearch;
import eluajs.project.ProjectValidator;
import eluajs.util.FileUtil;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Inline panels shown on the right side of the project tools window. */
final class ProjectToolsPanels {
    private ProjectToolsPanels() {}

    static JPanel health(Path root) {
        JPanel panel = page(t("Proje Sağlığı"), t("JDK, Android SDK, Gradle ve proje yapısı kontrolleri."));
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
        JButton refresh = Ui.button(t("Yeniden Kontrol Et"), true);
        Runnable check = () -> {
            rows.removeAll();
            addHealthRow(rows, t("JDK"), ToolchainManager.javaReady());
            addHealthRow(rows, t("Android SDK"), ToolchainManager.sdkReady());
            addHealthRow(rows, t("Gradle"), ToolchainManager.gradleReady());
            addHealthRow(rows, t("Build Tools"), ToolchainManager.buildToolsReady());
            try {
                ProjectValidator.Report report = ProjectValidator.validate(root);
                addHealthRow(rows, t("Proje yapısı"), report.ok());
                status.setText(report.ok() ? t("Temel kontroller geçti.") : t("%d sorun", report.problems().size()));
                status.setForeground(report.ok() ? Theme.ACCENT : Theme.DANGER);
            } catch (Exception ex) {
                addHealthRow(rows, t("Proje yapısı"), false);
                status.setText(ex.getMessage());
                status.setForeground(Theme.DANGER);
            }
            rows.revalidate();
            rows.repaint();
        };
        refresh.addActionListener(e -> check.run());
        JScrollPane scroll = Ui.scroll(rows);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = actions();
        actions.add(status);
        actions.add(refresh);
        panel.add(actions, BorderLayout.SOUTH);
        check.run();
        return panel;
    }

    private static void addHealthRow(JPanel rows, String name, boolean ok) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(7, 8, 7, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        row.add(Ui.label(ok ? "✓  " + name : "✕  " + name, 11, Font.BOLD, ok ? Theme.ACCENT : Theme.DANGER), BorderLayout.WEST);
        row.add(Ui.label(ok ? t("Hazır") : t("Eksik"), 10, Font.PLAIN, Theme.MUTED), BorderLayout.EAST);
        rows.add(row);
        rows.add(Box.createVerticalStrut(2));
    }

    static JPanel checkpoints(Path root, Runnable afterRestore) {
        JPanel panel = page(t("Proje Checkpoint'leri"), t("Projeyi anlık görüntüle, geri yükle veya sil."));
        DefaultListModel<Path> model = new DefaultListModel<>();
        JList<Path> list = new JList<>(model);
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.TEXT);
        list.setFont(Theme.mono(11));
        list.setFixedCellHeight(30);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, false);
                if (value instanceof Path path) label.setText(path.getFileName().toString());
                label.setBorder(new EmptyBorder(3, 6, 3, 6));
                label.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                return label;
            }
        });
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = actions();
        JButton create = Ui.button(t("Checkpoint Oluştur"), true);
        JButton restore = Ui.button(t("Geri Yükle"), false);
        JButton delete = Ui.dangerButton(t("Sil"));
        JButton refresh = Ui.button(t("Yenile"), false);
        create.addActionListener(e -> {
            String label = JOptionPane.showInputDialog(panel, t("Checkpoint adı"), "checkpoint");
            if (label == null) return;
            try { ProjectCheckpoints.create(root, label); refresh(model, list, root); }
            catch (Exception ex) { error(panel, ex); }
        });
        restore.addActionListener(e -> {
            Path selected = list.getSelectedValue();
            if (selected == null) return;
            if (JOptionPane.showConfirmDialog(panel, t("Proje bu checkpoint'e geri yüklensin mi?"),
                    t("Geri Yükle"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            try {
                ProjectCheckpoints.restore(root, selected);
                if (afterRestore != null) afterRestore.run();
                JOptionPane.showMessageDialog(panel, t("Checkpoint geri yüklendi."), t("Geri Yükle"), JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) { error(panel, ex); }
        });
        delete.addActionListener(e -> {
            Path selected = list.getSelectedValue();
            if (selected == null) return;
            try { ProjectCheckpoints.delete(root, selected); refresh(model, list, root); }
            catch (Exception ex) { error(panel, ex); }
        });
        refresh.addActionListener(e -> refresh(model, list, root));
        actions.add(refresh); actions.add(create); actions.add(restore); actions.add(delete);
        panel.add(actions, BorderLayout.SOUTH);
        refresh(model, list, root);
        return panel;
    }

    static JPanel notes(Path root) {
        JPanel panel = page(t("Proje Notları"), t("Bu proje için Markdown notları."));
        Path file = root.resolve(".eluajs/notes.md");
        JTextArea area = new JTextArea();
        area.setFont(Theme.mono(12));
        area.setBackground(Theme.EDITOR);
        area.setForeground(Theme.TEXT);
        area.setCaretColor(Theme.ACCENT);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(12, 14, 12, 14));
        try { if (Files.isRegularFile(file)) area.setText(Files.readString(file, StandardCharsets.UTF_8)); }
        catch (Exception ignored) { }
        JScrollPane scroll = Ui.scroll(area);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = actions();
        JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
        JButton save = Ui.button(t("Kaydet"), true);
        JButton reload = Ui.button(t("Yenile"), false);
        reload.addActionListener(e -> {
            try { area.setText(Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : ""); }
            catch (Exception ex) { status.setText(ex.getMessage()); }
        });
        save.addActionListener(e -> {
            try {
                FileUtil.recoveryCopy(root, file);
                Files.createDirectories(file.getParent());
                FileUtil.writeStringAtomic(file, area.getText());
                FileUtil.secureFile(file);
                status.setForeground(Theme.ACCENT);
                status.setText(t("Notlar kaydedildi."));
            } catch (Exception ex) {
                status.setForeground(Theme.DANGER);
                status.setText(t("Kaydedilemedi: %s", ex.getMessage()));
            }
        });
        actions.add(status); actions.add(reload); actions.add(save);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    static JPanel accessibility(Path root, BiConsumer<Path,Integer> navigator) {
        JPanel panel = page(t("Erişilebilirlik Denetçisi"), t("XML layout erişilebilirlik sorunları."));
        DefaultListModel<AccessibilityChecker.Issue> model = new DefaultListModel<>();
        JList<AccessibilityChecker.Issue> list = new JList<>(model);
        list.setFont(Theme.mono(11));
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, v, i, s, false);
                if (v instanceof AccessibilityChecker.Issue issue) label.setText(issue.toString());
                label.setBorder(new EmptyBorder(3, 6, 3, 6));
                label.setBackground(s ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                return label;
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    AccessibilityChecker.Issue issue = list.getSelectedValue();
                    if (issue != null && navigator != null) navigator.accept(issue.file(), issue.line());
                }
            }
        });
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = actions();
        JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
        JButton scan = Ui.button(t("Yeniden Tara"), true);
        Runnable reload = () -> {
            scan.setEnabled(false);
            status.setForeground(Theme.MUTED);
            status.setText(t("Taranıyor..."));
            new SwingWorker<List<AccessibilityChecker.Issue>, Void>() {
                @Override protected List<AccessibilityChecker.Issue> doInBackground() { return AccessibilityChecker.scan(root); }
                @Override protected void done() {
                    model.clear();
                    try {
                        for (AccessibilityChecker.Issue issue : get()) model.addElement(issue);
                        status.setForeground(Theme.MUTED);
                        status.setText(model.isEmpty() ? t("Sorun bulunamadı.") : t("%d sorun", model.size()));
                    } catch (Exception ex) {
                        status.setForeground(Theme.DANGER);
                        status.setText(ex.getMessage());
                    } finally { scan.setEnabled(true); }
                }
            }.execute();
        };
        scan.addActionListener(e -> reload.run());
        actions.add(status); actions.add(scan);
        panel.add(actions, BorderLayout.SOUTH);
        reload.run();
        return panel;
    }

    static JPanel localization(Path root) {
        JPanel panel = page(t("Çeviri Durumu"), t("Locale dosyalarındaki eksik çeviriler."));
        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        list.setFont(Theme.mono(11));
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, v, i, s, false);
                label.setBorder(new EmptyBorder(3, 6, 3, 6));
                label.setBackground(s ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                return label;
            }
        });
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = actions();
        JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
        JButton scan = Ui.button(t("Yenile"), true);
        Runnable reload = () -> {
            scan.setEnabled(false);
            status.setForeground(Theme.MUTED);
            status.setText(t("Taranıyor..."));
            new SwingWorker<List<LocalizationManager.LocaleReport>, Void>() {
                @Override protected List<LocalizationManager.LocaleReport> doInBackground() throws Exception { return LocalizationManager.scan(root); }
                @Override protected void done() {
                    model.clear();
                    try {
                        List<LocalizationManager.LocaleReport> reports = get();
                        for (LocalizationManager.LocaleReport report : reports) {
                            model.addElement(report.locale() + "  —  " + report.total() + " string"
                                    + (report.missing().isEmpty() ? "" : "  |  eksik: " + String.join(", ", report.missing())));
                        }
                        status.setForeground(Theme.MUTED);
                        status.setText(model.isEmpty() ? t("Locale bulunamadı.") : t("%d locale", model.size()));
                    } catch (Exception ex) {
                        status.setForeground(Theme.DANGER);
                        status.setText(ex.getMessage());
                    } finally { scan.setEnabled(true); }
                }
            }.execute();
        };
        scan.addActionListener(e -> reload.run());
        actions.add(status); actions.add(scan);
        panel.add(actions, BorderLayout.SOUTH);
        reload.run();
        return panel;
    }

    static JPanel references(Path root, BiConsumer<Path,Integer> navigator) {
        JPanel panel = page(t("Referans Haritası"), t("Sınıf, layout ve string kullanımlarını bulun."));
        JTextField query = Ui.field();
        query.putClientProperty("JTextField.placeholderText", t("Aranacak sınıf / layout / string"));
        DefaultListModel<ProjectSearch.Match> model = new DefaultListModel<>();
        JList<ProjectSearch.Match> list = new JList<>(model);
        list.setFont(Theme.mono(11));
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, v, i, s, false);
                if (v instanceof ProjectSearch.Match match) {
                    String rel;
                    try { rel = root.relativize(match.file()).toString(); }
                    catch (Exception ex) { rel = match.file().toString(); }
                    label.setText(rel + ":" + match.line() + "   " + match.preview());
                }
                label.setBorder(new EmptyBorder(3, 6, 3, 6));
                label.setBackground(s ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                return label;
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && navigator != null) {
                    ProjectSearch.Match match = list.getSelectedValue();
                    if (match != null) navigator.accept(match.file(), match.line());
                }
            }
        });
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(0, 0, 10, 0));
        top.add(query, BorderLayout.CENTER);
        JButton search = Ui.button(t("Ara"), true);
        top.add(search, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
        JPanel bottom = actions();
        bottom.add(status);
        panel.add(bottom, BorderLayout.SOUTH);
        Runnable run = () -> {
            String q = query.getText().trim();
            if (q.isBlank()) return;
            search.setEnabled(false);
            status.setForeground(Theme.MUTED);
            status.setText(t("Aranıyor..."));
            new SwingWorker<List<ProjectSearch.Match>, Void>() {
                @Override protected List<ProjectSearch.Match> doInBackground() throws Exception {
                    return ProjectSearch.search(root, q, false, 500);
                }
                @Override protected void done() {
                    model.clear();
                    try {
                        for (ProjectSearch.Match match : get()) model.addElement(match);
                        status.setForeground(Theme.MUTED);
                        status.setText(t("%d sonuç", model.size()));
                    } catch (Exception ex) {
                        status.setForeground(Theme.DANGER);
                        status.setText(ex.getMessage());
                    } finally { search.setEnabled(true); }
                }
            }.execute();
        };
        search.addActionListener(e -> run.run());
        query.addActionListener(e -> run.run());
        return panel;
    }

    static JPanel releaseNotes(Path root) {
        JPanel panel = page(t("Sürüm ve Sürüm Notları"), t("VersionCode artır ve Git geçmişinden not üret."));
        JTextArea notes = new JTextArea();
        notes.setFont(Theme.font(12, Font.PLAIN));
        notes.setBackground(Theme.PANEL);
        notes.setForeground(Theme.TEXT);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane scroll = Ui.scroll(notes);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = actions();
        JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
        JButton reload = Ui.button(t("Yenile"), false);
        JButton copy = Ui.button(t("Kopyala"), false);
        JButton bump = Ui.button(t("Sürüm +1"), true);
        Runnable load = () -> {
            reload.setEnabled(false);
            bump.setEnabled(false);
            status.setForeground(Theme.MUTED);
            status.setText(t("Yükleniyor..."));
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() throws Exception {
                    ProjectGradleManager.Versions version = ProjectGradleManager.read(root);
                    GitManager.Result log = GitManager.log(root, 40);
                    return t("Yeni sürüm: %s (%d)", version.versionName(), version.versionCode()) + "\n\n"
                            + t("Değişiklikler:") + "\n" + (log.ok() ? log.output() : t("Git geçmişi yok."));
                }
                @Override protected void done() {
                    try {
                        notes.setText(get());
                        status.setForeground(Theme.MUTED);
                        status.setText("");
                    } catch (Exception ex) {
                        notes.setText(t("Sürüm bilgisi okunamadı: %s", ex.getMessage()));
                    } finally {
                        reload.setEnabled(true);
                        bump.setEnabled(true);
                    }
                }
            }.execute();
        };
        reload.addActionListener(e -> load.run());
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(notes.getText()), null));
        bump.addActionListener(e -> {
            try {
                ProjectGradleManager.incrementVersionCode(root);
                load.run();
                status.setForeground(Theme.ACCENT);
                status.setText(t("Sürüm kodu artırıldı."));
            } catch (Exception ex) {
                status.setForeground(Theme.DANGER);
                status.setText(ex.getMessage());
            }
        });
        actions.add(status); actions.add(reload); actions.add(copy); actions.add(bump);
        panel.add(actions, BorderLayout.SOUTH);
        load.run();
        return panel;
    }

    static JPanel artifactDiff(Path root) {
        JPanel panel = page(t("APK / AAB Diff"), t("İki çıktı arasındaki dosya ve boyut farkları."));
        JTextField first = Ui.field();
        JTextField second = Ui.field();
        JTextArea output = new JTextArea();
        output.setEditable(false);
        output.setFont(Theme.mono(11));
        output.setBackground(Theme.CONSOLE);
        output.setForeground(Theme.TEXT_SOFT);
        output.setBorder(new EmptyBorder(10, 12, 10, 12));
        JPanel fields = new JPanel(new GridLayout(2, 1, 0, 6));
        fields.setOpaque(false);
        fields.setBorder(new EmptyBorder(0, 0, 10, 0));
        fields.add(fileRow(panel, first, t("İlk APK/AAB"), root));
        fields.add(fileRow(panel, second, t("İkinci APK/AAB"), root));
        panel.add(fields, BorderLayout.NORTH);
        JScrollPane scroll = Ui.scroll(output);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = actions();
        JButton compare = Ui.button(t("Karşılaştır"), true);
        compare.addActionListener(e -> compare(panel, first, second, output));
        actions.add(compare);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel fileRow(JPanel parent, JTextField field, String label, Path root) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel title = Ui.label(label, 10, Font.BOLD, Theme.MUTED);
        title.setPreferredSize(new Dimension(110, 28));
        row.add(title, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        JButton choose = Ui.button(t("Seç"), false);
        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(t("APK/AAB seç"));
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("APK / AAB", "apk", "aab"));
            if (root != null) chooser.setCurrentDirectory(root.toFile());
            if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row.add(choose, BorderLayout.EAST);
        return row;
    }

    private static void compare(JPanel owner, JTextField first, JTextField second, JTextArea output) {
        Path a = first.getText().isBlank() ? null : Path.of(first.getText().trim());
        Path b = second.getText().isBlank() ? null : Path.of(second.getText().trim());
        if (a == null || b == null || !Files.isRegularFile(a) || !Files.isRegularFile(b)) {
            output.setText(t("İki APK/AAB dosyası seçin."));
            return;
        }
        output.setText(t("Karşılaştırılıyor..."));
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                Map<String, Long> ea = entries(a), eb = entries(b);
                StringBuilder out = new StringBuilder();
                out.append("A: ").append(a.getFileName()).append("  (").append(Files.size(a)).append(" bytes)\n");
                out.append("B: ").append(b.getFileName()).append("  (").append(Files.size(b)).append(" bytes)\n");
                out.append(t("Boyut farkı: ")).append(Files.size(b) - Files.size(a)).append(" bytes\n\n");
                Set<String> all = new TreeSet<>(ea.keySet()); all.addAll(eb.keySet());
                for (String name : all) {
                    Long av = ea.get(name), bv = eb.get(name);
                    if (av == null) out.append("+ ").append(name).append("  ").append(bv).append(" bytes\n");
                    else if (bv == null) out.append("- ").append(name).append("  ").append(av).append(" bytes\n");
                    else if (!av.equals(bv)) out.append("~ ").append(name).append("  ").append(av).append(" → ").append(bv).append(" bytes\n");
                }
                return out.toString();
            }
            @Override protected void done() {
                try {
                    output.setText(get());
                    output.setCaretPosition(0);
                } catch (Exception ex) {
                    output.setText(t("Diff hatası: %s", ex.getMessage()));
                }
            }
        }.execute();
    }

    private static Map<String, Long> entries(Path file) throws IOException {
        Map<String, Long> out = new TreeMap<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) out.put(entry.getName(), entry.getSize());
            }
        }
        return out;
    }



    static JPanel focus(java.util.function.BooleanSupplier active, Runnable toggle) {
        JPanel panel = page(t("Focus Mode"), t("Kenar çubuklarını gizleyip yalnızca koda odaklanın."));
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(22, 0, 0, 0));
        JLabel status = Ui.label("", 11, Font.PLAIN, Theme.MUTED);
        JButton button = Ui.button("", true);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        Runnable refresh = () -> {
            boolean on = active.getAsBoolean();
            button.setText(on ? t("Odak Modundan Çık") : t("Odak Modunu Aç"));
            status.setText(on ? t("Odak modu açık.") : t("Odak modu kapalı."));
            status.setForeground(on ? Theme.ACCENT : Theme.MUTED);
        };
        button.addActionListener(e -> { if (toggle != null) toggle.run(); refresh.run(); });
        center.add(button);
        center.add(Box.createVerticalStrut(12));
        center.add(status);
        center.add(Box.createVerticalGlue());
        panel.add(center, BorderLayout.CENTER);
        refresh.run();
        return panel;
    }

    static JPanel action(String title, String description, String buttonLabel, Runnable action) {
        JPanel panel = page(title, description);
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(22, 0, 0, 0));
        JButton button = Ui.button(buttonLabel, true);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> { if (action != null) action.run(); });
        center.add(button);
        center.add(Box.createVerticalGlue());
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel page(String title, String description) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(Theme.BG);
        panel.setBorder(new EmptyBorder(20, 24, 16, 24));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel heading = Ui.label(title, 21, Font.BOLD, Theme.TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = Ui.label(description, 11, Font.PLAIN, Theme.MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(heading);
        text.add(Box.createVerticalStrut(5));
        text.add(sub);
        panel.add(text, BorderLayout.NORTH);
        return panel;
    }

    private static JPanel actions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(10, 0, 0, 0));
        return actions;
    }

    private static void refresh(DefaultListModel<Path> model, JList<Path> list, Path root) {
        model.clear();
        ProjectCheckpoints.list(root).forEach(model::addElement);
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private static void error(Component owner, Exception ex) {
        JOptionPane.showMessageDialog(owner, ex.getMessage(), t("Hata"), JOptionPane.ERROR_MESSAGE);
    }
}
