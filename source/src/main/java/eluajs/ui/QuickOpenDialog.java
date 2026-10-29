package eluajs.ui;

import eluajs.util.FileUtil;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Fast project file switcher (Ctrl+P), dependency-free. */
final class QuickOpenDialog extends JDialog {
    private static final Set<String> SKIP = Set.of(
            ".git", ".gradle", ".idea", ".eluajs", "build", "release", "out", "dist", "node_modules");
    private final Path root;
    private final Consumer<Path> opener;
    private final JTextField query = Ui.field();
    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);
    private List<Path> all = List.of();

    QuickOpenDialog(Frame owner, Path root, Consumer<Path> opener) {
        super(owner, t("Hızlı Dosya Aç"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        this.root = root;
        this.opener = opener;
        setIconImage(Ui.appIcon());
        setSize(720, 500);
        setMinimumSize(new Dimension(600, 400));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBackground(Theme.BG);
        body.setBorder(new EmptyBorder(14, 18, 16, 18));
        query.setToolTipText(t("Dosya adı yazın"));
        body.add(query, BorderLayout.NORTH);
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.TEXT);
        list.setFont(Theme.mono(12));
        list.setFixedCellHeight(28);
        list.setBorder(new EmptyBorder(4, 6, 4, 6));
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) choose();
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); choose(); }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) dispose();
            }
        });
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        body.add(scroll, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        query.addActionListener(e -> choose());
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { }
        });
        SwingWorker<List<Path>, Void> worker = new SwingWorker<>() {
            @Override protected List<Path> doInBackground() { return index(root); }
            @Override protected void done() {
                try { all = get(); filter(); if (!model.isEmpty()) list.setSelectedIndex(0); }
                catch (Exception ignored) { }
            }
        };
        worker.execute();
        SwingUtilities.invokeLater(query::requestFocusInWindow);
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 50));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(0, 16, 0, 8)));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(22, 22, Image.SCALE_SMOOTH))));
        brand.add(Ui.label(t("Hızlı Dosya Aç"), 14, Font.BOLD, Theme.TEXT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private void filter() {
        String q = query.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        int count = 0;
        for (Path path : all) {
            String rel = root.relativize(path).toString().replace('\\', '/');
            if (q.isEmpty() || rel.toLowerCase(Locale.ROOT).contains(q)) {
                model.addElement(path);
                if (++count >= 300) break;
            }
        }
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private void choose() {
        Path path = list.getSelectedValue();
        if (path == null) return;
        dispose();
        if (opener != null) opener.accept(path);
    }

    private static List<Path> index(Path root) {
        List<Path> paths = new ArrayList<>();
        try (var walk = Files.walk(root, 14)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !Files.isSymbolicLink(p))
                    .filter(p -> {
                        for (Path part : root.relativize(p)) if (SKIP.contains(part.toString())) return false;
                        return FileUtil.isTextFile(p);
                    })
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString().toLowerCase(Locale.ROOT)))
                    .limit(10_000)
                    .forEach(paths::add);
        } catch (Exception ignored) { }
        return paths;
    }

    private final class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
            if (value instanceof Path path) {
                label.setText(root.relativize(path).toString().replace('\\', '/'));
                label.setToolTipText(path.toString());
            }
            label.setBorder(new EmptyBorder(3, 6, 3, 6));
            label.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
            label.setForeground(Theme.TEXT);
            label.setFont(Theme.mono(12));
            return label;
        }
    }
}
