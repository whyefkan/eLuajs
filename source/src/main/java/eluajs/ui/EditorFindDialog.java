package eluajs.ui;

import eluajs.util.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eluajs.util.I18n.t;

/** Lightweight per-editor find/replace bar. */
final class EditorFindDialog extends JDialog {
    private final Supplier<SyntaxEditorPane> editorProvider;
    private final JTextField query = Ui.field();
    private final JTextField replacement = Ui.field();
    private final JCheckBox caseSensitive = new JCheckBox(t("Büyük/küçük harf duyarlı"));
    private final JLabel status = Ui.label("", 11, Font.PLAIN, Theme.MUTED);

    EditorFindDialog(Window owner, Supplier<SyntaxEditorPane> editorProvider) {
        super(owner, t("Bul / Değiştir"), ModalityType.MODELESS);
        this.editorProvider = editorProvider;
        setIconImage(Ui.appIcon());
        setSize(590, 270);
        setMinimumSize(new Dimension(500, 240));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JPanel findRow = new JPanel(new BorderLayout(8, 0));
        findRow.setOpaque(false);
        query.setToolTipText(t("Aranacak metin"));
        findRow.add(query, BorderLayout.CENTER);
        JPanel findActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        findActions.setOpaque(false);
        JButton previous = Ui.button(t("Önceki"), false);
        previous.addActionListener(e -> find(false));
        JButton next = Ui.button(t("Sonraki"), true);
        next.addActionListener(e -> find(true));
        findActions.add(previous);
        findActions.add(next);
        findRow.add(findActions, BorderLayout.EAST);

        JPanel replaceRow = new JPanel(new BorderLayout(8, 0));
        replaceRow.setOpaque(false);
        replacement.setToolTipText(t("Yeni metin"));
        replaceRow.add(replacement, BorderLayout.CENTER);
        JPanel replaceActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        replaceActions.setOpaque(false);
        JButton replace = Ui.button(t("Değiştir"), false);
        replace.addActionListener(e -> replaceOne());
        JButton replaceAll = Ui.button(t("Tümünü Değiştir"), false);
        replaceAll.addActionListener(e -> replaceAll());
        replaceActions.add(replace);
        replaceActions.add(replaceAll);
        replaceRow.add(replaceActions, BorderLayout.EAST);

        caseSensitive.setOpaque(false);
        caseSensitive.setForeground(Theme.MUTED);
        caseSensitive.setFont(Theme.font(11, Font.PLAIN));
        caseSensitive.setFocusPainted(false);
        JPanel options = new JPanel(new BorderLayout());
        options.setOpaque(false);
        options.add(caseSensitive, BorderLayout.WEST);
        options.add(status, BorderLayout.EAST);

        query.addActionListener(e -> find(true));
        replacement.addActionListener(e -> replaceOne());
        form.add(findRow);
        form.add(Box.createVerticalStrut(8));
        form.add(replaceRow);
        form.add(Box.createVerticalStrut(8));
        form.add(options);
        root.add(form, BorderLayout.NORTH);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        footer.add(close);
        root.add(footer, BorderLayout.SOUTH);
        add(root, BorderLayout.CENTER);
        getRootPane().setDefaultButton(next);
    }

    void open(boolean replaceMode) {
        SyntaxEditorPane editor = editorProvider.get();
        if (editor != null) {
            String selected = editor.getSelectedText();
            if (selected != null && !selected.isBlank() && !selected.contains("\n")) query.setText(selected);
        }
        setVisible(true);
        if (replaceMode) replacement.requestFocusInWindow();
        else {
            query.requestFocusInWindow();
            query.selectAll();
        }
    }

    private SyntaxEditorPane editor() {
        return editorProvider.get();
    }

    private void find(boolean forward) {
        SyntaxEditorPane editor = editor();
        if (editor == null) return;
        String needle = query.getText();
        if (needle.isEmpty()) {
            status.setText(t("Aranacak metin boş olamaz."));
            return;
        }
        String text = editor.getText();
        boolean sensitive = caseSensitive.isSelected();
        String source = sensitive ? text : text.toLowerCase(Locale.ROOT);
        String target = sensitive ? needle : needle.toLowerCase(Locale.ROOT);
        int start = forward ? editor.getSelectionEnd() : editor.getSelectionStart();
        if (start < 0 || start > text.length()) start = 0;
        int index;
        if (forward) {
            index = source.indexOf(target, Math.min(start, source.length()));
            if (index < 0 && start > 0) index = source.indexOf(target);
        } else {
            index = source.lastIndexOf(target, Math.max(0, start - 1));
            if (index < 0) index = source.lastIndexOf(target);
        }
        if (index < 0) {
            status.setText(t("Eşleşme bulunamadı."));
            return;
        }
        editor.requestFocusInWindow();
        editor.select(index, index + needle.length());
        try {
            Rectangle r = editor.modelToView2D(index).getBounds();
            editor.scrollRectToVisible(r);
        } catch (Exception ignored) { }
        status.setText(t("Eşleşme bulundu."));
    }

    private void replaceOne() {
        SyntaxEditorPane editor = editor();
        if (editor == null) return;
        String needle = query.getText();
        String selected = editor.getSelectedText();
        boolean matches = selected != null && (caseSensitive.isSelected()
                ? selected.equals(needle)
                : selected.equalsIgnoreCase(needle));
        if (!matches) find(true);
        else {
            editor.replaceSelection(replacement.getText());
            find(true);
        }
    }

    private void replaceAll() {
        SyntaxEditorPane editor = editor();
        if (editor == null) return;
        String needle = query.getText();
        if (needle.isEmpty()) {
            status.setText(t("Aranacak metin boş olamaz."));
            return;
        }
        String text = editor.getText();
        int flags = caseSensitive.isSelected() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Matcher matcher = Pattern.compile(Pattern.quote(needle), flags).matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        if (count == 0) {
            status.setText(t("Eşleşme bulunamadı."));
            return;
        }
        String next = matcher.reset().replaceAll(Matcher.quoteReplacement(replacement.getText()));
        editor.setText(next);
        status.setText(t("%d değişiklik yapıldı.", count));
    }
}
