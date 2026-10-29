package eluajs.ui;

import eluajs.ai.AiClient;
import eluajs.ai.AiConfig;
import eluajs.ai.AiMessage;
import eluajs.ai.AiPricing;
import eluajs.ai.ChatStore;
import eluajs.ai.AiProvider;
import eluajs.ai.AiResult;
import eluajs.ai.ProjectContext;
import eluajs.util.FileUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
    private static final String SYSTEM_PROMPT = """
            You are eLuajs AI, an autonomous Android Java coding agent inside the eLuajs IDE.
            eLuajs creates real Android Gradle projects with Java + XML and builds APKs/AABs.
            Keep solutions lightweight and compatible with the project toolchain.

            You edit the project by returning commands. The app applies them automatically.
            - READ: relative/path/File.java
              (request up to 4 files; a short explanation is allowed, the app ignores other text)
            - WRITE: relative/path/File.java
              ```java
              <COMPLETE file content>
              ```
            - DELETE: relative/path/File.java

            Rules:
            - Inspect relevant files before editing.
            - To change a file, return a WRITE block with the COMPLETE file content. Never use placeholders or patches.
            - Never claim that you changed a file unless you returned a WRITE or DELETE command.
            - Do not write outside the project or into build/, .gradle/, .git/, release/ or .eluajs/.
            - Do not modify unrelated files. Do not invent APIs.
            - After editing, reply with a short summary of what changed. Do not paste the code again.
            - If the request needs several files, return all required WRITE/DELETE blocks in the same reply. Do not stop after describing what you will do.
            - If the user asks a question only, do not use WRITE or DELETE.
            - The app shows your changes for user approval before applying them.
            """;

    private static final int MAX_FILE_ROUNDS = 5;
    private static final int MAX_FILES_PER_REQUEST = 4;
    private static final int MAX_FILE_CHARS = 20_000;
    private static final int MAX_TOTAL_FILE_CHARS = 60_000;
    private static final int MAX_HISTORY_MESSAGES = 120;
    private static final int MAX_VISIBLE_COMPONENTS = 500;
    private static final int MAX_WRITE_CHARS = 500_000;
    private static final int AI_TEXT_WIDTH = 320;
    private static final int USER_TEXT_WIDTH = 260;

    private final Supplier<SyntaxEditorPane> editorProvider;
    private final Supplier<String> fileLabel;
    private final Supplier<Path> currentFile;
    private final Path projectRoot;
    private final Runnable onClose;
    private final Runnable beforeSend;
    private final Runnable afterFilesChanged;

    private AiConfig config = AiConfig.load();
    private AiProvider lastProvider;
    private final List<AiMessage> history = new ArrayList<>();
    private Map<Path, String> lastUndo = new LinkedHashMap<>();
    private String lastPrompt = "";
    private String sessionId;
    private boolean busy;

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
    private final JButton undoEdits = iconOnly("undo", t("Undo AI edits"));
    private long sessionPrompt;
    private long sessionCompletion;
    private long sessionTotal;
    private double sessionCost;
    private boolean loadingProvider;

    public AiPanel(SyntaxEditorPane editor, Supplier<String> fileLabel, Supplier<Path> currentFile,
                   Path projectRoot, Runnable onClose, Runnable beforeSend, Runnable afterFilesChanged) {
        this(() -> editor, fileLabel, currentFile, projectRoot, onClose, beforeSend, afterFilesChanged);
    }

    public AiPanel(Supplier<SyntaxEditorPane> editorProvider, Supplier<String> fileLabel, Supplier<Path> currentFile,
                   Path projectRoot, Runnable onClose, Runnable beforeSend, Runnable afterFilesChanged) {
        super(new BorderLayout());
        this.editorProvider = editorProvider;
        this.fileLabel = fileLabel;
        this.currentFile = currentFile;
        this.projectRoot = projectRoot;
        this.onClose = onClose;
        this.beforeSend = beforeSend;
        this.afterFilesChanged = afterFilesChanged;
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
        statusRow.add(working);
        statusRow.add(status);
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

    private void updateSendState() {
        send.setEnabled(!busy && !prompt.getText().trim().isEmpty());
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
        undoEdits.setEnabled(false);
        working.stop();
        sessionPrompt = 0;
        sessionCompletion = 0;
        sessionTotal = 0;
        sessionCost = 0;
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
        undoEdits.setEnabled(false);
        sessionPrompt = 0;
        sessionCompletion = 0;
        sessionTotal = 0;
        sessionCost = 0;
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

    private void send() {
        String text = prompt.getText().trim();
        if (text.isEmpty() || busy) return;
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

        JComponent pending = addBubble(false, t("Thinking..."), false);
        List<AiMessage> messages = new ArrayList<>(history);
        boolean projectAccess = projectRoot != null && Files.isDirectory(projectRoot);
        String system = buildSystemPrompt(projectAccess);

        SwingWorker<LoopResult, ProgressEvent> worker = new SwingWorker<>() {
            @Override protected LoopResult doInBackground() throws Exception {
                return runAiLoop(system, messages, projectAccess, looksLikeChangeRequest(lastPrompt), event -> publish(event));
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
                busy = false;
                working.stop();
                updateSendState();
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
                    if (!lastUndo.isEmpty()) undoEdits.setEnabled(true);
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
                    addBubble(false, t("Request failed: %s", cause.getMessage()), true);
                    status.setText(t("Request failed: %s", cause.getMessage()));
                    status.setForeground(Theme.DANGER);
                }
                conversation.revalidate();
                conversation.repaint();
                scrollToBottom();
            }
        };
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

    private LoopResult runAiLoop(String system, List<AiMessage> initial, boolean projectAccess, boolean allowFallback, Consumer<ProgressEvent> progress) throws Exception {
        List<AiMessage> convo = new ArrayList<>(initial);
        int totalRead = 0;
        int fileBudget = config.tokenBudgetEnabled()
                ? (int) Math.min(MAX_TOTAL_FILE_CHARS, Math.max(4_000L, (long) config.tokenBudget() * 3L))
                : MAX_TOTAL_FILE_CHARS;
        long promptTokens = 0, completionTokens = 0, totalTokens = 0;
        double cost = 0;
        boolean nudged = false;
        AiProvider usedProvider = config.provider();
        String usedModel = config.activeModel();
        for (int round = 0; ; round++) {
            AiResult result = AiClient.completeDetailed(config, system, convo);
            promptTokens += result.promptTokens();
            completionTokens += result.completionTokens();
            totalTokens += result.total();
            cost += AiPricing.estimate(usedProvider, usedModel, result.promptTokens(), result.completionTokens());
            String answer = result.text();

            EditOutcome edit = projectAccess ? applyEditsIfPresent(answer, progress, allowFallback) : EditOutcome.NONE;
            if (edit.hasEdits()) {
                return new LoopResult(edit.summary(), promptTokens, completionTokens, totalTokens, cost, edit.changed());
            }

            List<String> requests = projectAccess ? parseReadRequests(answer) : List.of();
            if (requests.isEmpty()) {
                if (allowFallback && !nudged && round < MAX_FILE_ROUNDS && looksLikeChangeRequest(lastPrompt)) {
                    nudged = true;
                    convo.add(AiMessage.assistant(answer));
                    convo.add(AiMessage.user("Your previous reply only described the work. Apply the requested changes now. "
                            + "Return all required WRITE or DELETE blocks with complete file contents."));
                    continue;
                }
                return new LoopResult(answer, promptTokens, completionTokens, totalTokens, cost, false);
            }
            if (round >= MAX_FILE_ROUNDS) {
                convo.add(AiMessage.assistant(answer));
                convo.add(AiMessage.user("FILE READ LIMIT REACHED. Give the final answer now using the files already provided."));
                AiResult last = AiClient.completeDetailed(config, system, convo);
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
            String contents = readRequestedFiles(requests, fileBudget - totalRead);
            totalRead += contents.length();
            progress.accept(new ProgressEvent(EventKind.READ, requests));
            convo.add(AiMessage.user("REQUESTED FILE CONTENTS:\n" + contents));
            if (totalRead >= fileBudget) {
                convo.add(AiMessage.user("FILE READ LIMIT REACHED. Give the final answer now."));
                AiResult last = AiClient.completeDetailed(config, system, convo);
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

    private EditOutcome applyEditsIfPresent(String answer, Consumer<ProgressEvent> progress, boolean allowFallback) {
        List<FileWrite> writes = parseWrites(answer);
        List<String> deletes = parseDeletes(answer);
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
            return new EditOutcome(true, summary, applied.changed() > 0);
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
        if (!approveChanges(writes, deletes)) {
            return new ApplyResult(0, List.of(t("Changes rejected.")));
        }
        Map<Path, String> undo = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int changed = 0;
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
                changed++;
                progress.accept(new ProgressEvent(EventKind.DELETE, List.of(target.getFileName().toString())));
            } catch (Exception ex) {
                errors.add(delete + ": " + ex.getMessage());
            }
        }
        lastUndo = undo;
        return new ApplyResult(changed, errors);
    }

    private boolean approveChanges(List<FileWrite> writes, List<String> deletes) {
        if (config.fullAccess()) return true;
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
        if (changes.isEmpty()) return true;
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (SwingUtilities.isEventDispatchThread()) return AiDiffDialog.approve(owner, changes);
        final boolean[] approved = {false};
        try {
            SwingUtilities.invokeAndWait(() -> approved[0] = AiDiffDialog.approve(owner, changes));
        } catch (Exception ex) {
            return false;
        }
        return approved[0];
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
        String rest = WRITE_BLOCK.matcher(answer).replaceAll("");
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
        String rest = WRITE_BLOCK.matcher(answer).replaceAll("");
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
        if (lastUndo.isEmpty()) return;
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
    }

    private String buildSystemPrompt(boolean projectAccess) {
        StringBuilder b = new StringBuilder(SYSTEM_PROMPT);
        if (projectAccess) {
            b.append("\n\nPROJECT FILE TREE:\n").append(ProjectContext.tree(projectRoot));
        } else {
            b.append("\n\nProject editing is disabled. Return code in fenced blocks and explain where to apply it.\n");
        }
        b.append("\n\nCURRENT FILE (").append(fileLabel == null ? "unknown" : fileLabel.get()).append("):\n");
        b.append(limit(currentFileText(), 20_000));
        return b.toString();
    }

    private List<String> parseReadRequests(String answer) {
        if (answer == null) return List.of();
        List<String> out = new ArrayList<>();
        boolean inFence = false;
        for (String raw : answer.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("```")) { inFence = !inFence; continue; }
            if (inFence) continue;
            while (line.startsWith("- ") || line.startsWith("* ")) line = line.substring(2).trim();
            if (!line.regionMatches(true, 0, "READ:", 0, 5)) continue;
            String path = line.substring(5).trim();
            if (path.startsWith("`") && path.endsWith("`") && path.length() > 1) path = path.substring(1, path.length() - 1).trim();
            if (!path.isBlank() && !out.contains(path)) out.add(path);
            if (out.size() >= MAX_FILES_PER_REQUEST) break;
        }
        return out;
    }

    private String readRequestedFiles(List<String> paths, int budget) {
        StringBuilder b = new StringBuilder();
        for (String rel : paths) {
            if (b.length() >= budget) break;
            int room = Math.min(MAX_FILE_CHARS, budget - b.length());
            String content = ProjectContext.readTextFile(projectRoot, rel, room);
            b.append("\n===== ").append(rel).append(" =====\n");
            if (content == null) b.append("(File not found, not a text file, or too large.)\n");
            else b.append(content).append('\n');
        }
        return b.toString();
    }

    private static String limit(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max) + "\n... (truncated)";
    }

    private String currentFileText() {
        SyntaxEditorPane editor = editorProvider == null ? null : editorProvider.get();
        if (editor == null) return "";
        String current = editor.getText();
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
