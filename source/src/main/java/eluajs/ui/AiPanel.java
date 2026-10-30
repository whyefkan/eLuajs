package eluajs.ui;

import eluajs.ai.AiClient;
import eluajs.ai.AiErrors;
import eluajs.ai.AiConfig;
import eluajs.ai.AiMessage;
import eluajs.ai.AiPricing;
import eluajs.ai.ChatStore;
import eluajs.ai.AiAudit;
import eluajs.ai.AiProvider;
import eluajs.ai.AiResult;
import eluajs.ai.ProjectContext;
import eluajs.ai.ProjectIndex;
import eluajs.project.ProjectCheckpoints;
import eluajs.project.ProjectPageScaffold;
import eluajs.project.ProjectSearch;
import eluajs.project.ProjectValidator;
import eluajs.util.FileUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eluajs.util.I18n.t;

/** Clean, automatic AI chat panel. Edits the project directly and reports what changed. */
public final class AiPanel extends JPanel {
    private static final Pattern CODE_BLOCK = Pattern.compile(
            "(?s)```[ \\t]*[A-Za-z0-9_+.#-]*[ \\t]*\\R(.*?)```");
    private static final Pattern WRITE_BLOCK = Pattern.compile(
            "(?ms)^[ \\t]*WRITE:[ \\t]*(.+?)[ \\t]*\\R[ \\t]*```[^\\n]*\\R(.*?)```[ \\t]*");
    private static final Pattern PATCH_BLOCK = Pattern.compile(
            "(?ms)^[ \\t]*PATCH:[ \\t]*(.+?)[ \\t]*\\R<<<<<<< SEARCH\\R(.*?)^=======\\R(.*?)^>>>>>>> REPLACE[ \\t]*$");
    private static final String SYSTEM_PROMPT = """
            You are eLuajs AI, an autonomous Android Java coding agent inside the eLuajs IDE.
            eLuajs creates real Android Gradle projects with Java + XML and builds APKs/AABs.
            Keep solutions lightweight and compatible with the project toolchain. Minimize token use.

            Use only these commands:
            - READ: relative/path/File.java
              Optional line range: READ: relative/path/File.java#40-120
            - SEARCH: text
              Find text across the project (at most 2 searches).
            - PATCH: relative/path/File.java
              <<<<<<< SEARCH
              exact existing text
              =======
              replacement text
              >>>>>>> REPLACE
            - WRITE: relative/path/NewFile.java
              ```java
              <COMPLETE file content>
              ```
              Use WRITE for new files or when a full-file rewrite is truly needed.
            - DELETE: relative/path/File.java
            - CREATE_PAGE: Settings
              Creates SettingsActivity.java, activity_settings.xml, strings and the Manifest entry together.

            Rules:
            - Inspect relevant files before editing; prefer READ line ranges and SEARCH over full files.
            - For existing files, use PATCH; keep the SEARCH text exact and unique. Use WRITE only for new files or full rewrites.
            - Never use placeholders or patches that cannot match exactly.
            - Never claim that you changed a file unless you returned a PATCH, WRITE, DELETE or CREATE_PAGE command.
            - Do not write outside the project or into build/, .gradle/, .git/, release/ or .eluajs/.
            - Do not modify unrelated files. Do not invent APIs.
            - If a request needs several linked files, return all required commands in the same reply.
            - After editing, reply with a short summary. Do not paste the code again.
            - If the user asks a question only, do not use PATCH, WRITE, DELETE or CREATE_PAGE.
            - The app shows your changes for user approval before applying them.
            """;

    private static final int MAX_FILE_ROUNDS = 5;
    private static final int MAX_FILES_PER_REQUEST = 4;
    private static final int MAX_FILE_CHARS = 20_000;
    private static final int MAX_TOTAL_FILE_CHARS = 60_000;
    private static final int MAX_HISTORY_MESSAGES = 120;
    private static final int MAX_VISIBLE_COMPONENTS = 500;
    private static final int MAX_WRITE_CHARS = 500_000;
    private static final int MAX_SEARCHES_PER_REQUEST = 2;
    private static final int MAX_SEARCH_RESULTS = 60;
    private static final int MAX_CONTEXT_CHARS = 100_000;
    private static final int MAX_VALIDATION_ROUNDS = 2;
    private static final int AI_TEXT_WIDTH = 320;
    private static final int USER_TEXT_WIDTH = 260;

    private final Supplier<SyntaxEditorPane> editorProvider;
    private final Supplier<String> fileLabel;
    private final Supplier<Path> currentFile;
    private final Path projectRoot;
    private final Runnable onClose;
    private final Runnable beforeSend;
    private final Runnable afterFilesChanged;
    private final Consumer<Path> openFile;

    private AiConfig config = AiConfig.load();
    private AiProvider lastProvider;
    private final List<AiMessage> history = new ArrayList<>();
    private Map<Path, String> lastUndo = new LinkedHashMap<>();
    private Path lastCheckpoint;
    private String lastPrompt = "";
    private String cachedIndex;
    private long cachedIndexAt;
    private long contextTokensEstimate;
    private final java.util.LinkedList<String> promptHistory = new java.util.LinkedList<>();
    private int promptHistoryIndex;
    private final List<Path> pinned = new ArrayList<>();
    private String sessionId;
    private boolean busy;
    private SwingWorker<LoopResult, ProgressEvent> activeWorker;
    private String failedPrompt;

    private final JTextArea prompt = new JTextArea();
    private final JPanel conversation = new JPanel();
    private final JScrollPane conversationScroll;
    private final CardLayout chatCards = new CardLayout();
    private final JPanel chatArea = new JPanel(chatCards);
    private final JComboBox<AiProvider> providerCombo = new JComboBox<>(AiProvider.values());
    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
    private final JLabel usageLabel = Ui.label("", 9, Font.PLAIN, Theme.MUTED);
    private final WorkingIndicator working = new WorkingIndicator();
    private final JButton send = Ui.button("", true);
    private final JButton stop = Ui.button(t("Durdur"), false);
    private final JButton retry = Ui.button(t("Tekrar Dene"), false);
    private final JButton undoEdits = iconOnly("undo", t("Undo AI edits"));
    private long sessionPrompt;
    private long sessionCompletion;
    private long sessionTotal;
    private double sessionCost;
    private boolean loadingProvider;

    public AiPanel(SyntaxEditorPane editor, Supplier<String> fileLabel, Supplier<Path> currentFile,
                   Path projectRoot, Runnable onClose, Runnable beforeSend, Runnable afterFilesChanged) {
        this(() -> editor, fileLabel, currentFile, projectRoot, onClose, beforeSend, afterFilesChanged, null);
    }

    public AiPanel(Supplier<SyntaxEditorPane> editorProvider, Supplier<String> fileLabel, Supplier<Path> currentFile,
                   Path projectRoot, Runnable onClose, Runnable beforeSend, Runnable afterFilesChanged) {
        this(editorProvider, fileLabel, currentFile, projectRoot, onClose, beforeSend, afterFilesChanged, null);
    }

