package eluajs.ui;

import eluajs.project.ProjectSearch;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.function.BiConsumer;

public final class ProjectSearchDialog extends JDialog {
    private final Path projectRoot;
    private final BiConsumer<Path,Integer> navigator;
    private final JTextField query = Ui.field();
    private final JCheckBox caseSensitive = new JCheckBox(t("Büyük/küçük harf duyarlı"));
    private final DefaultListModel<ProjectSearch.Match> model = new DefaultListModel<>();
    private final JList<ProjectSearch.Match> results = new JList<>(model);
    private final JLabel status = Ui.label(t("Aranacak metni yazın."), 12, Font.PLAIN, Theme.MUTED);
    private final JButton search = Ui.button("⌕  " + t("Ara"), true);

    public ProjectSearchDialog(Frame owner, Path projectRoot, BiConsumer<Path,Integer> navigator) {
        super(owner, t("Proje İçinde Ara"), false);
        this.projectRoot = projectRoot;
        this.navigator = navigator;
        setIconImage(Ui.appIcon());
        setSize(780, 560);
        setMinimumSize(new Dimension(640, 460));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(content(), BorderLayout.CENTER);
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(22, 24, 20, 24));

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.setOpaque(false);
        JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        title.add(Ui.label(t("Proje içinde ara"), 22, Font.BOLD, Theme.TEXT));
        title.add(Box.createVerticalStrut(4));
        title.add(Ui.label(t("Java, XML, Gradle ve diğer metin dosyalarında arar. Ctrl+Shift+F"), 12, Font.PLAIN, Theme.MUTED));
        top.add(title, BorderLayout.NORTH);

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        query.setToolTipText("Aranacak metin");
        query.addActionListener(e -> runSearch());
        search.addActionListener(e -> runSearch());
        searchRow.add(query, BorderLayout.CENTER);
        searchRow.add(search, BorderLayout.EAST);
        top.add(searchRow, BorderLayout.CENTER);
        caseSensitive.setOpaque(false);
        caseSensitive.setForeground(Theme.MUTED);
        caseSensitive.setFont(Theme.font(12, Font.PLAIN));
        caseSensitive.setFocusPainted(false);
        top.add(caseSensitive, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        results.setBackground(Theme.PANEL);
        results.setForeground(Theme.TEXT);
        results.setSelectionBackground(Theme.SELECTION);
        results.setSelectionForeground(Theme.TEXT);
        results.setFont(Theme.mono(12));
        results.setFixedCellHeight(29);
        results.setBorder(new EmptyBorder(5, 6, 5, 6));
        results.setCellRenderer(new Renderer(projectRoot));
        results.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) openSelected(); }
        });
        results.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); openSelected(); } }
        });
        JScrollPane scroll = Ui.scroll(results);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        root.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(status, BorderLayout.WEST);
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        bottom.add(close, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(query::requestFocusInWindow);
        return root;
    }

    public void focusQuery() {
        setVisible(true);
        query.requestFocusInWindow();
        query.selectAll();
    }

    private void runSearch() {
        String q = query.getText().trim();
        if (q.isEmpty()) { Toolkit.getDefaultToolkit().beep(); return; }
        search.setEnabled(false);
        model.clear();
        status.setText(t("Aranıyor..."));
        SwingWorker<java.util.List<ProjectSearch.Match>,Void> worker = new SwingWorker<>() {
            @Override protected java.util.List<ProjectSearch.Match> doInBackground() throws Exception {
                return ProjectSearch.search(projectRoot, q, caseSensitive.isSelected(), 500);
            }
            @Override protected void done() {
                search.setEnabled(true);
                try {
                    var found = get();
                    found.forEach(model::addElement);
                    status.setText(t("%d sonuç", found.size()) + (found.size() >= 500 ? t(" (ilk 500)") : "") + t(". Çift tıklayarak açın."));
                    if (!found.isEmpty()) results.setSelectedIndex(0);
                } catch (Exception ex) {
                    status.setText(t("Arama başarısız."));
                    JOptionPane.showMessageDialog(ProjectSearchDialog.this, ex.getMessage(), t("Arama hatası"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void openSelected() {
        ProjectSearch.Match m = results.getSelectedValue();
        if (m != null && navigator != null) navigator.accept(m.file(), m.line());
    }

    private static final class Renderer extends DefaultListCellRenderer {
        private final Path root;
        Renderer(Path root) { this.root = root; }
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
            if (value instanceof ProjectSearch.Match m) {
                String rel;
                try { rel = root.relativize(m.file()).toString(); }
                catch (Exception e) { rel = m.file().toString(); }
                l.setText(rel + ":" + m.line() + "   " + m.preview());
                l.setToolTipText(m.file() + ":" + m.line());
            }
            l.setBorder(new EmptyBorder(3, 6, 3, 6));
            l.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
            l.setForeground(Theme.TEXT);
            l.setFont(Theme.mono(12));
            return l;
        }
    }
}
