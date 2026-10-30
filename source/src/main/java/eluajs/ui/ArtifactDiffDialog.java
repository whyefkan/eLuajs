package eluajs.ui;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ArtifactDiffDialog extends JDialog {
    private final JTextField first = Ui.field();
    private final JTextField second = Ui.field();
    private final JTextArea output = new JTextArea();

    ArtifactDiffDialog(Window owner) {
        super(owner, t("APK / AAB Diff"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        setIconImage(Ui.appIcon());
        setSize(900, 620);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Theme.TITLE_BAR);
        head.setBorder(new EmptyBorder(10, 16, 10, 8));
        head.add(Ui.label(t("APK / AAB Diff"), 14, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        head.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        add(head, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBackground(Theme.BG);
        body.setBorder(new EmptyBorder(14, 18, 16, 18));
        body.add(fields(), BorderLayout.NORTH);
        output.setEditable(false);
        output.setFont(Theme.mono(11));
        output.setBackground(Theme.CONSOLE);
        output.setForeground(Theme.TEXT_SOFT);
        output.setBorder(new EmptyBorder(10, 12, 10, 12));
        body.add(Ui.scroll(output), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private JPanel fields() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 6));
        panel.setOpaque(false);
        panel.add(fileRow(first, t("İlk APK/AAB")));
        panel.add(fileRow(second, t("İkinci APK/AAB")));
        return panel;
    }

    private JPanel fileRow(JTextField field, String label) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel title = Ui.label(label, 10, Font.BOLD, Theme.MUTED);
        title.setPreferredSize(new Dimension(110, 28));
        row.add(title, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        JButton choose = Ui.button(t("Seç"), false);
        choose.addActionListener(e -> choose(field));
        row.add(choose, BorderLayout.EAST);
        return row;
    }

    private void choose(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(t("APK/AAB seç"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("APK / AAB", "apk", "aab"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
            compare();
        }
    }

    private void compare() {
        if (!Files.isRegularFile(Path.of(first.getText().trim())) || !Files.isRegularFile(Path.of(second.getText().trim()))) return;
        try {
            Path a = Path.of(first.getText().trim()), b = Path.of(second.getText().trim());
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
            output.setText(out.toString());
        } catch (Exception ex) {
            output.setText(t("Diff hatası: %s", ex.getMessage()));
        }
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
}
