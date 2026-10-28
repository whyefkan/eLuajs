package eluajs.ui;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight syntax-colored editor with dependency-free Java completion. */
public final class SyntaxEditorPane extends JTextPane {
    private final SimpleAttributeSet normal = attrs(token(new Color(226, 232, 240), new Color(30, 41, 59)), Font.PLAIN);
    private final SimpleAttributeSet keyword = attrs(token(new Color(198, 120, 221), new Color(126, 34, 206)), Font.BOLD);
    private final SimpleAttributeSet type = attrs(token(new Color(97, 175, 239), new Color(29, 78, 216)), Font.PLAIN);
    private final SimpleAttributeSet method = attrs(token(new Color(86, 182, 194), new Color(15, 118, 110)), Font.PLAIN);
    private final SimpleAttributeSet string = attrs(token(new Color(152, 195, 121), new Color(22, 101, 52)), Font.PLAIN);
    private final SimpleAttributeSet number = attrs(token(new Color(209, 154, 102), new Color(180, 83, 9)), Font.PLAIN);
    private final SimpleAttributeSet comment = attrs(token(new Color(106, 153, 85), new Color(71, 85, 105)), Font.ITALIC);
    private final SimpleAttributeSet annotation = attrs(token(new Color(229, 192, 123), new Color(133, 77, 14)), Font.PLAIN);
    private final SimpleAttributeSet xmlTag = attrs(token(new Color(224, 108, 117), new Color(185, 28, 28)), Font.BOLD);
    private final SimpleAttributeSet xmlAttr = attrs(token(new Color(209, 154, 102), new Color(180, 83, 9)), Font.PLAIN);
    private final SimpleAttributeSet propertyKey = attrs(token(new Color(97, 175, 239), new Color(29, 78, 216)), Font.PLAIN);

    private String mode = "text";
    private boolean loadingDocument;
    private int fontSize = 14;
    private final List<int[]> folds = new ArrayList<>();
    private final List<Object> bracketTags = new ArrayList<>();
    private EditorFindDialog findDialog;
    private final UndoManager undoManager = new UndoManager();
    private final Timer highlightTimer;
    private static final int HIGHLIGHT_DEBOUNCE_MS = 120;
    private static final int MAX_HIGHLIGHT_CHARS = 1_500_000;

