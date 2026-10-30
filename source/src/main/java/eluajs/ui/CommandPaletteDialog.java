package eluajs.ui;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Keyboard-first command list (Ctrl+Shift+P). */
final class CommandPaletteDialog extends JDialog {
    record Command(String title, String hint, Runnable action) {
        @Override public String toString() { return title; }
    }

    private final JTextField query = Ui.field();
    private final DefaultListModel<Command> model = new DefaultListModel<>();
    private final JList<Command> list = new JList<>(model);
    private final List<Command> all;
    private static final java.util.List<String> RECENT = new java.util.ArrayList<>();

    CommandPaletteDialog(Frame owner, List<Command> commands) {
        super(owner, t("Komut Paleti"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        this.all = new ArrayList<>(commands);
        setIconImage(Ui.appIcon());
        setSize(680, 480);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBackground(Theme.BG);
        body.setBorder(new EmptyBorder(14, 18, 16, 18));
        query.setToolTipText(t("Komut ara"));
        body.add(query, BorderLayout.NORTH);
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.TEXT);
        list.setFont(Theme.font(12, Font.PLAIN));
        list.setFixedCellHeight(34);
        list.setBorder(new EmptyBorder(4, 6, 4, 6));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                if (value instanceof Command command) {
                    label.setText(command.title() + (command.hint().isBlank() ? "" : "   —   " + command.hint()));
                }
                label.setBorder(new EmptyBorder(3, 6, 3, 6));
                label.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                return label;
            }
        });
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
        filter();
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
        brand.add(Ui.label(t("Komut Paleti"), 14, Font.BOLD, Theme.TEXT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private void filter() {
        String q = query.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        List<Command> matches = new ArrayList<>();
        for (Command command : all) {
            String title = command.title().toLowerCase(Locale.ROOT);
            String hint = command.hint().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || fuzzy(title, q) || fuzzy(hint, q)) matches.add(command);
        }
        matches.sort((a, b) -> {
            int ai = RECENT.indexOf(a.title());
            int bi = RECENT.indexOf(b.title());
            if (ai >= 0 || bi >= 0) return ai < 0 ? 1 : bi < 0 ? -1 : Integer.compare(ai, bi);
            return a.title().compareToIgnoreCase(b.title());
        });
        for (Command command : matches) model.addElement(command);
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private static boolean fuzzy(String text, String query) {
        int at = 0;
        for (int i = 0; i < query.length(); i++) {
            at = text.indexOf(query.charAt(i), at);
            if (at < 0) return false;
            at++;
        }
        return true;
    }

    private void choose() {
        Command command = list.getSelectedValue();
        if (command == null) return;
        RECENT.remove(command.title());
        RECENT.add(0, command.title());
        while (RECENT.size() > 8) RECENT.remove(RECENT.size() - 1);
        dispose();
        if (command.action() != null) command.action().run();
    }
}
