package eluajs.ui;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Shows AI file changes as a simple line diff before they are applied. */
final class AiDiffDialog extends JDialog {
    record Change(String path, String oldText, String newText, boolean deleted) { }

    private final DefaultListModel<Change> model = new DefaultListModel<>();
    private final JList<Change> files = new JList<>(model);
    private final JTextArea diff = new JTextArea();
    private List<Change> approved = List.of();

    private AiDiffDialog(Window owner, List<Change> changes) {
        super(owner, t("AI changes"), ModalityType.APPLICATION_MODAL);
        setIconImage(Ui.appIcon());
        setSize(1040, 720);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        changes.forEach(model::addElement);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(Theme.BG);
        content.setBorder(new EmptyBorder(18, 20, 16, 20));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.label(t("AI değişiklikleri uygulanmadan önce gösterilir. Ctrl/Shift ile dosya seçin."), 11, Font.PLAIN, Theme.MUTED), BorderLayout.WEST);
        JLabel count = Ui.badge(t("%d dosya", changes.size()), Theme.ACCENT);
        head.add(count, BorderLayout.EAST);
        content.add(head, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setBorder(null);
        split.setDividerSize(5);
        split.setBackground(Theme.BORDER_SOFT);
        split.setResizeWeight(0.28);
        files.setBackground(Theme.PANEL);
        files.setForeground(Theme.TEXT);
        files.setSelectionBackground(Theme.SELECTION);
        files.setSelectionForeground(Theme.TEXT);
        files.setFont(Theme.mono(11));
        files.setFixedCellHeight(29);
        files.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                if (value instanceof Change change) label.setText((change.deleted() ? "− " : "+ ") + change.path());
                label.setBorder(new EmptyBorder(3, 6, 3, 6));
                label.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                return label;
            }
        });
        files.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        files.addListSelectionListener(e -> showSelected());
        JScrollPane filesScroll = Ui.scroll(files);
        filesScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        split.setLeftComponent(filesScroll);

        diff.setEditable(false);
        diff.setFont(Theme.mono(12));
        diff.setBackground(Theme.CONSOLE);
        diff.setForeground(Theme.TEXT_SOFT);
        diff.setCaretColor(Theme.ACCENT);
        diff.setBorder(new EmptyBorder(10, 12, 10, 12));
        JScrollPane diffScroll = Ui.scroll(diff);
        diffScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        split.setRightComponent(diffScroll);
        content.add(split, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton selectAll = Ui.button(t("Tümünü Seç"), false);
        selectAll.addActionListener(e -> {
            if (!model.isEmpty()) files.setSelectionInterval(0, model.size() - 1);
        });
        JButton selectNone = Ui.button(t("Hiçbiri"), false);
        selectNone.addActionListener(e -> files.clearSelection());
        JButton reject = Ui.button(t("Reddet"), false);
        reject.addActionListener(e -> dispose());
        JButton apply = Ui.button(t("Seçilenleri Uygula"), true);
        apply.addActionListener(e -> {
            List<Change> selected = files.getSelectedValuesList();
            if (selected.isEmpty()) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            approved = selected;
            dispose();
        });
        actions.add(selectAll);
        actions.add(selectNone);
        actions.add(reject);
        actions.add(apply);
        content.add(actions, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(apply);
        if (!model.isEmpty()) files.setSelectionInterval(0, model.size() - 1);
    }

    static List<Change> approve(Window owner, List<Change> changes) {
        if (changes == null || changes.isEmpty()) return List.of();
        AiDiffDialog dialog = new AiDiffDialog(owner, changes);
        dialog.setVisible(true);
        return dialog.approved;
    }

    private void showSelected() {
        Change change = files.getSelectedValue();
        if (change == null) {
            diff.setText("");
            return;
        }
        diff.setText(unified(change));
        diff.setCaretPosition(0);
    }

    private String unified(Change change) {
        String oldText = change.oldText() == null ? "" : change.oldText();
        String newText = change.newText() == null ? "" : change.newText();
        StringBuilder out = new StringBuilder();
        out.append("--- ").append(change.path()).append('\n');
        out.append("+++ ").append(change.path()).append('\n');
        String[] a = oldText.split("\\R", -1);
        String[] b = newText.split("\\R", -1);
        if (change.deleted()) {
            for (String line : a) out.append("- ").append(line).append('\n');
            return out.toString();
        }
        if (oldText.isEmpty()) {
            for (String line : b) out.append("+ ").append(line).append('\n');
            return out.toString();
        }
        if ((long) a.length * b.length > 2_250_000L) {
            out.append("@@ large change @@\n");
            int max = Math.max(a.length, b.length);
            for (int i = 0; i < max; i++) {
                String left = i < a.length ? a[i] : "";
                String right = i < b.length ? b[i] : "";
                if (!left.equals(right)) {
                    out.append("- ").append(left).append('\n');
                    out.append("+ ").append(right).append('\n');
                }
            }
            return out.toString();
        }
        int n = a.length;
        int m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                out.append("  ").append(a[i]).append('\n');
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.append("- ").append(a[i++]).append('\n');
            } else {
                out.append("+ ").append(b[j++]).append('\n');
            }
        }
        while (i < n) out.append("- ").append(a[i++]).append('\n');
        while (j < m) out.append("+ ").append(b[j++]).append('\n');
        return out.toString();
    }
}
