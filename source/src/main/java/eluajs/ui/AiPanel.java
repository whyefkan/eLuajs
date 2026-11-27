package eluajs.ui;

import eluajs.ai.AiClient;
import eluajs.ai.AiErrors;
import eluajs.ai.AiConfig;
import eluajs.ai.AiMessage;
import eluajs.ai.AiModelSync;
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
    private static final Pattern FENCED_SEGMENT = Pattern.compile(
            "(?s)```[ \\t]*([A-Za-z0-9_+.#-]*)[ \\t]*\\R(.*?)```");
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
    private static final int AI_TEXT_WIDTH = 356;
    private static final int USER_TEXT_WIDTH = 286;

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

    private final PlaceholderTextArea prompt = new PlaceholderTextArea(t("Ask eLuajs AI… (/fix, /explain, /review)"));
    private final ScrollableChatPanel conversation = new ScrollableChatPanel();
    private final JScrollPane conversationScroll;
    private final CardLayout chatCards = new CardLayout();
    private final JPanel chatArea = new JPanel(chatCards);
    private final JComboBox<AiProvider> providerCombo = new JComboBox<>(AiProvider.values());
    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JLabel headerModel = Ui.label("", 9, Font.PLAIN, Theme.MUTED);
    private final StatusDot statusDot = new StatusDot();
    private final JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
    private final JLabel usageLabel = Ui.label("", 9, Font.PLAIN, Theme.MUTED);
    private final WorkingIndicator working = new WorkingIndicator();
    private final JButton send = Ui.button("", true);
    private final JButton stop = Ui.dangerButton("");
    private final JButton retry = Ui.button(t("Tekrar Dene"), false);
    private final JButton undoEdits = iconOnly("undo", t("Undo AI edits"));
    private final JButton copyAnswer = iconOnly("copy", t("Copy last answer"));
    private String lastAnswerText = "";
    private JButton pinButton;
    private long sessionPrompt;
    private long sessionCompletion;
    private long sessionTotal;
    private double sessionCost;
    private final java.util.function.Consumer<AiProvider> modelSyncListener = provider ->
            SwingUtilities.invokeLater(() -> {
                if (busy || provider == null) return;
                config = AiConfig.load();
                if (providerCombo.getSelectedItem() == provider) loadModels();
            });
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
        setPreferredSize(new Dimension(436, 1));
        setMinimumSize(new Dimension(340, 1));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER_SOFT));

        conversation.setLayout(new BoxLayout(conversation, BoxLayout.Y_AXIS));
        conversation.setBackground(Theme.CONSOLE);
        conversation.setBorder(new EmptyBorder(12, 12, 12, 12));
        conversationScroll = Ui.scroll(conversation);
        conversationScroll.setBorder(null);
        conversationScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        conversationScroll.getViewport().setBackground(Theme.CONSOLE);
        conversationScroll.getVerticalScrollBar().setUnitIncrement(18);

        chatArea.setOpaque(false);
        chatArea.add(buildWelcomePanel(), "welcome");
        chatArea.add(conversationScroll, "chat");
        add(header(), BorderLayout.NORTH);
        add(chatArea, BorderLayout.CENTER);
        add(composer(), BorderLayout.SOUTH);
        initialise();
        AiModelSync.addListener(modelSyncListener);
        installShortcuts();
        restoreLatest();
    }

    private void pinCurrent(JButton button) {
        Path file = currentFile == null ? null : currentFile.get();
        if (file == null) return;
        if (pinned.remove(file)) addSystemNote("pin", Theme.MUTED, t("Unpinned: %s", file.getFileName()));
        else if (pinned.size() < 6) {
            pinned.add(file);
            addSystemNote("pin", Theme.ACCENT, t("Pinned: %s", file.getFileName()));
        }
        refreshPinState();
    }

    public void refreshPinState() {
        if (pinButton == null) return;
        Path file = currentFile == null ? null : currentFile.get();
        boolean on = file != null && pinned.contains(file);
        pinButton.setIcon(Icons.icon("pin", on ? Theme.ACCENT : Theme.TEXT_SOFT, 14));
        pinButton.setToolTipText(on ? t("Bu dosyayı sabit context'ten çıkar") : t("Sabit context: geçerli dosyayı AI'ya ekle"));
    }

    private JComponent header() {
        JPanel root = new JPanel(new BorderLayout(8, 0));
        root.setBackground(Theme.PANEL);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(9, 12, 9, 10)));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(new IconBadge("ai", Theme.ACCENT, 28, 15));
        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        JLabel title = Ui.label(t("AI Assistant"), 13, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerModel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleStack.add(title);
        titleStack.add(headerModel);
        brand.add(titleStack);
        root.add(Ui.centerVertically(brand), BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        tools.setOpaque(false);
        undoEdits.setEnabled(false);
        undoEdits.addActionListener(e -> undoAiEdits());
        JButton history = iconOnly("history", t("Chat history"));
        history.addActionListener(e -> showHistory(history));
        pinButton = iconOnly("pin", t("Sabit context: geçerli dosyayı AI'ya ekle"));
        pinButton.addActionListener(e -> pinCurrent(pinButton));
        JButton clear = iconOnly("chat-plus", t("New chat"));
        clear.addActionListener(e -> newChat());
        JButton close = iconOnly("close", t("Close"));
        close.addActionListener(e -> { if (onClose != null) onClose.run(); });
        copyAnswer.setEnabled(false);
        copyAnswer.addActionListener(e -> copyLastAnswer());
        tools.add(pinButton);
        tools.add(history);
        tools.add(copyAnswer);
        tools.add(undoEdits);
        tools.add(clear);
        tools.add(close);
        root.add(Ui.centerVertically(tools), BorderLayout.EAST);
        return root;
    }

    private JComponent composer() {
        JPanel root = new JPanel();
        root.setBackground(Theme.PANEL);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_SOFT),
                new EmptyBorder(8, 12, 10, 12)));

        JPanel statusRow = new JPanel(new BorderLayout(6, 0));
        statusRow.setOpaque(false);
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JPanel statusLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusLeft.setOpaque(false);
        statusLeft.add(statusDot);
        statusLeft.add(working);
        statusLeft.add(status);
        statusRow.add(statusLeft, BorderLayout.CENTER);

        retry.setIcon(Icons.icon("refresh", Theme.BLUE, 11));
        retry.setIconTextGap(4);
        retry.setFont(Theme.font(9, Font.BOLD));
        retry.setBorder(new EmptyBorder(2, 7, 2, 7));
        retry.setForeground(Theme.BLUE);
        retry.setEnabled(false);
        retry.setVisible(false);
        retry.addActionListener(e -> retryRequest());
        statusRow.add(retry, BorderLayout.EAST);
        root.add(statusRow);
        root.add(Box.createVerticalStrut(6));

        prompt.setFont(Theme.font(12, Font.PLAIN));
        prompt.setOpaque(false);
        prompt.setBackground(Theme.PANEL_2);
        prompt.setForeground(Theme.TEXT);
        prompt.setCaretColor(Theme.ACCENT);
        prompt.setSelectionColor(Theme.SELECTION);
        prompt.setSelectedTextColor(Theme.TEXT);
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        prompt.setBorder(new EmptyBorder(9, 11, 7, 11));
        prompt.setToolTipText(t("Enter: send  •  Shift+Enter: new line"));
        JScrollPane scroll = Ui.scroll(prompt);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(1, 64));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        send.setIcon(Icons.icon("send", new Color(5, 36, 28), 15));
        send.setBorder(new EmptyBorder(6, 8, 6, 8));
        send.setPreferredSize(new Dimension(32, 30));
        send.setToolTipText(t("Send (Enter)"));
        send.addActionListener(e -> send());

        stop.setIcon(Icons.icon("stop", Theme.DANGER, 15));
        stop.setBorder(new EmptyBorder(6, 8, 6, 8));
        stop.setPreferredSize(new Dimension(32, 30));
        stop.setToolTipText(t("Durdur"));
        stop.setVisible(false);
        stop.addActionListener(e -> stopRequest());

        Ui.styleCombo(providerCombo);
        Ui.styleCombo(modelCombo);
        providerCombo.setFont(Theme.font(10, Font.BOLD));
        modelCombo.setFont(Theme.font(10, Font.PLAIN));
        modelCombo.setEditable(true);
        styleComboEditor(modelCombo);
        styleCombo(providerCombo);
        styleCombo(modelCombo);
        providerCombo.setToolTipText(t("Provider"));
        modelCombo.setToolTipText(t("You can type any model supported by the provider."));
        providerCombo.setPreferredSize(new Dimension(118, 28));
        modelCombo.setPreferredSize(new Dimension(150, 28));

        JButton refreshModels = iconOnly("refresh", t("Modelleri Yenile"));
        refreshModels.setPreferredSize(new Dimension(28, 28));
        refreshModels.addActionListener(e -> refreshModels(refreshModels));

        JPanel modelPair = new JPanel(new GridLayout(1, 2, 5, 0));
        modelPair.setOpaque(false);
        modelPair.add(providerCombo);
        modelPair.add(modelCombo);

        JPanel composerControls = new JPanel(new BorderLayout(6, 0));
        composerControls.setOpaque(false);
        composerControls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_SOFT),
                new EmptyBorder(6, 8, 6, 8)));
        JPanel leftControls = new JPanel(new BorderLayout(5, 0));
        leftControls.setOpaque(false);
        leftControls.add(modelPair, BorderLayout.CENTER);
        leftControls.add(refreshModels, BorderLayout.EAST);
        composerControls.add(leftControls, BorderLayout.CENTER);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightControls.setOpaque(false);
        rightControls.add(stop);
        rightControls.add(send);
        composerControls.add(rightControls, BorderLayout.EAST);

        ComposerBox composerCard = new ComposerBox(Theme.PANEL_2, 14);
        composerCard.setLayout(new BorderLayout());
        composerCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        composerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 114));
        composerCard.add(scroll, BorderLayout.CENTER);
        composerCard.add(composerControls, BorderLayout.SOUTH);
        prompt.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { composerCard.setFocused(true); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { composerCard.setFocused(false); }
        });
        root.add(composerCard);
        root.add(Box.createVerticalStrut(6));

        JPanel bottomRow = new JPanel(new BorderLayout(8, 0));
        bottomRow.setOpaque(false);
        bottomRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        bottomRow.add(Ui.label(t("Enter: send  •  Shift+Enter: new line"), 9, Font.PLAIN, Theme.MUTED), BorderLayout.WEST);
        usageLabel.setIcon(Icons.icon("bolt", Theme.MUTED, 11));
        usageLabel.setIconTextGap(3);
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

    private void setStatus(String text, Color color) {
        status.setText(text);
        status.setForeground(color);
        statusDot.setColor(color);
        statusDot.setVisible(!busy);
    }

    private void stopRequest() {
        AiClient.cancelActive();
        if (activeWorker != null) activeWorker.cancel(true);
        stop.setEnabled(false);
        working.stop();
        setStatus(t("Durduruluyor..."), Theme.MUTED);
        updateSendState();
    }

    private void retryRequest() {
        if (failedPrompt == null || failedPrompt.isBlank() || busy) return;
        prompt.setText(failedPrompt);
        send();
    }

    private void updateSendState() {
        send.setVisible(!busy);
        send.setEnabled(!busy && !prompt.getText().trim().isEmpty());
        stop.setVisible(busy);
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
        boolean prev = loadingProvider;
        loadingProvider = true;
        try {
            providerCombo.setSelectedItem(config.provider());
            loadModels();
        } finally {
            loadingProvider = prev;
        }
    }

    private void initialise() {
        lastProvider = config.provider();
        loadingProvider = true;
        try {
            providerCombo.setSelectedItem(lastProvider);
            loadModels();
        } finally {
            loadingProvider = false;
        }
        providerCombo.addActionListener(e -> {
            if (loadingProvider) return;
            String previousModel = currentComboText(modelCombo);
            AiProvider next = (AiProvider) providerCombo.getSelectedItem();
            config = AiConfig.load();
            if (lastProvider != null && !previousModel.isEmpty()) {
                config.setModel(lastProvider, previousModel);
            }
            if (next != null) config.setProvider(next);
            config.save();
            loadModels();
        });
        modelCombo.addActionListener(e -> {
            if (loadingProvider) return;
            AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
            if (provider == null) return;
            String model = currentComboText(modelCombo);
            if (model.isEmpty()) return;
            config = AiConfig.load();
            config.setProvider(provider);
            config.setModel(provider, model);
            config.save();
            updateKeyStatus();
        });
    }

    private void loadModels() {
        boolean prev = loadingProvider;
        loadingProvider = true;
        try {
            AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
            if (provider == null) return;
            lastProvider = provider;
            String activeModel = config.model(provider);
            modelCombo.removeAllItems();
            for (String model : config.availableModels(provider)) modelCombo.addItem(model);
            modelCombo.setSelectedItem(activeModel);
            modelCombo.getEditor().setItem(activeModel);
            updateKeyStatus();
        } finally {
            loadingProvider = prev;
        }
    }

    private void refreshModels(JButton button) {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        if (provider == null) return;
        config = AiConfig.load();
        AiConfig snapshot = config.copy();
        snapshot.setProvider(provider);
        button.setEnabled(false);
        setStatus(t("Modeller okunuyor..."), Theme.MUTED);
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override protected List<String> doInBackground() throws Exception {
                return AiClient.listModels(snapshot);
            }
            @Override protected void done() {
                button.setEnabled(true);
                try {
                    List<String> models = get();
                    if (models.isEmpty()) {
                        setStatus(t("No models returned by provider."), Theme.WARNING);
                        return;
                    }
                    config = AiConfig.load();
                    config.setCachedModels(provider, models);
                    boolean sameProvider = providerCombo.getSelectedItem() == provider;
                    String current = sameProvider ? currentComboText(modelCombo) : config.savedModel(provider);
                    if (current.isBlank() || !models.contains(current)) current = models.get(0);
                    config.setModel(provider, current);
                    config.save();
                    if (sameProvider) loadModels();
                    setStatus(t("%d model bulundu.", models.size()), Theme.ACCENT);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    setStatus(t("Modeller okunamadı: %s", cause.getMessage()), Theme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private static String currentComboText(JComboBox<String> combo) {
        if (combo.isEditable()) {
            Component editor = combo.getEditor().getEditorComponent();
            if (editor instanceof JTextField field) return field.getText().trim();
            Object item = combo.getEditor().getItem();
            if (item != null) return String.valueOf(item).trim();
        }
        Object selected = combo.getSelectedItem();
        return selected == null ? "" : String.valueOf(selected).trim();
    }

    private void updateKeyStatus() {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        if (provider == null) provider = config.provider();
        String activeModel = currentComboText(modelCombo);
        if (activeModel.isBlank()) activeModel = config.model(provider);
        String headerSub = provider.displayName() + (activeModel.isBlank() ? "" : " · " + activeModel);
        if (headerSub.length() > 36) headerSub = headerSub.substring(0, 33) + "…";
        headerModel.setText(headerSub);
        boolean ready = !provider.requiresKey() || config.hasKey(provider);
        setStatus(ready ? t("Ready.") : t("No API key configured. Open Settings and add one."),
                ready ? Theme.ACCENT : Theme.WARNING);
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
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        IconBadge emblem = new IconBadge("ai", Theme.ACCENT, 52, 26);
        emblem.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = Ui.label(t("eLuajs AI"), 20, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = new JLabel("<html><div style='text-align:center;width:290px;'>"
                + t("Ask me to inspect or change the project. Files are updated automatically.")
                + "</div></html>");
        subtitle.setFont(Theme.font(11, Font.PLAIN));
        subtitle.setForeground(Theme.MUTED);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(emblem);
        card.add(Box.createVerticalStrut(10));
        card.add(title);
        card.add(Box.createVerticalStrut(5));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(18));

        JPanel starters = new JPanel();
        starters.setOpaque(false);
        starters.setLayout(new BoxLayout(starters, BoxLayout.Y_AXIS));
        starters.setAlignmentX(Component.CENTER_ALIGNMENT);
        welcomeCard(starters, "eye", Theme.BLUE, t("Inspect project"), t("Project structure & key files"),
                t("Inspect the project and summarize its structure."));
        starters.add(Box.createVerticalStrut(7));
        welcomeCard(starters, "wrench", Theme.WARNING, t("Fix a problem"), t("Find and fix obvious bugs"),
                t("Find and fix any obvious errors in the project."));
        starters.add(Box.createVerticalStrut(7));
        welcomeCard(starters, "page-plus", Theme.ACCENT, t("Add a new screen"), t("Activity, XML layout & Manifest"),
                t("Create a new Android activity screen for this project."));
        starters.add(Box.createVerticalStrut(7));
        welcomeCard(starters, "shield", new Color(181, 140, 255), t("Review code"), t("Check Android compatibility"),
                "/review");
        card.add(starters);
        card.add(Box.createVerticalStrut(14));

        JLabel slashHint = Ui.label("/fix  •  /explain  •  /page  •  /review", 9, Font.PLAIN, Theme.MUTED);
        slashHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(slashHint);
        root.add(card);
        return root;
    }

    private void welcomeCard(JPanel parent, String iconName, Color accent, String title, String desc, String promptText) {
        PromptCard btn = new PromptCard(iconName, accent, title, desc);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.addActionListener(e -> {
            prompt.setText(promptText);
            prompt.requestFocusInWindow();
            updateSendState();
        });
        parent.add(btn);
    }

    private void newChat() {
        conversation.removeAll();
        history.clear();
        sessionId = null;
        lastPrompt = "";
        failedPrompt = null;
        retry.setEnabled(false);
        retry.setVisible(false);
        lastUndo = new LinkedHashMap<>();
        lastCheckpoint = null;
        lastAnswerText = "";
        copyAnswer.setEnabled(false);
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
        setStatus(t("Ready."), Theme.ACCENT);
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
        failedPrompt = null;
        retry.setEnabled(false);
        retry.setVisible(false);
        for (AiMessage message : session.messages()) {
            boolean user = "user".equalsIgnoreCase(message.role());
            addBubble(user, message.content(), false);
            history.add(message);
            if (user) lastPrompt = message.content();
            else lastAnswerText = message.content();
        }
        lastUndo = new LinkedHashMap<>();
        lastCheckpoint = null;
        copyAnswer.setEnabled(!lastAnswerText.isBlank());
        undoEdits.setEnabled(false);
        sessionPrompt = 0;
        sessionCompletion = 0;
        sessionTotal = 0;
        sessionCost = 0;
        contextTokensEstimate = 0;
        updateUsageLabel();
        setStatus(t("Restored chat: %s", session.title()), Theme.MUTED);
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
        String chosenModel = currentComboText(modelCombo);
        config.setProvider(provider);
        config.setModel(provider, chosenModel);
        config.save();
        AiConfig requestConfig = config.copy();
        if (provider.requiresKey() && !config.hasKey(provider)) {
            chatCards.show(chatArea, "chat");
            addBubble(false, t("No API key configured. Open Settings and add one."), true);
            setStatus(t("No API key configured. Open Settings and add one."), Theme.WARNING);
            return;
        }
        if (beforeSend != null) beforeSend.run();

        chatCards.show(chatArea, "chat");
        lastPrompt = text;
        failedPrompt = null;
        retry.setEnabled(false);
        retry.setVisible(false);
        stop.setEnabled(true);
        addBubble(true, text, false);
        history.add(AiMessage.user(text));
        while (history.size() > MAX_HISTORY_MESSAGES) history.remove(0);
        ensureSession();
        saveSession();
        prompt.setText("");
        busy = true;
        updateSendState();
        working.start();
        setStatus(t("Thinking..."), Theme.MUTED);

        JTextArea streamArea = new JTextArea();
        JComponent pending = addStreamingBubble(streamArea);
        boolean projectAccess = projectRoot != null && Files.isDirectory(projectRoot);
        String system = buildSystemPrompt(projectAccess);
        List<AiMessage> messages = fitContext(system, requestHistory());
        contextTokensEstimate = Math.max(0, (system.length() + chars("", messages)) / 4L);
        updateUsageLabel();

        SwingWorker<LoopResult, ProgressEvent> worker = new SwingWorker<>() {
            @Override protected LoopResult doInBackground() throws Exception {
                return runAiLoop(requestConfig, system, messages, projectAccess, looksLikeChangeRequest(lastPrompt), event -> publish(event),
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
                            setStatus(t("Reading: %s", list), Theme.BLUE);
                            addSystemNote("eye", Theme.BLUE, t("Reading: %s", list));
                        }
                        case WRITE -> {
                            setStatus(t("Writing: %s", list), Theme.ACCENT);
                            addSystemNote("edit", Theme.ACCENT, t("Updated: %s", list));
                        }
                        case DELETE -> {
                            setStatus(t("Deleting: %s", list), Theme.DANGER);
                            addSystemNote("trash", Theme.DANGER, t("Deleted: %s", list));
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
                    setStatus(t("Durduruldu"), Theme.MUTED);
                    conversation.revalidate();
                    conversation.repaint();
                    scrollToBottom();
                    return;
                }
                try {
                    LoopResult result = get();
                    lastAnswerText = result.text();
                    copyAnswer.setEnabled(!lastAnswerText.isBlank());
                    addBubble(false, result.text(), false);
                    history.add(AiMessage.assistant(result.text()));
                    while (history.size() > MAX_HISTORY_MESSAGES) history.remove(0);
                    saveSession();
                    sessionPrompt += result.promptTokens();
                    sessionCompletion += result.completionTokens();
                    sessionTotal += result.totalTokens();
                    sessionCost += result.cost();
                    if (result.totalTokens() > 0) {
                        addSystemNote("bolt", Theme.MUTED, t("%s in / %s out • ~%s",
                                AiPricing.formatTokens(result.promptTokens()),
                                AiPricing.formatTokens(result.completionTokens()),
                                AiPricing.formatCost(result.cost())));
                    }
                    if (!lastUndo.isEmpty() || lastCheckpoint != null) undoEdits.setEnabled(true);
                    if (result.filesChanged() && afterFilesChanged != null) {
                        SwingUtilities.invokeLater(afterFilesChanged);
                    } else if (!result.filesChanged() && looksLikeChangeRequest(lastPrompt)) {
                        addSystemNote("about", Theme.MUTED, t("No files were changed."));
                    }
                    updateUsageLabel();
                    setStatus(t("Ready."), Theme.ACCENT);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (!history.isEmpty() && "user".equals(history.get(history.size() - 1).role())) {
                        history.remove(history.size() - 1);
                    }
                    saveSession();
                    failedPrompt = lastPrompt;
                    boolean canRetry = failedPrompt != null && !failedPrompt.isBlank();
                    retry.setEnabled(canRetry);
                    retry.setVisible(canRetry);
                    String friendly = AiErrors.friendly(cause);
                    addBubble(false, t("Request failed: %s", friendly), true);
                    setStatus(t("Request failed: %s", friendly), Theme.DANGER);
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

    private LoopResult runAiLoop(AiConfig requestConfig, String system, List<AiMessage> initial, boolean projectAccess, boolean allowFallback,
                                 Consumer<ProgressEvent> progress, Runnable clearStream, Consumer<String> stream) throws Exception {
        List<AiMessage> convo = new ArrayList<>(initial);
        int totalRead = 0;
        int validationRounds = 0;
        int fileBudget = requestConfig.tokenBudgetEnabled()
                ? (int) Math.min(MAX_TOTAL_FILE_CHARS, Math.max(4_000L, (long) requestConfig.tokenBudget() * 3L))
                : MAX_TOTAL_FILE_CHARS;
        long promptTokens = 0, completionTokens = 0, totalTokens = 0;
        double cost = 0;
        boolean nudged = false;
        AiProvider usedProvider = requestConfig.provider();
        String usedModel = requestConfig.activeModel();
        for (int round = 0; ; round++) {
            clearStream.run();
            AiResult result = AiClient.completeStreaming(requestConfig, system, convo, stream);
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
                boolean checkpointTaken = false;
                for (PageRequest page : pages) {
                    if (!confirmPage(page)) {
                        summary.append(t("Page creation cancelled.")).append('\n');
                        continue;
                    }
                    if (!checkpointTaken) {
                        checkpointTaken = true;
                        try { lastCheckpoint = ProjectCheckpoints.create(projectRoot, "ai-before-page"); }
                        catch (Exception ignored) { }
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
                AiResult last = AiClient.completeStreaming(requestConfig, system, convo, stream);
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
                AiResult last = AiClient.completeStreaming(requestConfig, system, convo, stream);
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

    private void copyLastAnswer() {
        if (lastAnswerText.isBlank()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(lastAnswerText), null);
        Ui.toast(SwingUtilities.getWindowAncestor(this), t("Copied."));
    }

    private void undoAiEdits() {
        if (lastUndo.isEmpty()) {
            if (lastCheckpoint != null && projectRoot != null) {
                try {
                    ProjectCheckpoints.restore(projectRoot, lastCheckpoint);
                    lastCheckpoint = null;
                    undoEdits.setEnabled(false);
                    addSystemNote("undo", Theme.ACCENT, t("AI edits undone."));
                    setStatus(t("AI edits undone."), Theme.ACCENT);
                    if (afterFilesChanged != null) afterFilesChanged.run();
                } catch (Exception ex) {
                    setStatus(t("Undo failed: %s", ex.getMessage()), Theme.DANGER);
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
            addSystemNote("undo", Theme.ACCENT, t("AI edits undone."));
            setStatus(t("AI edits undone."), Theme.ACCENT);
        } else {
            setStatus(t("Undo failed: %s", String.join("; ", errors)), Theme.DANGER);
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

    public void invalidateContext() { invalidateIndex(); }

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

    private int bubbleContentWidth(boolean user) {
        int vp = conversationScroll != null ? conversationScroll.getViewport().getWidth() : 0;
        int avail = vp > 220 ? vp - 36 : 396;
        return user ? Math.max(200, Math.min(340, (int) (avail * 0.78)))
                : Math.max(250, avail - 26);
    }

    private JComponent addStreamingBubble(JTextArea area) {
        area.setEditable(false);
        area.setOpaque(false);
        area.setForeground(Theme.TEXT_SOFT);
        area.setFont(Theme.font(12, Font.PLAIN));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(2, 0, 0, 0));

        ChatBubble card = new ChatBubble(Theme.PANEL, Theme.BORDER_SOFT, 12);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel head = new JPanel(new BorderLayout(6, 0));
        head.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(new IconBadge("ai", Theme.ACCENT, 20, 11));
        left.add(Ui.label("eLuajs AI", 10, Font.BOLD, Theme.TEXT));
        String activeModel = currentComboText(modelCombo);
        if (!activeModel.isBlank()) {
            String shortModel = activeModel.length() > 26 ? activeModel.substring(0, 23) + "…" : activeModel;
            left.add(Ui.label(shortModel, 9, Font.PLAIN, Theme.MUTED));
        }
        WorkingIndicator liveDots = new WorkingIndicator();
        liveDots.start();
        head.add(left, BorderLayout.WEST);
        head.add(liveDots, BorderLayout.EAST);

        card.add(head, BorderLayout.NORTH);
        card.add(area, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        wrap.add(card, BorderLayout.CENTER);
        conversation.add(wrap);
        conversation.add(Box.createVerticalStrut(10));
        trimConversation();
        conversation.revalidate();
        conversation.repaint();
        scrollToBottom();
        return wrap;
    }

    private JComponent addBubble(boolean user, String text, boolean error) {
        if (text == null) text = "";
        int width = bubbleContentWidth(user);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        Color textColor = user ? Theme.TEXT : (error ? Theme.DANGER : Theme.TEXT_SOFT);
        for (Segment segment : segments(text)) {
            if (segment.code()) {
                content.add(buildCodeBlock(segment.lang(), segment.text(), width - 8, error));
                content.add(Box.createVerticalStrut(6));
            } else {
                String raw = segment.text().strip();
                if (raw.isBlank()) continue;
                if (user || error) {
                    WrapArea area = new WrapArea(cleanInlineMarkdown(raw), width, Theme.font(12, Font.PLAIN), textColor);
                    area.setAlignmentX(Component.LEFT_ALIGNMENT);
                    content.add(area);
                } else {
                    RichText rich = new RichText(markdownToHtml(raw, textColor, width), width);
                    rich.setAlignmentX(Component.LEFT_ALIGNMENT);
                    content.add(rich);
                }
                content.add(Box.createVerticalStrut(4));
            }
        }

        JPanel row;
        if (user) {
            row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            Color userFill = Theme.isLight()
                    ? new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(), 34)
                    : Theme.ACCENT_DARK;
            Color userStroke = new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(),
                    Theme.isLight() ? 105 : 85);
            ChatBubble bubble = new ChatBubble(userFill, userStroke, 14);
            bubble.setLayout(new BorderLayout());
            bubble.setBorder(new EmptyBorder(9, 13, 9, 13));
            bubble.add(content, BorderLayout.CENTER);
            bubble.setMaximumSize(new Dimension(width + 28, Integer.MAX_VALUE));
            row.add(bubble);
        } else {
            row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            Color fill = error
                    ? (Theme.isLight() ? new Color(255, 241, 242) : new Color(40, 20, 24))
                    : Theme.PANEL;
            Color stroke = error
                    ? new Color(Theme.DANGER.getRed(), Theme.DANGER.getGreen(), Theme.DANGER.getBlue(), 110)
                    : Theme.BORDER_SOFT;
            ChatBubble card = new ChatBubble(fill, stroke, 12);
            card.setLayout(new BorderLayout(0, 6));
            card.setBorder(new EmptyBorder(10, 12, 10, 12));

            JPanel head = new JPanel(new BorderLayout(6, 0));
            head.setOpaque(false);
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            left.setOpaque(false);
            left.add(new IconBadge(error ? "alert" : "ai", error ? Theme.DANGER : Theme.ACCENT, 20, 11));
            left.add(Ui.label(error ? t("Error") : "eLuajs AI", 10, Font.BOLD, error ? Theme.DANGER : Theme.TEXT));
            if (!error) {
                String activeModel = currentComboText(modelCombo);
                if (!activeModel.isBlank()) {
                    String shortModel = activeModel.length() > 24 ? activeModel.substring(0, 21) + "…" : activeModel;
                    left.add(Ui.label(shortModel, 9, Font.PLAIN, Theme.MUTED));
                }
            }
            head.add(left, BorderLayout.WEST);

            if (!error && !text.isBlank()) {
                final String copyValue = text;
                JButton copyBtn = new JButton(Icons.icon("copy", Theme.MUTED, 12));
                copyBtn.setToolTipText(t("Copy"));
                copyBtn.setFocusPainted(false);
                copyBtn.setBorderPainted(false);
                copyBtn.setContentAreaFilled(false);
                copyBtn.setOpaque(false);
                copyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                copyBtn.setBorder(new EmptyBorder(2, 4, 2, 4));
                copyBtn.addActionListener(e -> {
                    copyToClipboard(copyValue);
                    copyBtn.setIcon(Icons.icon("check", Theme.ACCENT, 12));
                    Timer reset = new Timer(1400, ev -> copyBtn.setIcon(Icons.icon("copy", Theme.MUTED, 12)));
                    reset.setRepeats(false);
                    reset.start();
                });
                head.add(copyBtn, BorderLayout.EAST);
            }

            card.add(head, BorderLayout.NORTH);
            card.add(content, BorderLayout.CENTER);
            row.add(card, BorderLayout.CENTER);
        }

        Dimension rowPref = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(1, rowPref.height + 4)));
        conversation.add(row);
        conversation.add(Box.createVerticalStrut(10));
        trimConversation();
        conversation.revalidate();
        conversation.repaint();
        scrollToBottom();
        return row;
    }

    private JComponent buildCodeBlock(String lang, String code, int width, boolean error) {
        String label = (lang == null || lang.isBlank()) ? "code" : lang.trim();
        ChatBubble box = new ChatBubble(Theme.BG, Theme.BORDER, 9);
        box.setLayout(new BorderLayout());
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBackground(Theme.PANEL_2);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(4, 9, 4, 7)));
        JLabel langLabel = Ui.label(label, 9, Font.BOLD, Theme.MUTED);
        langLabel.setIcon(Icons.icon("code", Theme.ACCENT, 11));
        langLabel.setIconTextGap(5);
        bar.add(langLabel, BorderLayout.WEST);

        JButton copyCode = new JButton(t("Copy"));
        copyCode.setIcon(Icons.icon("copy", Theme.MUTED, 11));
        copyCode.setIconTextGap(4);
        copyCode.setFont(Theme.font(9, Font.BOLD));
        copyCode.setForeground(Theme.MUTED);
        copyCode.setFocusPainted(false);
        copyCode.setBorderPainted(false);
        copyCode.setContentAreaFilled(false);
        copyCode.setOpaque(false);
        copyCode.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyCode.setBorder(new EmptyBorder(1, 5, 1, 5));
        copyCode.addActionListener(e -> {
            copyToClipboard(code);
            copyCode.setText(t("Copied."));
            copyCode.setForeground(Theme.ACCENT);
            copyCode.setIcon(Icons.icon("check", Theme.ACCENT, 11));
            Timer reset = new Timer(1400, ev -> {
                copyCode.setText(t("Copy"));
                copyCode.setForeground(Theme.MUTED);
                copyCode.setIcon(Icons.icon("copy", Theme.MUTED, 11));
            });
            reset.setRepeats(false);
            reset.start();
        });
        bar.add(copyCode, BorderLayout.EAST);

        JPanel codeWrap = new JPanel(new BorderLayout());
        codeWrap.setOpaque(false);
        codeWrap.setBorder(new EmptyBorder(8, 10, 8, 10));
        Color codeFg = error ? Theme.DANGER
                : (Theme.isLight() ? new Color(22, 101, 52) : new Color(158, 212, 152));
        WrapArea area = new WrapArea(code == null ? "" : code.stripTrailing(), Math.max(180, width - 20),
                Theme.mono(11), codeFg);
        codeWrap.add(area, BorderLayout.CENTER);

        box.add(bar, BorderLayout.NORTH);
        box.add(codeWrap, BorderLayout.CENTER);
        return box;
    }

    private static void copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text == null ? "" : text), null);
        } catch (Exception ignored) { }
    }

    private static String shortList(List<String> paths) {
        String list = String.join(", ", paths);
        return list.length() > 70 ? list.substring(0, 67) + "…" : list;
    }

    private void addSystemNote(String text) {
        addSystemNote("about", Theme.MUTED, text);
    }

    private void addSystemNote(String iconName, Color accent, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        ChatBubble chip = new ChatBubble(Theme.PANEL_2, Theme.BORDER_SOFT, 12);
        chip.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
        chip.setBorder(new EmptyBorder(4, 9, 4, 10));
        chip.add(new JLabel(Icons.icon(iconName, accent, 11)));
        chip.add(Ui.label(text, 9, Font.PLAIN, Theme.TEXT_SOFT));
        row.add(chip);
        Dimension pref = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(1, pref.height)));
        conversation.add(row);
        conversation.add(Box.createVerticalStrut(6));
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
        Matcher m = FENCED_SEGMENT.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.add(new Segment(text.substring(last, m.start()), false, ""));
            out.add(new Segment(m.group(2), true, m.group(1)));
            last = m.end();
        }
        if (last < text.length()) out.add(new Segment(text.substring(last), false, ""));
        if (out.isEmpty()) out.add(new Segment(text, false, ""));
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
        String codeColor = Theme.isLight() ? "#0f766e" : "#7ee0b8";
        String headingColor = String.format("#%02x%02x%02x", Theme.TEXT.getRed(), Theme.TEXT.getGreen(), Theme.TEXT.getBlue());
        List<String> codeSpans = new ArrayList<>();
        Matcher codeMatcher = Pattern.compile("`([^`]+)`").matcher(html);
        StringBuffer sb = new StringBuffer();
        while (codeMatcher.find()) {
            String token = "\u0000CODE" + codeSpans.size() + "\u0000";
            codeSpans.add("<font face=\"Monospaced\" color=\"" + codeColor + "\">" + codeMatcher.group(1) + "</font>");
            codeMatcher.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        codeMatcher.appendTail(sb);
        html = sb.toString();
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("~~(.+?)~~", "<strike>$1</strike>");
        html = html.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<i>$1</i>");
        html = html.replaceAll("(?m)^\\s*#{1,6}\\s+(.+)$", "<b style='color:" + headingColor + "'>$1</b>");
        html = html.replaceAll("(?m)^\\s*[-*]\\s+", "• ");
        html = html.replace("**", "").replace("~~", "");
        html = html.replace("\r\n", "\n").replace("\n", "<br>");
        for (int i = 0; i < codeSpans.size(); i++) {
            html = html.replace("\u0000CODE" + i + "\u0000", codeSpans.get(i));
        }
        String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return "<html><body style='margin:0;padding:0;color:" + hex + ";'>" + html + "</body></html>";
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
    private record Segment(String text, boolean code, String lang) { }

    private static final class ScrollableChatPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(40, visibleRect.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static final class PlaceholderTextArea extends JTextArea {
        private final String placeholder;
        PlaceholderTextArea(String placeholder) { this.placeholder = placeholder; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                g2.setColor(Theme.MUTED);
                Insets in = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, in.left + 1, in.top + fm.getAscent());
                g2.dispose();
            }
        }
    }

    private static final class ComposerBox extends JPanel {
        private final Color fill;
        private final int radius;
        private boolean focused;
        ComposerBox(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
        }
        void setFocused(boolean focused) { this.focused = focused; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.setColor(focused ? Theme.ACCENT : Theme.BORDER);
            g2.setStroke(new BasicStroke(focused ? 1.4f : 1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class IconBadge extends JComponent {
        private final String iconName;
        private final Color accent;
        private final int boxSize;
        private final int iconSize;
        IconBadge(String iconName, Color accent, int boxSize, int iconSize) {
            this.iconName = iconName;
            this.accent = accent;
            this.boxSize = boxSize;
            this.iconSize = iconSize;
            setOpaque(false);
            Dimension d = new Dimension(boxSize, boxSize);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int r = Math.max(8, boxSize / 3);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 34));
            g2.fillRoundRect(0, 0, boxSize, boxSize, r, r);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 95));
            g2.drawRoundRect(0, 0, boxSize - 1, boxSize - 1, r, r);
            Icons.draw(g2, iconName, (boxSize - iconSize) / 2, (boxSize - iconSize) / 2, iconSize, accent);
            g2.dispose();
        }
    }

    private static final class StatusDot extends JComponent {
        private Color color = Theme.ACCENT;
        StatusDot() {
            setOpaque(false);
            Dimension d = new Dimension(8, 8);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }
        void setColor(Color c) { this.color = c == null ? Theme.MUTED : c; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(1, 1, 6, 6);
            g2.dispose();
        }
    }

    private static final class PromptCard extends JButton {
        private final String iconName;
        private final Color accent;
        PromptCard(String iconName, Color accent, String title, String subtitle) {
            this.iconName = iconName;
            this.accent = accent;
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(9, 11, 9, 11));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(326, 48));
            setMaximumSize(new Dimension(346, 48));

            add(new IconBadge(iconName, accent, 30, 15), BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel tLabel = Ui.label(title, 11, Font.BOLD, Theme.TEXT);
            tLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel sLabel = Ui.label(subtitle, 9, Font.PLAIN, Theme.MUTED);
            sLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(tLabel);
            text.add(Box.createVerticalStrut(1));
            text.add(sLabel);
            add(text, BorderLayout.CENTER);
            add(new JLabel(Icons.icon("arrow-right", Theme.MUTED, 13)), BorderLayout.EAST);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();
            g2.setColor(pressed ? Theme.PANEL_HOVER : (hover ? Theme.PANEL_2 : Theme.PANEL));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.setColor(hover ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120) : Theme.BORDER_SOFT);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class RichText extends JEditorPane {
        RichText(String html, int width) {
            setContentType("text/html");
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            setFont(Theme.font(12, Font.PLAIN));
            setEditable(false);
            setOpaque(false);
            setBorder(null);
            setFocusable(true);
            setSelectionColor(Theme.SELECTION);
            setSelectedTextColor(Theme.TEXT);
            setText(html);
            setSize(new Dimension(width, Short.MAX_VALUE));
            Dimension d = getPreferredSize();
            int height = Math.max(1, d.height + 2);
            setPreferredSize(new Dimension(width, height));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        }
    }

    private static final class WrapArea extends JTextArea {
        WrapArea(String text, int width, Font font, Color foreground) {
            super(text == null ? "" : text);
            setFont(font);
            setForeground(foreground);
            setOpaque(false);
            setEditable(false);
            setFocusable(true);
            setSelectionColor(Theme.SELECTION);
            setSelectedTextColor(Theme.TEXT);
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
        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }
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