    public AiPanel(Supplier<SyntaxEditorPane> editorProvider, Supplier<String> fileLabel, Supplier<Path> currentFile,
                   Path projectRoot, Runnable onClose, Runnable beforeSend, Runnable afterFilesChanged,
                   Consumer<Path> openFile) {
        super(new BorderLayout());
        this.editorProvider = editorProvider;
        this.fileLabel = fileLabel;
        this.currentFile = currentFile;
        this.projectRoot = projectRoot;
        this.onClose = onClose;
        this.beforeSend = beforeSend;
        this.afterFilesChanged = afterFilesChanged;
        this.openFile = openFile;
        setBackground(Theme.PANEL);
        setPreferredSize(new Dimension(400, 1));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER_SOFT));

        conversation.setLayout(new BoxLayout(conversation, BoxLayout.Y_AXIS));
        conversation.setBackground(Theme.CONSOLE);
        conversation.setBorder(new EmptyBorder(10, 12, 10, 12));
        conversationScroll = Ui.scroll(conversation);
        conversationScroll.setBorder(null);
        conversationScroll.getViewport().setBackground(Theme.CONSOLE);
        conversationScroll.getVerticalScrollBar().setUnitIncrement(18);

        chatArea.setOpaque(false);
        chatArea.add(buildWelcomePanel(), "welcome");
        chatArea.add(conversationScroll, "chat");
        add(header(), BorderLayout.NORTH);
        add(chatArea, BorderLayout.CENTER);
        add(composer(), BorderLayout.SOUTH);
        initialise();
        installShortcuts();
        restoreLatest();
    }

    private void pinCurrent(JButton button) {
        Path file = currentFile == null ? null : currentFile.get();
        if (file == null) return;
        if (pinned.remove(file)) {
            button.setForeground(Theme.MUTED);
            addSystemNote(t("Unpinned: %s", file.getFileName()));
        } else if (pinned.size() < 6) {
            pinned.add(file);
            button.setForeground(Theme.ACCENT);
            addSystemNote(t("Pinned: %s", file.getFileName()));
        }
    }

    private JComponent header() {
        JPanel root = new JPanel(new BorderLayout(8, 0));
        root.setBackground(Theme.PANEL);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(8, 12, 8, 8)));

        JPanel titleText = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        titleText.setOpaque(false);
        titleText.add(Ui.label("✦", 13, Font.PLAIN, Theme.ACCENT));
        titleText.add(Ui.label(t("AI Assistant"), 14, Font.BOLD, Theme.TEXT));
        root.add(titleText, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        tools.setOpaque(false);
        undoEdits.setEnabled(false);
        undoEdits.addActionListener(e -> undoAiEdits());
        JButton history = iconOnly("history", t("Chat history"));
        history.addActionListener(e -> showHistory(history));
        JButton pin = Ui.button(t("Pin"), false);
        pin.setFont(Theme.font(9, Font.BOLD));
        pin.setBorder(new EmptyBorder(4, 6, 4, 6));
        pin.setToolTipText(t("Sabit context: geçerli dosyayı AI'ya ekle"));
        pin.addActionListener(e -> pinCurrent(pin));
        tools.add(pin);
        JButton clear = iconOnly("plus", t("New chat"));
        clear.addActionListener(e -> newChat());
        JButton close = iconOnly("close", t("Close"));
        close.addActionListener(e -> { if (onClose != null) onClose.run(); });
        tools.add(history);
        tools.add(undoEdits);
        tools.add(clear);
        tools.add(close);
        root.add(tools, BorderLayout.EAST);
        return root;
    }

    private JComponent composer() {
        JPanel root = new JPanel();
        root.setBackground(Theme.PANEL);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(8, 12, 10, 12));

        prompt.setFont(Theme.font(12, Font.PLAIN));
        prompt.setBackground(Theme.PANEL_2);
        prompt.setForeground(Theme.TEXT);
        prompt.setCaretColor(Theme.ACCENT);
        prompt.setSelectionColor(Theme.SELECTION);
        prompt.setSelectedTextColor(Theme.TEXT);
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        prompt.setBorder(new EmptyBorder(8, 10, 8, 10));
        prompt.setToolTipText(t("Enter: send  •  Shift+Enter: new line"));
        JScrollPane scroll = Ui.scroll(prompt);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(1, 58));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));

        ChatBubble promptBox = new ChatBubble(Theme.PANEL_2, Theme.BORDER, 12);
        promptBox.setLayout(new BorderLayout());
        promptBox.setBorder(new EmptyBorder(2, 2, 2, 2));
        promptBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        promptBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        promptBox.add(scroll, BorderLayout.CENTER);

        send.setIcon(Icons.icon("send", new Color(5, 36, 28), 16));
        send.setBorder(new EmptyBorder(8, 9, 8, 9));
        send.setPreferredSize(new Dimension(38, 38));
        send.setToolTipText(t("Send (Enter)"));
        send.addActionListener(e -> send());

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusRow.setOpaque(false);
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        stop.setFont(Theme.font(9, Font.BOLD));
        stop.setBorder(new EmptyBorder(2, 6, 2, 6));
        stop.setForeground(Theme.DANGER);
        stop.setVisible(false);
        stop.addActionListener(e -> stopRequest());
        retry.setFont(Theme.font(9, Font.BOLD));
        retry.setBorder(new EmptyBorder(2, 6, 2, 6));
        retry.setForeground(Theme.BLUE);
        retry.setEnabled(false);
        retry.addActionListener(e -> retryRequest());
        statusRow.add(working);
        statusRow.add(status);
        statusRow.add(retry);
        statusRow.add(stop);
        root.add(statusRow);
        root.add(Box.createVerticalStrut(5));

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        inputRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        inputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        inputRow.add(promptBox, BorderLayout.CENTER);
        JPanel sendWrap = new JPanel(new GridBagLayout());
        sendWrap.setOpaque(false);
        sendWrap.add(send);
        inputRow.add(sendWrap, BorderLayout.EAST);
        root.add(inputRow);
        root.add(Box.createVerticalStrut(6));

        Ui.styleCombo(providerCombo);
        Ui.styleCombo(modelCombo);
        providerCombo.setFont(Theme.font(11, Font.PLAIN));
        modelCombo.setFont(Theme.font(11, Font.PLAIN));
        modelCombo.setEditable(true);
        styleComboEditor(modelCombo);
        styleCombo(providerCombo);
        styleCombo(modelCombo);
        providerCombo.setToolTipText(t("Provider"));
        modelCombo.setToolTipText(t("You can type any model supported by the provider."));
        JPanel modelRow = new JPanel(new GridLayout(1, 2, 7, 0));
        modelRow.setOpaque(false);
        modelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        modelRow.add(providerCombo);
        modelRow.add(modelCombo);
        root.add(modelRow);
        root.add(Box.createVerticalStrut(4));

        JPanel bottomRow = new JPanel(new BorderLayout(8, 0));
        bottomRow.setOpaque(false);
        bottomRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        bottomRow.add(Ui.label(t("Enter: send  •  Shift+Enter: new line"), 9, Font.PLAIN, Theme.MUTED), BorderLayout.WEST);
        usageLabel.setToolTipText(t("Approximate cost based on public list prices."));
        bottomRow.add(usageLabel, BorderLayout.EAST);
        root.add(bottomRow);
        updateUsageLabel();

        prompt.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateSendState(); }
            public void removeUpdate(DocumentEvent e) { updateSendState(); }
            public void changedUpdate(DocumentEvent e) { }
        });
        updateSendState();
        return root;
    }

    private JButton iconOnly(String icon, String tooltip) {
        JButton button = Ui.button("", false);
        button.setIcon(Icons.icon(icon, Theme.TEXT_SOFT, 14));
        button.setBorder(new EmptyBorder(5, 6, 5, 6));
        button.setToolTipText(tooltip);
        return button;
    }

    private void stopRequest() {
        AiClient.cancelActive();
        if (activeWorker != null) activeWorker.cancel(true);
        busy = false;
        working.stop();
        stop.setVisible(false);
        status.setText(t("Durduruldu"));
        status.setForeground(Theme.MUTED);
        updateSendState();
    }

    private void retryRequest() {
        if (failedPrompt == null || failedPrompt.isBlank() || busy) return;
        prompt.setText(failedPrompt);
        send();
    }

    private void updateSendState() {
        send.setEnabled(!busy && !prompt.getText().trim().isEmpty());
    }

    public void reviewProject() {
        prompt.setText("Review the current project changes for bugs, Android compatibility, permissions and obvious performance problems. "
                + "Do not modify files; give a concise review with file references and suggest fixes.");
        send();
    }

    public void fixBuild(String buildLog) {
        if (buildLog == null) buildLog = "";
        String text = "The Android build failed. Diagnose the cause, inspect the relevant files, and fix the project. "
                + "Return the required WRITE commands and a short explanation.\n\nBUILD LOG:\n" + limit(buildLog, 14_000);
        prompt.setText(text);
        send();
    }

    public void focusPrompt() {
        SwingUtilities.invokeLater(prompt::requestFocusInWindow);
    }

    public void reloadConfig() {
        config = AiConfig.load();
        loadingProvider = true;
        providerCombo.setSelectedItem(config.provider());
        loadingProvider = false;
        loadModels();
    }

    private void initialise() {
        lastProvider = config.provider();
        loadingProvider = true;
        providerCombo.setSelectedItem(lastProvider);
        loadingProvider = false;
        loadModels();
        providerCombo.addActionListener(e -> {
            if (loadingProvider) return;
            storeModel(lastProvider);
            AiProvider next = (AiProvider) providerCombo.getSelectedItem();
            if (next != null) config.setProvider(next);
            loadModels();
            config.save();
            updateKeyStatus();
        });
        modelCombo.addActionListener(e -> {
            if (loadingProvider) return;
            AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
            if (provider == null) return;
            Object model = modelCombo.isEditable() ? modelCombo.getEditor().getItem() : modelCombo.getSelectedItem();
            config.setProvider(provider);
            config.setModel(provider, model == null ? "" : String.valueOf(model));
            config.save();
            updateKeyStatus();
        });
    }

    private void loadModels() {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        if (provider == null) return;
        lastProvider = provider;
        modelCombo.removeAllItems();
        for (String model : provider.models()) modelCombo.addItem(model);
        modelCombo.setSelectedItem(config.model(provider));
        modelCombo.getEditor().setItem(config.model(provider));
        updateKeyStatus();
    }

    private void storeModel(AiProvider provider) {
        if (provider == null) return;
        Object model = modelCombo.isEditable() ? modelCombo.getEditor().getItem() : modelCombo.getSelectedItem();
        config.setModel(provider, model == null ? "" : String.valueOf(model));
    }

    private void updateKeyStatus() {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        if (provider == null) provider = config.provider();
        boolean ready = !provider.requiresKey() || config.hasKey(provider);
        status.setText(ready ? t("Ready.") : t("No API key configured. Open Settings and add one."));
        status.setForeground(ready ? Theme.ACCENT : Theme.WARNING);
    }

    private void showWelcome() {
        chatCards.show(chatArea, "welcome");
        prompt.requestFocusInWindow();
    }

    private JComponent buildWelcomePanel() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.CONSOLE);
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel(Icons.icon("ai", Theme.ACCENT, 38));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = Ui.label(t("eLuajs AI"), 23, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = Ui.label(t("Ask me to inspect or change the project. Files are updated automatically."), 11, Font.PLAIN, Theme.MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(icon);
        card.add(Box.createVerticalStrut(9));
        card.add(title);
        card.add(Box.createVerticalStrut(6));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(16));

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        chips.setOpaque(false);
        welcomeChip(chips, t("Inspect project"), t("Inspect the project and summarize its structure."));
        welcomeChip(chips, t("Fix a problem"), t("Find and fix any obvious errors in the project."));
        welcomeChip(chips, t("Add a new screen"), t("Create a new Android activity screen for this project."));
        card.add(chips);
        root.add(card);
        return root;
    }

    private void welcomeChip(JPanel row, String label, String promptText) {
        JButton chip = Ui.button(label, false);
        chip.setFont(Theme.font(10, Font.BOLD));
        chip.setBorder(new EmptyBorder(6, 9, 6, 9));
        chip.addActionListener(e -> {
            prompt.setText(promptText);
            prompt.requestFocusInWindow();
            updateSendState();
        });
        row.add(chip);
    }

    private void newChat() {
        conversation.removeAll();
        history.clear();
        sessionId = null;
        lastPrompt = "";
        lastUndo = new LinkedHashMap<>();
        lastCheckpoint = null;
        undoEdits.setEnabled(false);
        working.stop();
        sessionPrompt = 0;
        sessionCompletion = 0;
        sessionTotal = 0;
        sessionCost = 0;
        contextTokensEstimate = 0;
        updateUsageLabel();
        conversation.revalidate();
        conversation.repaint();
        status.setText(t("Ready."));
        status.setForeground(Theme.ACCENT);
        updateSendState();
        showWelcome();
        updateKeyStatus();
    }

    private void restoreLatest() {
        ChatStore.Session session = ChatStore.latest(projectRoot);
        if (session != null && !session.messages().isEmpty()) loadSession(session);
        else showWelcome();
    }

    private void loadSession(ChatStore.Session session) {
        conversation.removeAll();
        history.clear();
        sessionId = session.id();
        chatCards.show(chatArea, "chat");
        lastPrompt = "";
        for (AiMessage message : session.messages()) {
            boolean user = "user".equalsIgnoreCase(message.role());
            addBubble(user, message.content(), false);
            history.add(message);
            if (user) lastPrompt = message.content();
        }
        lastUndo = new LinkedHashMap<>();
        lastCheckpoint = null;
        undoEdits.setEnabled(false);
        sessionPrompt = 0;
        sessionCompletion = 0;
        sessionTotal = 0;
        sessionCost = 0;
        contextTokensEstimate = 0;
        updateUsageLabel();
        status.setText(t("Restored chat: %s", session.title()));
        status.setForeground(Theme.MUTED);
        SwingUtilities.invokeLater(this::scrollToBottom);
    }

    private void ensureSession() {
        if (sessionId == null || sessionId.isBlank()) sessionId = ChatStore.newId();
    }

    private void saveSession() {
        if (sessionId == null || sessionId.isBlank() || history.isEmpty()) return;
        ChatStore.save(projectRoot, new ChatStore.Session(sessionId, ChatStore.titleFor(history),
                System.currentTimeMillis(), new ArrayList<>(history)));
    }

    private void showHistory(JButton anchor) {
        List<ChatStore.Session> sessions = ChatStore.list(projectRoot);
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JPanel panel = new JPanel();
        panel.setBackground(Theme.PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(8, 9, 8, 9));
        JLabel title = Ui.sectionLabel(t("Chat history"));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(5));
        if (sessions.isEmpty()) {
            JLabel empty = Ui.label(t("No saved chats yet."), 10, Font.PLAIN, Theme.MUTED);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(empty);
        } else {
            DateTimeFormatter format = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
            for (ChatStore.Session session : sessions) {
                JButton item = Ui.button("", false);
                item.setHorizontalAlignment(SwingConstants.LEFT);
                item.setFont(Theme.font(10, Font.PLAIN));
                item.setBorder(new EmptyBorder(5, 8, 5, 8));
                String date = format.format(Instant.ofEpochMilli(session.updatedAt()));
                String label = session.title();
                if (label.length() > 34) label = label.substring(0, 31) + "…";
                item.setText(label + "   ·   " + date);
                item.setToolTipText(session.title() + "  ·  " + date);
                item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                item.addActionListener(e -> {
                    popup.setVisible(false);
                    loadSession(session);
                });
                panel.add(item);
                panel.add(Box.createVerticalStrut(2));
            }
        }
        JScrollPane scroll = Ui.scroll(panel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.PANEL);
        int popupWidth = 320;
        int height = sessions.isEmpty() ? 80 : Math.min(340, 54 + sessions.size() * 30);
        popup.setPopupSize(popupWidth, height);
        popup.add(scroll);
        popup.show(anchor, -popupWidth + anchor.getWidth(), anchor.getHeight());
    }

    private String expandSlash(String text) {
        if (text == null || !text.startsWith("/")) return text;
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String tail = parts.length > 1 ? parts[1].trim() : "";
        return switch (command) {
            case "/fix" -> "Find and fix the current problem in this project. Inspect the relevant files and apply the smallest safe change.";
            case "/explain" -> "Explain the current file and the important project links. Do not modify files.";
            case "/page" -> tail.isBlank() ? "Create a new Android page. Ask for a clear name if needed." : "CREATE_PAGE: " + tail;
            case "/test" -> "Add a lightweight self-check or validation for this project without adding third-party dependencies.";
            case "/review" -> "Review the current project changes for bugs, Android compatibility, permissions and obvious performance problems. Do not modify files; give a concise review with file references.";
            default -> text;
        };
    }

    private void send() {
        String text = expandSlash(prompt.getText().trim());
        if (text.isEmpty() || busy) return;
        promptHistory.remove(text);
        promptHistory.addFirst(text);
        while (promptHistory.size() > 20) promptHistory.removeLast();
        promptHistoryIndex = -1;
        config = AiConfig.load();
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        if (provider == null) provider = config.provider();
        Object model = modelCombo.isEditable() ? modelCombo.getEditor().getItem() : modelCombo.getSelectedItem();
        config.setProvider(provider);
        config.setModel(provider, model == null ? "" : String.valueOf(model));
        config.save();
        if (provider.requiresKey() && !config.hasKey(provider)) {
            chatCards.show(chatArea, "chat");
            addBubble(false, t("No API key configured. Open Settings and add one."), true);
            status.setText(t("No API key configured. Open Settings and add one."));
            status.setForeground(Theme.WARNING);
            return;
        }
        if (beforeSend != null) beforeSend.run();

        chatCards.show(chatArea, "chat");
        lastPrompt = text;
        failedPrompt = null;
        retry.setEnabled(false);
        stop.setVisible(true);
        addBubble(true, text, false);
        history.add(AiMessage.user(text));
        while (history.size() > MAX_HISTORY_MESSAGES) history.remove(0);
        ensureSession();
        saveSession();
        prompt.setText("");
        busy = true;
        updateSendState();
        working.start();
        status.setText(t("Thinking..."));
        status.setForeground(Theme.MUTED);

        JTextArea streamArea = new JTextArea();
        JComponent pending = addStreamingBubble(streamArea);
        boolean projectAccess = projectRoot != null && Files.isDirectory(projectRoot);
        String system = buildSystemPrompt(projectAccess);
        List<AiMessage> messages = fitContext(system, requestHistory());
        contextTokensEstimate = Math.max(0, (system.length() + chars("", messages)) / 4L);
        updateUsageLabel();

        SwingWorker<LoopResult, ProgressEvent> worker = new SwingWorker<>() {
            @Override protected LoopResult doInBackground() throws Exception {
                return runAiLoop(system, messages, projectAccess, looksLikeChangeRequest(lastPrompt), event -> publish(event),
                        () -> SwingUtilities.invokeLater(() -> streamArea.setText("")),
                        chunk -> SwingUtilities.invokeLater(() -> {
                            streamArea.append(chunk);
                            streamArea.setCaretPosition(streamArea.getDocument().getLength());
                            JScrollBar bar = conversationScroll.getVerticalScrollBar();
                            bar.setValue(bar.getMaximum());
                        }));
            }
            @Override protected void process(List<ProgressEvent> chunks) {
                for (ProgressEvent event : chunks) {
                    String list = shortList(event.paths());
                    switch (event.kind()) {
                        case READ -> {
                            status.setText(t("Reading: %s", list));
                            status.setForeground(Theme.BLUE);
                            addSystemNote(t("Reading: %s", list));
                        }
                        case WRITE -> {
                            status.setText(t("Writing: %s", list));
                            status.setForeground(Theme.ACCENT);
                            addSystemNote(t("Updated: %s", list));
                        }
                        case DELETE -> {
                            status.setText(t("Deleting: %s", list));
                            status.setForeground(Theme.DANGER);
                            addSystemNote(t("Deleted: %s", list));
                        }
                    }
                }
            }
            @Override protected void done() {
                conversation.remove(pending);
                activeWorker = null;
                stop.setVisible(false);
                busy = false;
                working.stop();
                updateSendState();
                if (isCancelled()) {
                    status.setText(t("Durduruldu"));
                    status.setForeground(Theme.MUTED);
                    conversation.revalidate();
                    conversation.repaint();
                    scrollToBottom();
                    return;
                }
                try {
                    LoopResult result = get();
                    addBubble(false, result.text(), false);
                    history.add(AiMessage.assistant(result.text()));
                    while (history.size() > MAX_HISTORY_MESSAGES) history.remove(0);
                    saveSession();
                    sessionPrompt += result.promptTokens();
                    sessionCompletion += result.completionTokens();
                    sessionTotal += result.totalTokens();
                    sessionCost += result.cost();
                    if (result.totalTokens() > 0) {
                        addSystemNote(t("%s in / %s out • ~%s",
                                AiPricing.formatTokens(result.promptTokens()),
                                AiPricing.formatTokens(result.completionTokens()),
                                AiPricing.formatCost(result.cost())));
                    }
                    if (!lastUndo.isEmpty() || lastCheckpoint != null) undoEdits.setEnabled(true);
                    if (result.filesChanged() && afterFilesChanged != null) {
                        SwingUtilities.invokeLater(afterFilesChanged);
                    } else if (!result.filesChanged() && looksLikeChangeRequest(lastPrompt)) {
                        addSystemNote(t("No files were changed."));
                    }
                    updateUsageLabel();
                    status.setText(t("Ready."));
                    status.setForeground(Theme.ACCENT);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (!history.isEmpty() && "user".equals(history.get(history.size() - 1).role())) {
                        history.remove(history.size() - 1);
                    }
                    saveSession();
                    failedPrompt = lastPrompt;
                    retry.setEnabled(failedPrompt != null && !failedPrompt.isBlank());
                    String friendly = AiErrors.friendly(cause);
                    addBubble(false, t("Request failed: %s", friendly), true);
                    status.setText(t("Request failed: %s", friendly));
                    status.setForeground(Theme.DANGER);
                }
                conversation.revalidate();
                conversation.repaint();
                scrollToBottom();
            }
        };
        activeWorker = worker;
        worker.execute();
        trimConversation();
        conversation.revalidate();
        conversation.repaint();
        scrollToBottom();
    }

    private void trimConversation() {
        while (conversation.getComponentCount() > MAX_VISIBLE_COMPONENTS) {
            conversation.remove(0);
        }
    }

    private LoopResult runAiLoop(String system, List<AiMessage> initial, boolean projectAccess, boolean allowFallback,
                                 Consumer<ProgressEvent> progress, Runnable clearStream, Consumer<String> stream) throws Exception {
        List<AiMessage> convo = new ArrayList<>(initial);
        int totalRead = 0;
        int validationRounds = 0;
        int fileBudget = config.tokenBudgetEnabled()
                ? (int) Math.min(MAX_TOTAL_FILE_CHARS, Math.max(4_000L, (long) config.tokenBudget() * 3L))
                : MAX_TOTAL_FILE_CHARS;
        long promptTokens = 0, completionTokens = 0, totalTokens = 0;
        double cost = 0;
        boolean nudged = false;
        AiProvider usedProvider = config.provider();
        String usedModel = config.activeModel();
        for (int round = 0; ; round++) {
            clearStream.run();
            AiResult result = AiClient.completeStreaming(config, system, convo, stream);
            promptTokens += result.promptTokens();
            completionTokens += result.completionTokens();
            totalTokens += result.total();
            cost += AiPricing.estimate(usedProvider, usedModel, result.promptTokens(), result.completionTokens());
            String answer = result.text();

            List<PageRequest> pages = projectAccess ? parsePageRequests(answer) : List.of();
            if (!pages.isEmpty()) {
                StringBuilder summary = new StringBuilder();
                List<String> errors = new ArrayList<>();
                boolean createdAny = false;
                try { lastCheckpoint = ProjectCheckpoints.create(projectRoot, "ai-before-page"); }
                catch (Exception ignored) { }
                for (PageRequest page : pages) {
                    if (!confirmPage(page)) {
                        summary.append(t("Page creation cancelled.")).append('\n');
                        continue;
                    }
                    try {
                        ProjectPageScaffold.Result created = ProjectPageScaffold.create(projectRoot, page.name(), page.title());
                        summary.append(t("Created page: %s", created.className())).append('\n');
                        createdAny = true;
                        progress.accept(new ProgressEvent(EventKind.WRITE, List.of(created.className())));
                        invalidateIndex();
                        if (openFile != null && !created.files().isEmpty()) {
                            Path fresh = created.files().get(0);
                            SwingUtilities.invokeLater(() -> openFile.accept(fresh));
                        }
                    } catch (Exception ex) {
                        errors.add(page.name() + ": " + ex.getMessage());
                    }
                }
                if (errors.isEmpty()) {
                    String validation = validationFailure();
                    if (!validation.isBlank()) summary.append('\n').append(t("Validation issues remain; check the Problems panel."));
                    return new LoopResult(summary.toString().trim(), promptTokens, completionTokens, totalTokens, cost, createdAny);
                }
                summary.append(t("AI could not create page: %s", String.join("; ", errors)));
                return new LoopResult(summary.toString(), promptTokens, completionTokens, totalTokens, cost, false);
            }

            EditOutcome edit = projectAccess ? applyEditsIfPresent(answer, progress, allowFallback) : EditOutcome.NONE;
            if (edit.hasEdits()) {
                String validation = validationFailure();
                if (!validation.isBlank() && validationRounds < MAX_VALIDATION_ROUNDS && round < MAX_FILE_ROUNDS) {
                    validationRounds++;
                    convo.add(AiMessage.assistant(answer));
                    convo.add(AiMessage.user("VALIDATION FAILED after applying your edits.\n"
                            + validation + "\nFix only the validation problems with PATCH commands, then summarize."));
                    continue;
                }
                String summary = edit.summary();
                if (!validation.isBlank()) summary += "\n\n" + t("Validation issues remain; check the Problems panel.");
                return new LoopResult(summary, promptTokens, completionTokens, totalTokens, cost, edit.changed());
            }

            List<ReadRequest> requests = projectAccess ? parseReadRequests(answer) : List.of();
            List<SearchRequest> searches = projectAccess ? parseSearchRequests(answer) : List.of();
            if (requests.isEmpty() && searches.isEmpty()) {
                if (allowFallback && !nudged && round < MAX_FILE_ROUNDS && looksLikeChangeRequest(lastPrompt)) {
                    nudged = true;
                    convo.add(AiMessage.assistant(answer));
                    convo.add(AiMessage.user("Your previous reply only described the work. Apply the requested changes now. "
                            + "Use PATCH for existing files and WRITE for new files. Return all required commands."));
                    continue;
                }
                return new LoopResult(answer, promptTokens, completionTokens, totalTokens, cost, false);
            }
            if (round >= MAX_FILE_ROUNDS) {
                convo.add(AiMessage.assistant(answer));
                convo.add(AiMessage.user("FILE READ LIMIT REACHED. Give the final answer now using the files already provided."));
                clearStream.run();
                AiResult last = AiClient.completeStreaming(config, system, convo, stream);
                promptTokens += last.promptTokens();
                completionTokens += last.completionTokens();
                totalTokens += last.total();
                cost += AiPricing.estimate(usedProvider, usedModel, last.promptTokens(), last.completionTokens());
                EditOutcome lastEdit = projectAccess ? applyEditsIfPresent(last.text(), progress, allowFallback) : EditOutcome.NONE;
                if (lastEdit.hasEdits()) {
                    return new LoopResult(lastEdit.summary(), promptTokens, completionTokens, totalTokens, cost, lastEdit.changed());
                }
                return new LoopResult(last.text(), promptTokens, completionTokens, totalTokens, cost, false);
            }
            convo.add(AiMessage.assistant(answer));
            StringBuilder payload = new StringBuilder();
            List<String> readPaths = new ArrayList<>();
            if (!requests.isEmpty()) {
                String contents = readRequestedFiles(requests, fileBudget - totalRead);
                totalRead += contents.length();
                for (ReadRequest request : requests) readPaths.add(request.path());
                payload.append("REQUESTED FILE CONTENTS:\n").append(contents);
            }
            if (!searches.isEmpty()) {
                String found = searchProject(searches, Math.max(1000, fileBudget - totalRead));
                totalRead += found.length();
                if (!payload.isEmpty()) payload.append('\n');
                payload.append("SEARCH RESULTS:\n").append(found);
                for (SearchRequest search : searches) readPaths.add("search:" + search.query());
            }
            progress.accept(new ProgressEvent(EventKind.READ, readPaths));
            convo.add(AiMessage.user(payload.toString()));
            if (totalRead >= fileBudget) {
                convo.add(AiMessage.user("FILE READ LIMIT REACHED. Give the final answer now."));
                clearStream.run();
                AiResult last = AiClient.completeStreaming(config, system, convo, stream);
                promptTokens += last.promptTokens();
                completionTokens += last.completionTokens();
                totalTokens += last.total();
                cost += AiPricing.estimate(usedProvider, usedModel, last.promptTokens(), last.completionTokens());
                EditOutcome lastEdit = projectAccess ? applyEditsIfPresent(last.text(), progress, allowFallback) : EditOutcome.NONE;
                if (lastEdit.hasEdits()) {
                    return new LoopResult(lastEdit.summary(), promptTokens, completionTokens, totalTokens, cost, lastEdit.changed());
                }
                return new LoopResult(last.text(), promptTokens, completionTokens, totalTokens, cost, false);
            }
        }
    }

    private String validationFailure() {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) return "";
        try {
            ProjectValidator.Report report = ProjectValidator.validate(projectRoot);
            if (report.ok()) return "";
            StringBuilder b = new StringBuilder();
            for (String message : report.messages()) {
                if (b.length() > 4000) break;
                b.append("- ").append(message).append('\n');
            }
            return b.toString().trim();
        } catch (Exception ex) {
            return "Validation error: " + ex.getMessage();
        }
    }

    private EditOutcome applyEditsIfPresent(String answer, Consumer<ProgressEvent> progress, boolean allowFallback) {
        List<FileWrite> writes = new ArrayList<>(parseWrites(answer));
        List<String> deletes = parseDeletes(answer);
        List<String> patchErrors = new ArrayList<>();
        List<FileWrite> patched = materializePatches(answer, patchErrors);
        java.util.Set<String> explicitPaths = new java.util.HashSet<>();
        for (FileWrite write : writes) explicitPaths.add(write.path());
        for (FileWrite write : patched) if (!explicitPaths.contains(write.path())) writes.add(write);
        if (!writes.isEmpty() || !deletes.isEmpty()) {
            ApplyResult applied = applyEdits(writes, deletes, progress);
            String summary = stripAllCodeBlocks(stripEditCommands(answer)).trim();
            if (applied.changed() == 0) {
                if (applied.errors().contains(t("Changes rejected."))) summary = t("Changes rejected.");
                else if (!applied.errors().isEmpty()) summary = t("AI could not apply changes: %s", String.join("; ", applied.errors()));
                else if (summary.isBlank()) summary = t("No files were changed.");
            } else if (summary.isBlank()) {
                summary = t("Updated %d files.", applied.changed());
            }
            if (!applied.errors().isEmpty() && !summary.contains(applied.errors().get(0))) {
                summary += "\n\n" + t("Skipped: %s", String.join("; ", applied.errors()));
            }
            if (!patchErrors.isEmpty()) {
                summary += "\n\n" + t("Skipped: %s", String.join("; ", patchErrors));
            }
            return new EditOutcome(true, summary, applied.changed() > 0);
        }
        if (!patchErrors.isEmpty()) {
            return new EditOutcome(true, t("AI could not apply changes: %s", String.join("; ", patchErrors)), false);
        }

        // Fallback for change requests where a provider answers with a code block instead of WRITE.
        Path target = currentFile == null ? null : currentFile.get();
        if (allowFallback && projectRoot != null && target != null) {
            Matcher matcher = CODE_BLOCK.matcher(answer);
            if (matcher.find()) {
                String code = matcher.group(1);
                if (code != null && !code.isBlank()) {
                    Path safe = ProjectContext.safeResolve(projectRoot, projectRoot.toAbsolutePath().normalize()
                            .relativize(target.toAbsolutePath().normalize()).toString());
                    if (safe != null) {
                        if (!code.endsWith("\n")) code += "\n";
                        ApplyResult applied = applyEdits(List.of(new FileWrite(
                                projectRoot.toAbsolutePath().normalize().relativize(safe).toString(), code)), List.of(), progress);
                        String factual = t("Updated %s.", safe.getFileName().toString());
                        String note = stripAllCodeBlocks(answer).trim();
                        String summary = note.isBlank() ? factual : factual + "\n\n" + note;
                        if (applied.changed() == 0) {
                            summary = t("AI could not apply changes: %s", String.join("; ", applied.errors()));
                        }
                        return new EditOutcome(true, summary, applied.changed() > 0);
                    }
                }
            }
        }
        return EditOutcome.NONE;
    }

    private ApplyResult applyEdits(List<FileWrite> writes, List<String> deletes, Consumer<ProgressEvent> progress) {
        if (Thread.currentThread().isInterrupted()) {
            return new ApplyResult(0, List.of(t("Request cancelled.")));
        }
        java.util.Set<String> approved = approveChanges(writes, deletes);
        if (approved != null) {
            if (approved.isEmpty()) return new ApplyResult(0, List.of(t("Changes rejected.")));
            writes = writes.stream().filter(w -> approved.contains(w.path())).toList();
            deletes = deletes.stream().filter(approved::contains).toList();
        }
        if (!config.fullAccess() && hasSensitive(writes, deletes) && !confirmSensitive(writes, deletes)) {
            return new ApplyResult(0, List.of(t("Changes rejected.")));
        }
        Map<Path, String> undo = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int changed = 0;
        try {
            if (projectRoot != null && (!writes.isEmpty() || !deletes.isEmpty())) {
                lastCheckpoint = ProjectCheckpoints.create(projectRoot, "ai-before-edit");
            }
        } catch (Exception ignored) { }
        for (FileWrite write : writes) {
            Path target = ProjectContext.safeResolve(projectRoot, write.path());
            if (target == null) { errors.add(write.path() + " (not allowed)"); continue; }
            if (write.content().length() > MAX_WRITE_CHARS) { errors.add(write.path() + " (too large)"); continue; }
            try {
                if (Files.isRegularFile(target)) {
                    undo.putIfAbsent(target, Files.readString(target, StandardCharsets.UTF_8));
                    FileUtil.recoveryCopy(projectRoot, target);
                } else {
                    undo.putIfAbsent(target, null);
                }
                FileUtil.writeStringAtomic(target, write.content());
                AiAudit.log(projectRoot, "WRITE", rel(target), "bytes=" + write.content().length());
                changed++;
                progress.accept(new ProgressEvent(EventKind.WRITE, List.of(target.getFileName().toString())));
            } catch (Exception ex) {
                errors.add(write.path() + ": " + ex.getMessage());
            }
        }
        for (String delete : deletes) {
            Path target = ProjectContext.safeResolve(projectRoot, delete);
            if (target == null) { errors.add(delete + " (not allowed)"); continue; }
            if (!Files.isRegularFile(target)) { errors.add(delete + " (not found)"); continue; }
            try {
                undo.putIfAbsent(target, Files.readString(target, StandardCharsets.UTF_8));
                FileUtil.recoveryCopy(projectRoot, target);
                Files.delete(target);
                AiAudit.log(projectRoot, "DELETE", rel(target), "");
                changed++;
                progress.accept(new ProgressEvent(EventKind.DELETE, List.of(target.getFileName().toString())));
            } catch (Exception ex) {
                errors.add(delete + ": " + ex.getMessage());
            }
        }
        lastUndo = undo;
        invalidateIndex();
        return new ApplyResult(changed, errors);
    }

    private boolean hasSensitive(List<FileWrite> writes, List<String> deletes) {
        for (FileWrite write : writes) if (sensitive(write.path())) return true;
        for (String delete : deletes) if (sensitive(delete)) return true;
        return false;
    }

    private boolean sensitive(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT).replace('\\', '/');
        return lower.contains("release-signing") || lower.contains("keystore")
                || lower.endsWith(".jks") || lower.endsWith(".keystore");
    }

    private boolean confirmSensitive(List<FileWrite> writes, List<String> deletes) {
        final boolean[] approved = {false};
        Runnable ask = () -> approved[0] = JOptionPane.showConfirmDialog(this,
                t("This change touches signing or keystore files. Apply it?"),
                t("Sensitive files"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
        try { if (SwingUtilities.isEventDispatchThread()) ask.run(); else SwingUtilities.invokeAndWait(ask); }
        catch (Exception ignored) { }
        return approved[0];
    }

    private boolean confirmPage(PageRequest page) {
        if (config.fullAccess()) return true;
        final boolean[] approved = {false};
        Runnable ask = () -> approved[0] = JOptionPane.showConfirmDialog(this,
                t("Create page %s with its Activity, layout, string and Manifest entry?", page.name()),
                t("Create page"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
        try {
            if (SwingUtilities.isEventDispatchThread()) ask.run();
            else SwingUtilities.invokeAndWait(ask);
        } catch (Exception ignored) { }
        return approved[0];
    }

    private java.util.Set<String> approveChanges(List<FileWrite> writes, List<String> deletes) {
        if (config.fullAccess()) return null;
        List<AiDiffDialog.Change> changes = new ArrayList<>();
        for (FileWrite write : writes) {
            Path target = ProjectContext.safeResolve(projectRoot, write.path());
            if (target == null) continue;
            String oldText = "";
            try {
                if (Files.isRegularFile(target)) oldText = Files.readString(target, StandardCharsets.UTF_8);
            } catch (Exception ignored) { }
            changes.add(new AiDiffDialog.Change(write.path(), oldText, write.content(), false));
        }
        for (String delete : deletes) {
            Path target = ProjectContext.safeResolve(projectRoot, delete);
            if (target == null || !Files.isRegularFile(target)) continue;
            try {
                changes.add(new AiDiffDialog.Change(delete, Files.readString(target, StandardCharsets.UTF_8), "", true));
            } catch (Exception ignored) { }
        }
        if (changes.isEmpty()) return java.util.Set.of();
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (SwingUtilities.isEventDispatchThread()) return approvedPaths(AiDiffDialog.approve(owner, changes));
        final java.util.concurrent.atomic.AtomicReference<java.util.List<AiDiffDialog.Change>> selected =
                new java.util.concurrent.atomic.AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> selected.set(AiDiffDialog.approve(owner, changes)));
        } catch (Exception ex) {
            return java.util.Set.of();
        }
        return approvedPaths(selected.get());
    }

    private static java.util.Set<String> approvedPaths(java.util.List<AiDiffDialog.Change> changes) {
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();
        if (changes != null) for (AiDiffDialog.Change change : changes) paths.add(change.path());
        return paths;
    }

    private List<PatchEdit> parsePatches(String answer) {
        List<PatchEdit> out = new ArrayList<>();
        if (answer == null) return out;
        Matcher matcher = PATCH_BLOCK.matcher(answer);
        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String search = matcher.group(2);
            String replace = matcher.group(3);
            if (!path.isBlank() && !search.isEmpty()) out.add(new PatchEdit(path, search, replace));
        }
        return out;
    }

    private List<FileWrite> materializePatches(String answer, List<String> errors) {
        Map<String, String> files = new LinkedHashMap<>();
        for (PatchEdit patch : parsePatches(answer)) {
            Path target = ProjectContext.safeResolve(projectRoot, patch.path());
            if (target == null || !Files.isRegularFile(target)) {
                errors.add(patch.path() + " (not found)");
                continue;
            }
            try {
                String rel = projectRoot.toAbsolutePath().normalize()
                        .relativize(target.toAbsolutePath().normalize()).toString();
                String old = files.containsKey(rel) ? files.get(rel) : Files.readString(target, StandardCharsets.UTF_8);
                String needle = patch.search();
                int first = old.indexOf(needle);
                if (first < 0 && old.indexOf('\r') >= 0) {
                    old = old.replace("\r\n", "\n");
                    needle = needle.replace("\r\n", "\n");
                    first = old.indexOf(needle);
                }
                if (first < 0) {
                    String looseOld = old.replaceAll("(?m)[ \t]+$", "");
                    String looseNeedle = needle.replaceAll("(?m)[ \t]+$", "");
                    first = looseOld.indexOf(looseNeedle);
                    if (first >= 0) { old = looseOld; needle = looseNeedle; }
                }
                if (first < 0) { errors.add(patch.path() + " (SEARCH text not found)"); continue; }
                if (old.indexOf(needle, first + 1) >= 0) {
                    errors.add(patch.path() + " (SEARCH text is not unique)");
                    continue;
                }
                files.put(rel, old.substring(0, first) + patch.replace()
                        + old.substring(first + needle.length()));
            } catch (Exception ex) {
                errors.add(patch.path() + ": " + ex.getMessage());
            }
        }
        List<FileWrite> out = new ArrayList<>();
        for (var entry : files.entrySet()) out.add(new FileWrite(entry.getKey(), entry.getValue()));
        return out;
    }

    private List<FileWrite> parseWrites(String answer) {
        List<FileWrite> out = new ArrayList<>();
        if (answer == null) return out;
        Matcher matcher = WRITE_BLOCK.matcher(answer);
        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String content = matcher.group(2);
            if (!path.isBlank() && !content.isEmpty()) {
                if (!content.endsWith("\n")) content += "\n";
                out.add(new FileWrite(path, content));
            }
        }
        return out;
    }

    private List<String> parseDeletes(String answer) {
        List<String> out = new ArrayList<>();
        if (answer == null) return out;
        String rest = PATCH_BLOCK.matcher(WRITE_BLOCK.matcher(answer).replaceAll("")).replaceAll("");
        boolean inFence = false;
        for (String raw : rest.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("```")) { inFence = !inFence; continue; }
            if (inFence) continue;
            if (line.regionMatches(true, 0, "DELETE:", 0, 7)) {
                String path = line.substring(7).trim();
                if (path.startsWith("`") && path.endsWith("`") && path.length() > 1) path = path.substring(1, path.length() - 1).trim();
                if (!path.isBlank() && !out.contains(path)) out.add(path);
            }
        }
        return out;
    }

    private String stripEditCommands(String answer) {
        String rest = PATCH_BLOCK.matcher(WRITE_BLOCK.matcher(answer).replaceAll("")).replaceAll("");
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        for (String line : rest.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                out.append(line).append('\n');
                continue;
            }
            if (!inFence && trimmed.regionMatches(true, 0, "DELETE:", 0, 7)) continue;
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private static String stripAllCodeBlocks(String answer) {
        return CODE_BLOCK.matcher(answer == null ? "" : answer).replaceAll("").trim();
    }

    private String rel(Path path) {
        return projectRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private void undoAiEdits() {
        if (lastUndo.isEmpty()) {
            if (lastCheckpoint != null && projectRoot != null) {
                try {
                    ProjectCheckpoints.restore(projectRoot, lastCheckpoint);
                    lastCheckpoint = null;
                    undoEdits.setEnabled(false);
                    addSystemNote(t("AI edits undone."));
                    status.setText(t("AI edits undone."));
                    status.setForeground(Theme.ACCENT);
                    if (afterFilesChanged != null) afterFilesChanged.run();
                } catch (Exception ex) {
                    status.setText(t("Undo failed: %s", ex.getMessage()));
                    status.setForeground(Theme.DANGER);
                }
            }
            return;
        }
        List<String> errors = new ArrayList<>();
        for (Map.Entry<Path, String> entry : lastUndo.entrySet()) {
            try {
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                else FileUtil.writeStringAtomic(entry.getKey(), entry.getValue());
            } catch (Exception ex) {
                errors.add(rel(entry.getKey()) + ": " + ex.getMessage());
            }
        }
        lastUndo = new LinkedHashMap<>();
        undoEdits.setEnabled(false);
        if (errors.isEmpty()) {
            addSystemNote(t("AI edits undone."));
            status.setText(t("AI edits undone."));
            status.setForeground(Theme.ACCENT);
        } else {
            status.setText(t("Undo failed: %s", String.join("; ", errors)));
            status.setForeground(Theme.DANGER);
        }
        invalidateIndex();
        if (afterFilesChanged != null) afterFilesChanged.run();
    }

    private void updateUsageLabel() {
        int budget = Math.max(4000, config.tokenBudget());
        if (!config.tokenBudgetEnabled()) {
            usageLabel.setText((sessionTotal <= 0 ? "0" : AiPricing.formatTokens(sessionTotal)) + " tok • ~"
                    + AiPricing.formatCost(sessionCost));
        } else if (sessionTotal <= 0) {
            usageLabel.setText("0 / " + AiPricing.formatTokens(budget) + " tok");
        } else {
            usageLabel.setText(AiPricing.formatTokens(sessionTotal) + " / " + AiPricing.formatTokens(budget)
                    + " tok • ~" + AiPricing.formatCost(sessionCost));
        }
        if (contextTokensEstimate > 0) usageLabel.setText(usageLabel.getText()
                + " • ctx ~" + AiPricing.formatTokens(contextTokensEstimate));
    }

    private String indexForContext() {
        long now = System.currentTimeMillis();
        if (cachedIndex == null || now - cachedIndexAt > 60_000L) {
            cachedIndex = ProjectIndex.compact(projectRoot);
            cachedIndexAt = now;
        }
        return cachedIndex;
    }

    private void invalidateIndex() { cachedIndex = null; }

    private String buildSystemPrompt(boolean projectAccess) {
        StringBuilder b = new StringBuilder(SYSTEM_PROMPT);
        if (projectAccess) {
            b.append("\n\n").append(indexForContext());
        } else {
            b.append("\n\nProject editing is disabled. Return code in fenced blocks and explain where to apply it.\n");
        }
        String rules = projectRules();
        if (!rules.isBlank()) b.append("\n\nPROJECT RULES:\n").append(rules);
        if (!pinned.isEmpty()) {
            b.append("\n\nPINNED FILES:\n");
            int budget = 12_000;
            for (Path file : pinned) {
                if (budget <= 0) break;
                String rel;
                try { rel = projectRoot.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString(); }
                catch (Exception ex) { rel = file.getFileName().toString(); }
                String content = ProjectContext.readTextFile(projectRoot, rel, Math.min(4_000, budget));
                if (content != null) {
                    b.append("===== ").append(rel).append(" =====\n").append(ProjectContext.redactSecrets(content)).append('\n');
                    budget -= content.length();
                }
            }
        }
        b.append("\n\nCURRENT FILE (").append(fileLabel == null ? "unknown" : fileLabel.get()).append("):\n");
        b.append(limit(currentFileText(), 12_000));
        return b.toString();
    }

    private String projectRules() {
        if (projectRoot == null) return "";
        for (String name : new String[]{"ELUAJS.md", "AGENTS.md"}) {
            Path file = projectRoot.resolve(name);
            if (!Files.isRegularFile(file)) continue;
            try { return limit(Files.readString(file, StandardCharsets.UTF_8), 8_000); }
            catch (IOException ignored) { }
        }
        return "";
    }

    private List<ReadRequest> parseReadRequests(String answer) {
        if (answer == null) return List.of();
        List<ReadRequest> out = new ArrayList<>();
        boolean inFence = false;
        for (String raw : answer.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("```")) { inFence = !inFence; continue; }
            if (inFence) continue;
            while (line.startsWith("- ") || line.startsWith("* ")) line = line.substring(2).trim();
            if (!line.regionMatches(true, 0, "READ:", 0, 5)) continue;
            String value = line.substring(5).trim();
            if (value.startsWith("`") && value.endsWith("`") && value.length() > 1) value = value.substring(1, value.length() - 1).trim();
            if (value.isBlank()) continue;
            int startLine = 0, endLine = 0;
            int hash = value.indexOf('#');
            if (hash > 0) {
                String range = value.substring(hash + 1).trim();
                value = value.substring(0, hash).trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("L?(\\d+)(?:\\s*[-:]\\s*L?(\\d+))?")
                        .matcher(range);
                if (m.find()) {
                    startLine = Integer.parseInt(m.group(1));
                    endLine = m.group(2) == null ? startLine + 120 : Integer.parseInt(m.group(2));
                }
            }
            final String readPath = value;
            if (!readPath.isBlank() && out.stream().noneMatch(r -> r.path().equals(readPath))) {
                out.add(new ReadRequest(readPath, startLine, endLine));
            }
            if (out.size() >= MAX_FILES_PER_REQUEST) break;
        }
        return out;
    }

    private List<SearchRequest> parseSearchRequests(String answer) {
        if (answer == null) return List.of();
        List<SearchRequest> out = new ArrayList<>();
        boolean inFence = false;
        for (String raw : answer.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("```")) { inFence = !inFence; continue; }
            if (inFence) continue;
            while (line.startsWith("- ") || line.startsWith("* ")) line = line.substring(2).trim();
            if (!line.regionMatches(true, 0, "SEARCH:", 0, 7)) continue;
            String query = line.substring(7).trim();
            if (query.startsWith("`") && query.endsWith("`") && query.length() > 1) query = query.substring(1, query.length() - 1).trim();
            final String searchQuery = query;
            if (!searchQuery.isBlank() && out.stream().noneMatch(r -> r.query().equals(searchQuery))) out.add(new SearchRequest(searchQuery));
            if (out.size() >= MAX_SEARCHES_PER_REQUEST) break;
        }
        return out;
    }

    private List<PageRequest> parsePageRequests(String answer) {
        if (answer == null) return List.of();
        List<PageRequest> out = new ArrayList<>();
        boolean inFence = false;
        for (String raw : answer.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("```")) { inFence = !inFence; continue; }
            if (inFence || !line.regionMatches(true, 0, "CREATE_PAGE:", 0, 12)) continue;
            String value = line.substring(12).trim();
            if (value.startsWith("`") && value.endsWith("`") && value.length() > 1) value = value.substring(1, value.length() - 1).trim();
            String[] parts = value.split("\\|", 2);
            String name = parts[0].trim();
            String title = parts.length > 1 ? parts[1].trim() : "";
            if (!name.isBlank()) out.add(new PageRequest(name, title));
            if (out.size() >= 3) break;
        }
        return out;
    }

    private String readRequestedFiles(List<ReadRequest> requests, int budget) {
        StringBuilder b = new StringBuilder();
        for (ReadRequest request : requests) {
            if (b.length() >= budget) break;
            int room = Math.max(1, Math.min(MAX_FILE_CHARS, budget - b.length()));
            String content = request.start() > 0
                    ? ProjectContext.readTextFileRange(projectRoot, request.path(), request.start(), request.end(), room)
                    : ProjectContext.readTextFile(projectRoot, request.path(), room);
            b.append("\n===== ").append(request.path());
            if (request.start() > 0) b.append("#").append(request.start()).append('-').append(request.end());
            b.append(" =====\n");
            if (content == null) b.append("(File not found, not a text file, or too large.)\n");
            else b.append(ProjectContext.redactSecrets(content)).append('\n');
        }
        return b.toString();
    }

    private String searchProject(List<SearchRequest> searches, int budget) {
        StringBuilder b = new StringBuilder();
        for (SearchRequest search : searches) {
            if (b.length() >= budget) break;
            List<ProjectSearch.Match> matches = ProjectIndex.search(projectRoot, search.query(), MAX_SEARCH_RESULTS);
            b.append("QUERY: ").append(search.query()).append(" (").append(matches.size()).append(" results)\n");
            for (ProjectSearch.Match match : matches) {
                if (b.length() >= budget) break;
                b.append(projectRoot.relativize(match.file())).append(':').append(match.line())
                        .append(": ").append(match.preview()).append('\n');
            }
        }
        return b.toString();
    }

    private static String limit(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max) + "\n... (truncated)";
    }

    private List<AiMessage> requestHistory() {
        if (history.isEmpty()) return new ArrayList<>();
        int keep = config.tokenBudgetEnabled() ? 12 : 24;
        if (history.size() <= keep) return new ArrayList<>(history);
        int from = history.size() - keep;
        StringBuilder summary = new StringBuilder("EARLIER CONVERSATION SUMMARY (compact):\n");
        for (int i = 0; i < from; i++) {
            AiMessage message = history.get(i);
            if (message == null || message.content() == null) continue;
            if (!"user".equals(message.role())) continue;
            String text = message.content().replaceAll("\\s+", " ").trim();
            if (text.length() > 180) text = text.substring(0, 177) + "…";
            summary.append("- ").append(text).append('\n');
            if (summary.length() > 2400) { summary.append("- …\n"); break; }
        }
        List<AiMessage> out = new ArrayList<>();
        if (summary.length() > 36) out.add(AiMessage.user(summary.toString()));
        out.addAll(history.subList(from, history.size()));
        return out;
    }

    private List<AiMessage> fitContext(String system, List<AiMessage> messages) {
        int limit = MAX_CONTEXT_CHARS;
        if (config.tokenBudgetEnabled()) {
            limit = (int) Math.min(MAX_CONTEXT_CHARS, Math.max(12_000L, (long) config.tokenBudget() * 4L));
        }
        List<AiMessage> out = new ArrayList<>(messages);
        while (chars(system, out) > limit && out.size() > 1) {
            int remove = 0;
            if (out.get(0).role().equals("user") && out.get(0).content() != null
                    && out.get(0).content().startsWith("EARLIER CONVERSATION SUMMARY")) remove = 1;
            out.remove(remove);
        }
        return out;
    }

    private static int chars(String system, List<AiMessage> messages) {
        int total = system == null ? 0 : system.length();
        for (AiMessage m : messages) total += m.content() == null ? 0 : m.content().length();
        return total;
    }

    private String currentFileText() {
        SyntaxEditorPane editor = editorProvider == null ? null : editorProvider.get();
        if (editor == null) return "";
        String current = ProjectContext.redactSecrets(editor.getText());
        if (current.length() > 60_000) current = current.substring(0, 60_000) + "\n... (truncated)";
        String label = fileLabel == null ? "unknown" : fileLabel.get();
        return "Current file: " + label + "\n--- FILE START ---\n" + current + "\n--- FILE END ---";
    }

    private static boolean looksLikeChangeRequest(String text) {
        if (text == null) return false;
        String s = text.toLowerCase(Locale.ROOT);
        String[] words = {"fix", "update", "change", "add", "remove", "delete", "rename", "refactor",
                "implement", "create", "write", "theme", "page", "improve", "polish",
                "düzelt", "güncelle", "değiştir", "ekle", "sil", "kaldır", "oluştur", "yaz", "güçlendir", "tema", "sayfa"};
        for (String word : words) if (s.contains(word)) return true;
        return false;
    }

    private void styleCombo(JComboBox<?> combo) {
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                label.setBackground(selected ? Theme.SELECTION : Theme.PANEL_2);
                label.setForeground(Theme.TEXT);
                label.setFont(Theme.font(11, Font.PLAIN));
                label.setBorder(new EmptyBorder(3, 6, 3, 6));
                return label;
            }
        });
    }

    private void styleComboEditor(JComboBox<?> combo) {
        Component editor = combo.getEditor().getEditorComponent();
        if (editor instanceof JTextField field) {
            field.setBackground(Theme.PANEL_2);
            field.setForeground(Theme.TEXT);
            field.setCaretColor(Theme.ACCENT);
            field.setSelectionColor(Theme.SELECTION);
            field.setSelectedTextColor(Theme.TEXT);
            field.setFont(Theme.font(11, Font.PLAIN));
            field.setBorder(new EmptyBorder(5, 8, 5, 8));
        }
    }

    private void installShortcuts() {
        InputMap im = prompt.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = prompt.getActionMap();
        AbstractAction sendAction = new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (send.isEnabled()) send();
            }
        };
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "eluajs-ai-send");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "eluajs-ai-send");
        am.put("eluajs-ai-send", sendAction);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "eluajs-ai-prev");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "eluajs-ai-next");
        am.put("eluajs-ai-prev", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (promptHistory.isEmpty()) return;
                promptHistoryIndex = Math.min(promptHistory.size() - 1, promptHistoryIndex + 1);
                prompt.setText(promptHistory.get(promptHistoryIndex));
            }
        });
        am.put("eluajs-ai-next", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (promptHistory.isEmpty()) return;
                promptHistoryIndex = Math.max(0, promptHistoryIndex - 1);
                prompt.setText(promptHistory.get(promptHistoryIndex));
            }
        });
    }

    private JComponent addStreamingBubble(JTextArea area) {
        area.setEditable(false);
        area.setOpaque(false);
        area.setForeground(Theme.TEXT_SOFT);
        area.setFont(Theme.font(12, Font.PLAIN));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(0, 0, 0, 0));
        ChatBubble bubble = new ChatBubble(Theme.CONSOLE, Theme.BORDER_SOFT, 12);
        bubble.setLayout(new BorderLayout());
        bubble.setBorder(new EmptyBorder(8, 10, 8, 10));
        bubble.add(area, BorderLayout.CENTER);
        bubble.setMaximumSize(new Dimension(AI_TEXT_WIDTH, Integer.MAX_VALUE));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.add(bubble);
        conversation.add(row);
        conversation.add(Box.createVerticalStrut(8));
        trimConversation();
        conversation.revalidate();
        conversation.repaint();
        scrollToBottom();
        return row;
    }

    private JComponent addBubble(boolean user, String text, boolean error) {
        if (text == null) text = "";
        int width = user ? USER_TEXT_WIDTH : AI_TEXT_WIDTH;
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        Color textColor = user ? Theme.TEXT : (error ? Theme.DANGER : Theme.TEXT_SOFT);
        for (Segment segment : segments(text)) {
            if (segment.code()) {
                ChatBubble codeBox = new ChatBubble(Theme.CONSOLE, Theme.BORDER_SOFT, 8);
                codeBox.setLayout(new BorderLayout());
                codeBox.setBorder(new EmptyBorder(7, 9, 7, 9));
                codeBox.setAlignmentX(Component.LEFT_ALIGNMENT);
                WrapArea area = new WrapArea(segment.text(), width - 22, Theme.mono(11),
                        error ? Theme.DANGER : (Theme.isLight() ? new Color(22, 101, 52) : new Color(152, 195, 121)));
                codeBox.add(area, BorderLayout.CENTER);
                content.add(codeBox);
                content.add(Box.createVerticalStrut(6));
            } else {
                String clean = cleanInlineMarkdown(segment.text().strip());
                if (clean.isBlank()) continue;
                WrapArea area = new WrapArea(clean, width, Theme.font(12, Font.PLAIN), textColor);
                area.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(area);
                content.add(Box.createVerticalStrut(4));
            }
        }

        JPanel row = new JPanel(new FlowLayout(user ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        if (user) {
            ChatBubble bubble = new ChatBubble(Theme.ACCENT_DARK, Theme.isLight() ? Theme.BORDER : new Color(41, 118, 96), 14);
            bubble.setLayout(new BorderLayout());
            bubble.setBorder(new EmptyBorder(9, 12, 9, 12));
            bubble.add(content, BorderLayout.CENTER);
            bubble.setMaximumSize(new Dimension(width + 24, Integer.MAX_VALUE));
            row.add(bubble);
        } else {
            content.setMaximumSize(new Dimension(width + 8, Integer.MAX_VALUE));
            row.add(content);
        }
        Dimension rowPref = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(1, rowPref.height)));
        conversation.add(row);
        conversation.add(Box.createVerticalStrut(8));
        trimConversation();
        conversation.revalidate();
        conversation.repaint();
        scrollToBottom();
        return row;
    }

    private static String shortList(List<String> paths) {
        String list = String.join(", ", paths);
        return list.length() > 70 ? list.substring(0, 67) + "…" : list;
    }

    private void addSystemNote(String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        ChatBubble chip = new ChatBubble(Theme.PANEL_2, Theme.BORDER_SOFT, 10);
        chip.setLayout(new BorderLayout());
        chip.setBorder(new EmptyBorder(4, 9, 4, 9));
        chip.add(Ui.label(text, 9, Font.ITALIC, Theme.MUTED), BorderLayout.CENTER);
        row.add(chip);
        Dimension pref = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(1, pref.height)));
        conversation.add(row);
        conversation.add(Box.createVerticalStrut(5));
        trimConversation();
        conversation.revalidate();
        conversation.repaint();
        scrollToBottom();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = conversationScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private List<Segment> segments(String text) {
        List<Segment> out = new ArrayList<>();
        Matcher m = CODE_BLOCK.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.add(new Segment(text.substring(last, m.start()), false));
            out.add(new Segment(m.group(1), true));
            last = m.end();
        }
        if (last < text.length()) out.add(new Segment(text.substring(last), false));
        if (out.isEmpty()) out.add(new Segment(text, false));
        return out;
    }

    private static String cleanInlineMarkdown(String text) {
        String s = text.replace("**", "").replace("__", "").replace("~~", "").replace("`", "");
        s = s.replaceAll("(?m)^\\s*#{1,6}\\s+", "");
        s = s.replaceAll("(?m)^\\s*[-*]\\s+", "• ");
        return s.replace("*", "").trim();
    }

    private static String markdownToHtml(String text, Color color, int width) {
        String html = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("~~(.+?)~~", "<strike>$1</strike>");
        html = html.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<i>$1</i>");
        html = html.replaceAll("`([^`]+)`", "<font face=\"Monospaced\" color=\"#98c379\">$1</font>");
        html = html.replaceAll("(?m)^\\s*#{1,6}\\s+(.+)$", "<b>$1</b>");
        html = html.replaceAll("(?m)^\\s*[-*]\\s+", "• ");
        html = html.replace("**", "").replace("~~", "");
        html = html.replace("\r\n", "\n").replace("\n", "<br>");
        String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return "<html><body style='margin:0;width:" + Math.max(80, width - 4)
                + "px;font-family:SansSerif;font-size:12px;color:" + hex + "'>" + html + "</body></html>";
    }

    private record ReadRequest(String path, int start, int end) {}
    private record SearchRequest(String query) {}
    private record PageRequest(String name, String title) {}
    private record PatchEdit(String path, String search, String replace) {}

    private record LoopResult(String text, long promptTokens, long completionTokens, long totalTokens, double cost, boolean filesChanged) { }
    private record ProgressEvent(EventKind kind, List<String> paths) { }
    private record FileWrite(String path, String content) { }
    private record ApplyResult(int changed, List<String> errors) { }
    private record EditOutcome(boolean hasEdits, String summary, boolean changed) {
        static final EditOutcome NONE = new EditOutcome(false, "", false);
    }
    private enum EventKind { READ, WRITE, DELETE }
    private record Segment(String text, boolean code) { }

    private static final class RichText extends JEditorPane {
        RichText(String html, int width) {
            setContentType("text/html");
            setText(html);
            setEditable(false);
            setOpaque(false);
            setBorder(null);
            setFocusable(true);
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            setSize(width, Integer.MAX_VALUE);
            Dimension d = getPreferredSize();
            int height = Math.max(1, d.height);
            setPreferredSize(new Dimension(width, height));
            setMaximumSize(new Dimension(width, height));
        }
    }

    private static final class WrapArea extends JTextArea {
        WrapArea(String text, int width, Font font, Color foreground) {
            super(text == null ? "" : text);
            setFont(font);
            setForeground(foreground);
            setOpaque(false);
            setEditable(false);
            setFocusable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setBorder(null);
            setSize(width, Integer.MAX_VALUE);
            Dimension d = getPreferredSize();
            setPreferredSize(new Dimension(width, Math.max(1, d.height)));
            setMaximumSize(new Dimension(width, Math.max(1, d.height)));
        }
    }

    private static final class ChatBubble extends JPanel {
        private final Color fill;
        private final Color stroke;
        private final int radius;
        ChatBubble(Color fill, Color stroke, int radius) {
            this.fill = fill;
            this.stroke = stroke;
            this.radius = radius;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            if (stroke != null) {
                g2.setColor(stroke);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class WorkingIndicator extends JComponent {
        private final Timer timer;
        private int phase;
        WorkingIndicator() {
            setOpaque(false);
            setPreferredSize(new Dimension(20, 12));
            setMinimumSize(new Dimension(20, 12));
            setMaximumSize(new Dimension(20, 12));
            timer = new Timer(320, e -> { phase = (phase + 1) % 4; repaint(); });
            setVisible(false);
        }
        void start() { phase = 0; setVisible(true); timer.start(); }
        void stop() { timer.stop(); setVisible(false); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < 3; i++) {
                boolean active = phase == i;
                g2.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(),
                        active ? 255 : 90));
                int size = active ? 5 : 4;
                int y = active ? 3 : 4;
                g2.fillOval(2 + i * 6, y, size, size);
            }
            g2.dispose();
        }
    }
}