    private static final Pattern JAVA_KEYWORDS = Pattern.compile("\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null|record|sealed|permits|non-sealed|var|yield)\\b");
    private static final Pattern JAVA_TYPES = Pattern.compile("\\b([A-Z][A-Za-z0-9_$]*)\\b");
    private static final Pattern JAVA_METHODS = Pattern.compile("\\b([a-zA-Z_$][A-Za-z0-9_$]*)(?=\\s*\\()");
    private static final Pattern ANNOTATIONS = Pattern.compile("@[A-Za-z_$][A-Za-z0-9_$.]*");
    private static final Pattern NUMBERS = Pattern.compile("(?<![A-Za-z0-9_$])(?:0[xX][0-9A-Fa-f_]+|0[bB][01_]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?)(?![A-Za-z0-9_$])");
    // Possessive quantifiers keep this from recursing (StackOverflowError) on long
    // string/char literals in big files. The body can never consume the closing quote,
    // so possessive matching is still correct here.
    private static final Pattern STRINGS = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*+\"|'(?:\\\\.|[^'\\\\])*+'");
    // Do NOT use a global (?s) flag here: with DOTALL the // alternative would
    // swallow the rest of the file after the first line comment (the "all green"
    // editor bug). Block comments get their own DOTALL equivalent instead.
    // Single left-to-right pass for C-like languages. Matching literals before
    // comments in one combined pattern keeps "http://..." inside a string green
    // and keeps quotes inside a comment from being colored as a string.
    private static final Pattern LITERALS_AND_COMMENTS = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*+\"|'(?:\\\\.|[^'\\\\])*+'|/\\*[\\s\\S]*?\\*/|//[^\\n]*");

    private static final Pattern XML_TAGS = Pattern.compile("</?[A-Za-z_:][A-Za-z0-9_.:-]*|/?>");
    private static final Pattern XML_ATTRS = Pattern.compile("\\b[A-Za-z_:][A-Za-z0-9_.:-]*(?=\\s*=)");
    private static final Pattern XML_COMMENTS = Pattern.compile("(?s)<!--.*?-->");

    private static final Pattern GRADLE_KEYWORDS = Pattern.compile("\\b(plugins|android|namespace|compileSdk|minSdk|targetSdk|versionCode|versionName|dependencies|implementation|api|compileOnly|runtimeOnly|annotationProcessor|testImplementation|androidTestImplementation|buildTypes|release|debug|repositories|google|mavenCentral|id|platform)\\b");
    private static final Pattern PROPERTY_KEYS = Pattern.compile("(?m)^[ \\t]*([^#!\\s][^=:\\n]*?)(?=\\s*[=:])");
    private static final Pattern HASH_COMMENTS = Pattern.compile("(?m)^[ \\t]*[#!].*$");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_$][A-Za-z0-9_$]{2,}\\b");
    private static final Pattern JSON_KEYS = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)");
    private static final Pattern JSON_BOOLS = Pattern.compile("\\b(true|false|null)\\b");

    private static final List<String> COMPLETIONS = List.of(
            "Activity", "Bundle", "Intent", "Context", "View", "Button", "TextView", "EditText", "ImageView",
            "LinearLayout", "FrameLayout", "RelativeLayout", "RecyclerView", "WebView", "Toast", "AlertDialog",
            "Color", "Gravity", "Uri", "Bitmap", "BitmapFactory", "SharedPreferences", "Handler", "Looper",
            "File", "IOException", "ArrayList", "HashMap", "List", "Map", "Set", "JSONObject", "JSONArray",
            "String", "Integer", "Boolean", "Override", "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy",
            "setContentView", "findViewById", "startActivity", "startActivityForResult", "finish", "getIntent", "getApplicationContext",
            "setOnClickListener", "setText", "getText", "setVisibility", "setEnabled", "setBackgroundColor",
            "getString", "getResources", "getAssets", "getSharedPreferences", "runOnUiThread",
            "public", "private", "protected", "static", "final", "class", "interface", "extends", "implements",
            "import", "package", "new", "return", "if", "else", "for", "while", "switch", "try", "catch", "throw",
            "true", "false", "null", "void", "int", "long", "double", "float", "boolean", "StringBuilder"
    );

    /** Common platform imports. Completion stays dependency-free; third-party libraries remain normal Gradle imports. */
    private static final Map<String,String> COMMON_IMPORTS = Map.ofEntries(
            Map.entry("Activity", "android.app.Activity"),
            Map.entry("Bundle", "android.os.Bundle"),
            Map.entry("Intent", "android.content.Intent"),
            Map.entry("Context", "android.content.Context"),
            Map.entry("View", "android.view.View"),
            Map.entry("Button", "android.widget.Button"),
            Map.entry("TextView", "android.widget.TextView"),
            Map.entry("EditText", "android.widget.EditText"),
            Map.entry("ImageView", "android.widget.ImageView"),
            Map.entry("LinearLayout", "android.widget.LinearLayout"),
            Map.entry("FrameLayout", "android.widget.FrameLayout"),
            Map.entry("RelativeLayout", "android.widget.RelativeLayout"),
            Map.entry("WebView", "android.webkit.WebView"),
            Map.entry("Toast", "android.widget.Toast"),
            Map.entry("AlertDialog", "android.app.AlertDialog"),
            Map.entry("Color", "android.graphics.Color"),
            Map.entry("Gravity", "android.view.Gravity"),
            Map.entry("Uri", "android.net.Uri"),
            Map.entry("Bitmap", "android.graphics.Bitmap"),
            Map.entry("BitmapFactory", "android.graphics.BitmapFactory"),
            Map.entry("SharedPreferences", "android.content.SharedPreferences"),
            Map.entry("Handler", "android.os.Handler"),
            Map.entry("Looper", "android.os.Looper"),
            Map.entry("File", "java.io.File"),
            Map.entry("IOException", "java.io.IOException"),
            Map.entry("ArrayList", "java.util.ArrayList"),
            Map.entry("HashMap", "java.util.HashMap"),
            Map.entry("List", "java.util.List"),
            Map.entry("Map", "java.util.Map"),
            Map.entry("Set", "java.util.Set"),
            Map.entry("JSONObject", "org.json.JSONObject"),
            Map.entry("JSONArray", "org.json.JSONArray")
    );

    public SyntaxEditorPane() {
        setBackground(Theme.EDITOR);
        setForeground(Theme.TEXT);
        setCaretColor(Theme.ACCENT);
        setSelectionColor(Theme.SELECTION);
        setSelectedTextColor(Theme.TEXT);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        setFont(Theme.mono(fontSize));
        setToolTipText(t("Java dosyalarında Ctrl+Space ile kod tamamlama ve temel otomatik import"));
        highlightTimer = new Timer(HIGHLIGHT_DEBOUNCE_MS, e -> highlightNow());
        highlightTimer.setRepeats(false);
        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                if (!loadingDocument) { folds.clear(); queueHighlight(); }
            }
            @Override public void removeUpdate(DocumentEvent e) {
                if (!loadingDocument) { folds.clear(); queueHighlight(); }
            }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        addCaretListener(e -> highlightMatchingBracket());
        getDocument().addUndoableEditListener(e -> {
            UndoableEdit edit = e.getEdit();
            if (edit instanceof AbstractDocument.DefaultDocumentEvent event
                    && event.getType() != DocumentEvent.EventType.CHANGE) {
                undoManager.addEdit(edit);
            }
        });
        installCompletionShortcut();
        installEditingShortcuts();
        setComponentPopupMenu(editorMenu());
    }

    public void setFile(Path file, String text) {
        highlightTimer.stop();
        loadingDocument = true;
        try {
            mode = modeFor(file);
            setText(text);
            setCaretPosition(0);
        } finally {
            loadingDocument = false;
            undoManager.discardAllEdits();
        }
        highlightNow();
    }

    public void refreshHighlighting() { highlightNow(); }

    public void setZoom(int size) {
        fontSize = Math.max(9, Math.min(30, size));
        setFont(Theme.mono(fontSize));
        updateAttributeFontSizes();
        highlightNow();
    }

    public int zoom() { return fontSize; }

    @Override public boolean getScrollableTracksViewportWidth() {
        Component parent = getParent();
        if (parent == null) return true;
        return getUI().getPreferredSize(this).width <= parent.getSize().width;
    }

    private void installCompletionShortcut() {
        InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), "eluajs-complete");
        am.put("eluajs-complete", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { showCompletion(); }
        });
    }

    private void installEditingShortcuts() {
        InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "eluajs-newline");
        am.put("eluajs-newline", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { insertNewlineWithIndent(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "eluajs-undo");
        am.put("eluajs-undo", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (undoManager.canUndo()) undoManager.undo();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "eluajs-redo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "eluajs-redo");
        am.put("eluajs-redo", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (undoManager.canRedo()) undoManager.redo();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "eluajs-copy");
        am.put("eluajs-copy", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { copySelectionOrLine(false); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), "eluajs-cut");
        am.put("eluajs-cut", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { copySelectionOrLine(true); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "eluajs-paste");
        am.put("eluajs-paste", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { pasteFromClipboard(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), "eluajs-select-all");
        am.put("eluajs-select-all", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { selectAll(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "eluajs-find");
        am.put("eluajs-find", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { openFindDialog(false); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), "eluajs-replace");
        am.put("eluajs-replace", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { openFindDialog(true); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "eluajs-zoom-in");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK), "eluajs-zoom-in");
        am.put("eluajs-zoom-in", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { setZoom(fontSize + 1); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "eluajs-zoom-out");
        am.put("eluajs-zoom-out", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { setZoom(fontSize - 1); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), "eluajs-zoom-reset");
        am.put("eluajs-zoom-reset", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { setZoom(14); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "eluajs-fold");
        am.put("eluajs-fold", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { toggleFoldAtCaret(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "eluajs-unfold");
        am.put("eluajs-unfold", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { unfoldAll(); }
        });
    }

    private JPopupMenu editorMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem cut = new JMenuItem(t("Kes"));
        cut.addActionListener(e -> copySelectionOrLine(true));
        JMenuItem copy = new JMenuItem(t("Kopyala"));
        copy.addActionListener(e -> copySelectionOrLine(false));
        JMenuItem paste = new JMenuItem(t("Yapıştır"));
        paste.addActionListener(e -> pasteFromClipboard());
        JMenuItem selectAll = new JMenuItem(t("Tümünü Seç"));
        selectAll.addActionListener(e -> selectAll());
        JMenuItem find = new JMenuItem(t("Bul"));
        find.addActionListener(e -> openFindDialog(false));
        JMenuItem replace = new JMenuItem(t("Değiştir"));
        replace.addActionListener(e -> openFindDialog(true));
        JMenuItem fold = new JMenuItem(t("Bloğu Katla"));
        fold.addActionListener(e -> toggleFoldAtCaret());
        JMenuItem unfold = new JMenuItem(t("Tümünü Aç"));
        unfold.addActionListener(e -> unfoldAll());
        JMenuItem zoomIn = new JMenuItem(t("Yakınlaştır"));
        zoomIn.addActionListener(e -> setZoom(fontSize + 1));
        JMenuItem zoomOut = new JMenuItem(t("Uzaklaştır"));
        zoomOut.addActionListener(e -> setZoom(fontSize - 1));
        JMenuItem zoomReset = new JMenuItem(t("Yakınlaştırmayı Sıfırla"));
        zoomReset.addActionListener(e -> setZoom(14));
        menu.add(cut);
        menu.add(copy);
        menu.add(paste);
        menu.add(selectAll);
        menu.addSeparator();
        menu.add(find);
        menu.add(replace);
        menu.addSeparator();
        menu.add(fold);
        menu.add(unfold);
        menu.addSeparator();
        menu.add(zoomIn);
        menu.add(zoomOut);
        menu.add(zoomReset);
        return menu;
    }

    private void copySelectionOrLine(boolean cut) {
        try {
            String selected = getSelectedText();
            String value;
            int start;
            int end;
            if (selected != null && !selected.isEmpty()) {
                value = selected;
                start = getSelectionStart();
                end = getSelectionEnd();
            } else {
                Element root = getDocument().getDefaultRootElement();
                int caret = Math.min(getCaretPosition(), getDocument().getLength());
                Element line = root.getElement(root.getElementIndex(caret));
                start = line.getStartOffset();
                end = Math.min(line.getEndOffset(), getDocument().getLength());
                value = getDocument().getText(start, end - start);
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(value), null);
            if (cut) {
                getDocument().remove(start, end - start);
                setCaretPosition(Math.min(start, getDocument().getLength()));
            }
        } catch (BadLocationException ignored) { }
    }

    private void pasteFromClipboard() {
        try {
            java.awt.datatransfer.Transferable data = Toolkit.getDefaultToolkit()
                    .getSystemClipboard().getContents(this);
            if (data != null && data.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                replaceSelection((String) data.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));
            }
        } catch (Exception ignored) { }
    }

    private void insertNewlineWithIndent() {
        int caret = getCaretPosition();
        Element root = getDocument().getDefaultRootElement();
        Element line = root.getElement(root.getElementIndex(caret));
        String indent = "";
        if (line != null) {
            try {
                String before = getDocument().getText(line.getStartOffset(), Math.max(0, caret - line.getStartOffset()));
                int i = 0;
                while (i < before.length() && (before.charAt(i) == ' ' || before.charAt(i) == '\t')) i++;
                indent = before.substring(0, i);
                String trimmed = before.stripTrailing();
                if (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")) indent += "    ";
                if (trimmed.endsWith("}") || trimmed.endsWith(")") || trimmed.endsWith("]")) {
                    if (indent.endsWith("    ")) indent = indent.substring(0, indent.length() - 4);
                }
            } catch (BadLocationException ignored) { }
        }
        replaceSelection("\n" + indent);
    }

    private void openFindDialog(boolean replace) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (findDialog == null || !findDialog.isDisplayable()) {
            findDialog = new EditorFindDialog(owner, () -> this);
        }
        findDialog.open(replace);
    }

    private void updateAttributeFontSizes() {
        for (SimpleAttributeSet attrs : List.of(normal, keyword, type, method, string, number, comment,
                annotation, xmlTag, xmlAttr, propertyKey)) {
            StyleConstants.setFontSize(attrs, fontSize);
        }
    }

    private void highlightMatchingBracket() {
        Highlighter highlighter = getHighlighter();
        for (Object tag : bracketTags) highlighter.removeHighlight(tag);
        bracketTags.clear();
        String text;
        try { text = getText(); } catch (Exception ex) { return; }
        int caret = Math.min(getCaretPosition(), text.length());
        int position = -1;
        if (caret > 0 && isBracket(text.charAt(caret - 1))) position = caret - 1;
        else if (caret < text.length() && isBracket(text.charAt(caret))) position = caret;
        if (position < 0) return;
        int match = matchingBracket(text, position);
        if (match < 0) return;
        Highlighter.HighlightPainter painter =
                new DefaultHighlighter.DefaultHighlightPainter(new Color(55, 214, 164, 80));
        try {
            bracketTags.add(highlighter.addHighlight(position, position + 1, painter));
            bracketTags.add(highlighter.addHighlight(match, match + 1, painter));
        } catch (BadLocationException ignored) { }
    }

    private static boolean isBracket(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }

    private static int matchingBracket(String text, int position) {
        char open = text.charAt(position);
        boolean forward = open == '(' || open == '[' || open == '{';
        char target = switch (open) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            case ')' -> '(';
            case ']' -> '[';
            case '}' -> '{';
            default -> open;
        };
        int depth = 0;
        if (forward) {
            for (int i = position; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == open) depth++;
                else if (c == target && --depth == 0) return i;
            }
        } else {
            for (int i = position; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == open) depth++;
                else if (c == target && --depth == 0) return i;
            }
        }
        return -1;
    }

    private void toggleFoldAtCaret() {
        String text = getText();
        int caret = Math.min(getCaretPosition(), text.length());
        int open = text.lastIndexOf('{', Math.max(0, caret));
        if (open < 0) return;
        int close = matchingBracket(text, open);
        if (close <= open) return;
        int start = text.indexOf('\n', open);
        int end = text.lastIndexOf('\n', close);
        if (start < 0 || end <= start) return;
        folds.clear();
        folds.add(new int[]{start + 1, end});
        highlightNow();
    }

    private void unfoldAll() {
        folds.clear();
        highlightNow();
    }

    private void applyFolds() {
        if (folds.isEmpty()) return;
        StyledDocument doc = getStyledDocument();
        SimpleAttributeSet hidden = new SimpleAttributeSet();
        StyleConstants.setFontSize(hidden, 1);
        StyleConstants.setForeground(hidden, getBackground());
        for (int[] range : folds) {
            int start = Math.max(0, Math.min(range[0], doc.getLength()));
            int end = Math.max(start, Math.min(range[1], doc.getLength()));
            if (end > start) doc.setCharacterAttributes(start, end - start, hidden, false);
        }
    }

    private void showCompletion() {
        if (!"java".equals(mode)) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        int caret = getCaretPosition();
        String text = getText();
        int start = caret;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
        String prefix = text.substring(start, caret);

        Set<String> candidates = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String item : COMPLETIONS) if (prefix.isEmpty() || item.regionMatches(true, 0, prefix, 0, prefix.length())) candidates.add(item);
        int scanFrom = Math.max(0, caret - 60_000);
        int scanTo = Math.min(text.length(), caret + 60_000);
        Matcher m = IDENTIFIER.matcher(text.substring(scanFrom, scanTo));
        while (m.find()) {
            String item = m.group();
            if ((prefix.isEmpty() || item.regionMatches(true, 0, prefix, 0, prefix.length())) && !item.equals(prefix)) candidates.add(item);
        }
        if (candidates.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        JList<String> results = new JList<>(candidates.toArray(String[]::new));
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setSelectedIndex(0);
        results.setVisibleRowCount(Math.min(9, candidates.size()));
        results.setBackground(Theme.PANEL_2);
        results.setForeground(Theme.TEXT);
        results.setSelectionBackground(Theme.SELECTION);
        results.setSelectionForeground(Theme.TEXT);
        results.setFont(Theme.mono(13));
        results.setFixedCellHeight(25);
        results.setBorder(new EmptyBorder(4, 5, 4, 5));

        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JScrollPane scroll = Ui.scroll(results);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(285, Math.min(235, 10 + results.getVisibleRowCount() * 25)));
        popup.add(scroll);

        final int replaceStart = start;
        Runnable accept = () -> {
            String selected = results.getSelectedValue();
            if (selected == null) return;
            try {
                getDocument().remove(replaceStart, getCaretPosition() - replaceStart);
                getDocument().insertString(replaceStart, selected, null);
                setCaretPosition(replaceStart + selected.length());
                String fqcn = COMMON_IMPORTS.get(selected);
                if (fqcn != null) ensureImport(fqcn);
            } catch (BadLocationException ignored) { }
            popup.setVisible(false);
            requestFocusInWindow();
        };

        results.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) accept.run(); }
        });
        results.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_TAB) {
                    e.consume(); accept.run();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.setVisible(false); requestFocusInWindow();
                }
            }
        });

        try {
            Rectangle r = modelToView2D(caret).getBounds();
            popup.show(this, r.x, r.y + r.height);
            SwingUtilities.invokeLater(results::requestFocusInWindow);
        } catch (BadLocationException ex) {
            popup.show(this, 20, 20);
        }
    }


    private void ensureImport(String fqcn) throws BadLocationException {
        String text = getText();
        String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        if (fqcn.startsWith("java.lang.")) return;
        if (text.contains("import " + fqcn + ";")) return;
        String parent = fqcn.substring(0, fqcn.lastIndexOf('.'));
        if (text.contains("import " + parent + ".*;")) return;
        Pattern pkgPattern = Pattern.compile("(?m)^package[ \\t]+([^;]+);[ \\t]*");
        Matcher pkg = pkgPattern.matcher(text);
        if (pkg.find() && pkg.group(1).trim().equals(parent)) return;

        int insertAt = 0;
        if (pkg.find(0)) insertAt = pkg.end();
        String addition = (insertAt == 0 ? "" : "\n") + "import " + fqcn + ";\n";
        int caret = getCaretPosition();
        getDocument().insertString(insertAt, addition, null);
        if (insertAt <= caret) setCaretPosition(caret + addition.length());
    }

    private void queueHighlight() {
        // A full styled-document recolor on every keystroke makes large files lag.
        // Restarting a short Swing timer collapses bursts of edits into one pass.
        highlightTimer.restart();
    }

    private void highlightNow() {
        StyledDocument doc = getStyledDocument();
        String text;
        try { text = doc.getText(0, doc.getLength()); }
        catch (BadLocationException e) { return; }

        int caret = getCaretPosition();
        updateAttributeFontSizes();
        doc.setCharacterAttributes(0, doc.getLength(), normal, true);
        if (doc.getLength() > MAX_HIGHLIGHT_CHARS) {
            setToolTipText(t("Büyük dosya: performans için syntax renklendirme geçici olarak sınırlandı."));
            return;
        }
        setToolTipText(t("Java dosyalarında Ctrl+Space ile kod tamamlama ve temel otomatik import"));

        try {
            switch (mode) {
                case "java" -> highlightJava(doc, text);
                case "xml" -> highlightXml(doc, text);
                case "gradle" -> highlightGradle(doc, text);
                case "properties" -> highlightProperties(doc, text);
                case "json" -> highlightJson(doc, text);
                default -> { }
            }
        } catch (StackOverflowError | RuntimeException ex) {
            // Renklendirme asla dosya açmayı engellememeli. Sorun çıkarsa düz metne döneriz.
            try { doc.setCharacterAttributes(0, doc.getLength(), normal, true); } catch (Throwable ignored) { }
        }
        applyFolds();
        if (caret <= doc.getLength()) setCaretPosition(caret);
        highlightMatchingBracket();
    }

    private void highlightJava(StyledDocument doc, String text) {
        apply(doc, text, JAVA_KEYWORDS, keyword, 0);
        apply(doc, text, JAVA_TYPES, type, 1);
        apply(doc, text, JAVA_METHODS, method, 1);
        apply(doc, text, ANNOTATIONS, annotation, 0);
        apply(doc, text, NUMBERS, number, 0);
        applyLiteralsAndComments(doc, text);
    }

    private void highlightXml(StyledDocument doc, String text) {
        apply(doc, text, XML_TAGS, xmlTag, 0);
        apply(doc, text, XML_ATTRS, xmlAttr, 0);
        apply(doc, text, STRINGS, string, 0);
        apply(doc, text, XML_COMMENTS, comment, 0);
    }

    private void highlightGradle(StyledDocument doc, String text) {
        apply(doc, text, GRADLE_KEYWORDS, keyword, 0);
        apply(doc, text, JAVA_METHODS, method, 1);
        apply(doc, text, NUMBERS, number, 0);
        applyLiteralsAndComments(doc, text);
    }

    private void highlightProperties(StyledDocument doc, String text) {
        apply(doc, text, PROPERTY_KEYS, propertyKey, 1);
        apply(doc, text, NUMBERS, number, 0);
        apply(doc, text, HASH_COMMENTS, comment, 0);
    }

    private void highlightJson(StyledDocument doc, String text) {
        apply(doc, text, STRINGS, string, 0);
        apply(doc, text, JSON_KEYS, propertyKey, 0);
        apply(doc, text, NUMBERS, number, 0);
        apply(doc, text, JSON_BOOLS, keyword, 0);
    }

    private void applyLiteralsAndComments(StyledDocument doc, String text) {
        Matcher m = LITERALS_AND_COMMENTS.matcher(text);
        while (m.find()) {
            String token = m.group();
            AttributeSet style = token.startsWith("//") || token.startsWith("/*") ? comment : string;
            doc.setCharacterAttributes(m.start(), m.end() - m.start(), style, false);
        }
    }

    private static void apply(StyledDocument doc, String text, Pattern p, AttributeSet style, int group) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            int start = group == 0 ? m.start() : m.start(group);
            int end = group == 0 ? m.end() : m.end(group);
            if (start >= 0 && end >= start) doc.setCharacterAttributes(start, end - start, style, false);
        }
    }

    private static Color token(Color dark, Color lightColor) {
        return Theme.isLight() ? lightColor : dark;
    }

    private static String modeFor(Path file) {
        if (file == null) return "text";
        String n = file.getFileName().toString().toLowerCase();
        if (n.endsWith(".java")) return "java";
        if (n.endsWith(".xml")) return "xml";
        if (n.endsWith(".gradle") || n.endsWith(".gradle.kts")) return "gradle";
        if (n.endsWith(".properties")) return "properties";
        if (n.endsWith(".json")) return "json";
        return "text";
    }

    private static SimpleAttributeSet attrs(Color color, int style) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        StyleConstants.setFontFamily(a, Font.MONOSPACED);
        StyleConstants.setFontSize(a, 14);
        StyleConstants.setBold(a, (style & Font.BOLD) != 0);
        StyleConstants.setItalic(a, (style & Font.ITALIC) != 0);
        return a;
    }
}
