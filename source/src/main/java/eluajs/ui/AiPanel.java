package eluajs.ui;

import eluajs.ai.AiClient;
import eluajs.ai.AiConfig;
import eluajs.ai.AiMessage;
import eluajs.ai.AiPricing;
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
            Keep solutions lightweight and compatible with old computers.

            You edit the project by returning commands. The app applies them automatically.
            - READ: relative/path/File.java
              (request up to 4 files; in that reply use READ lines only, no other text)
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
            - If the user asks a question only, do not use WRITE or DELETE.
            - The app applies your changes directly; the user does not need to click any buttons.
            """;

    private static final int MAX_FILE_ROUNDS = 3;
    private static final int MAX_FILES_PER_REQUEST = 4;
    private static final int MAX_FILE_CHARS = 20_000;
    private static final int MAX_TOTAL_FILE_CHARS = 60_000;
    private static final int MAX_WRITE_CHARS = 500_000;
    private static final int AI_TEXT_WIDTH = 312;
    private static final int USER_TEXT_WIDTH = 238;

    private final SyntaxEditorPane editor;
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
    private boolean busy;

    private final JComboBox<AiProvider> providerCombo = new JComboBox<>(AiProvider.values());
    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JTextArea prompt = new JTextArea();
    private final JPanel conversation = new JPanel();
    private final JScrollPane conversationScroll;
    private final JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
    private final JLabel usageLabel = Ui.label("", 9, Font.PLAIN, Theme.MUTED);
    private final WorkingIndicator working = new WorkingIndicator();
    private final JButton send = Ui.iconButton(t("Send"), "send", true);
    private final JButton undoEdits = iconOnly("undo", t("Undo AI edits"));
    private long sessionPrompt;
    private long sessionCompletion;
    private long sessionTotal;
    private double sessionCost;
    private boolean loadingProvider;

    public AiPanel(SyntaxEditorPane editor, Supplier<String> fileLabel, Supplier<Path> currentFile,
                   Path projectRoot, Runnable onClose, Runnable beforeSend, Runnable afterFilesChanged) {
        super(new BorderLayout());
        this.editor = editor;
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
        conversationScroll = new JScrollPane(conversation);
        conversationScroll.setBorder(null);
        conversationScroll.getViewport().setBackground(Theme.CONSOLE);
        conversationScroll.getVerticalScrollBar().setUnitIncrement(18);

        add(header(), BorderLayout.NORTH);
        add(conversationScroll, BorderLayout.CENTER);
        add(composer(), BorderLayout.SOUTH);
        initialise();
        installShortcuts();
        addWelcomeMessage();
    }

    private JComponent header() {
        JPanel root = new JPanel();
        root.setBackground(Theme.PANEL);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(10, 12, 8, 12));

        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel titleText = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        titleText.setOpaque(false);
        titleText.add(Ui.label("✦", 13, Font.PLAIN, Theme.ACCENT));
        titleText.add(Ui.label(t("AI Assistant"), 14, Font.BOLD, Theme.TEXT));
        titleRow.add(titleText, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        tools.setOpaque(false);
        undoEdits.setEnabled(false);
        undoEdits.addActionListener(e -> undoAiEdits());
        JButton clear = iconOnly("plus", t("New chat"));
        clear.addActionListener(e -> newChat());
        JButton close = iconOnly("close", t("Close"));
        close.addActionListener(e -> { if (onClose != null) onClose.run(); });
        tools.add(undoEdits);
        tools.add(clear);
        tools.add(close);
        titleRow.add(tools, BorderLayout.EAST);
        root.add(titleRow);
        root.add(Box.createVerticalStrut(7));

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
        JPanel engine = new JPanel(new GridLayout(1, 2, 8, 0));
        engine.setOpaque(false);
        engine.setAlignmentX(Component.LEFT_ALIGNMENT);
        engine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        engine.add(providerCombo);
        engine.add(modelCombo);
        root.add(engine);
        root.add(Box.createVerticalStrut(6));

        JPanel stats = new JPanel(new BorderLayout(8, 0));
        stats.setOpaque(false);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(working);
        left.add(status);
        stats.add(left, BorderLayout.CENTER);
        usageLabel.setToolTipText(t("Approximate cost based on public list prices."));
        stats.add(usageLabel, BorderLayout.EAST);
        root.add(stats);
        updateUsageLabel();
        return root;
    }

    private JComponent composer() {
        JPanel root = new JPanel();
        root.setBackground(Theme.PANEL);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(7, 12, 10, 12));

        prompt.setFont(Theme.font(12, Font.PLAIN));
        prompt.setBackground(Theme.PANEL_2);
        prompt.setForeground(Theme.TEXT);
        prompt.setCaretColor(Theme.ACCENT);
        prompt.setSelectionColor(Theme.SELECTION);
        prompt.setSelectedTextColor(Theme.TEXT);
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        prompt.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(prompt);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(1, 72));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        ChatBubble box = new ChatBubble(Theme.PANEL_2, Theme.BORDER, 12);
        box.setLayout(new BorderLayout());
        box.setBorder(new EmptyBorder(2, 2, 2, 2));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        box.add(scroll, BorderLayout.CENTER);
        root.add(box);
        root.add(Box.createVerticalStrut(6));

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(Ui.label(t("Enter: send  •  Shift+Enter: new line"), 9, Font.PLAIN, Theme.MUTED), BorderLayout.WEST);
        send.addActionListener(e -> send());
        bottom.add(send, BorderLayout.EAST);
        root.add(bottom);

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
        if (provider == null) return;
        boolean ready = config.hasKey(provider);
        status.setText(ready ? t("Ready.") : t("No API key configured. Open Settings and add one."));
        status.setForeground(ready ? Theme.ACCENT : Theme.WARNING);
    }

    private void newChat() {
        conversation.removeAll();
        history.clear();
        lastPrompt = "";
        lastUndo = new LinkedHashMap<>();
        undoEdits.setEnabled(false);
        working.stop();
        sessionPrompt = 0;
        sessionCompletion = 0;
        sessionTotal = 0;
        sessionCost = 0;
        updateUsageLabel();
        addWelcomeMessage();
        conversation.revalidate();
        conversation.repaint();
        status.setText(t("Ready."));
        status.setForeground(Theme.ACCENT);
        updateSendState();
    }

    private void addWelcomeMessage() {
        addBubble(false, t("Ask me to change the project. I will edit the files automatically and tell you what changed."), false);
    }

    private void send() {
        String text = prompt.getText().trim();
        if (text.isEmpty() || busy) return;
        config = AiConfig.load();
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        if (provider == null) return;
        Object model = modelCombo.isEditable() ? modelCombo.getEditor().getItem() : modelCombo.getSelectedItem();
        config.setProvider(provider);
        config.setModel(provider, model == null ? "" : String.valueOf(model));
        config.save();
        if (!config.hasKey(provider)) {
            addBubble(false, t("No API key configured. Open Settings and add one."), true);
            status.setText(t("No API key configured. Open Settings and add one."));
            status.setForeground(Theme.WARNING);
            return;
        }
        if (beforeSend != null) beforeSend.run();

        lastPrompt = text;
        addBubble(true, text, false);
        history.add(AiMessage.user(text));
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
                    String list = String.join(", ", event.paths());
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
        conversation.revalidate();
        conversation.repaint();
        scrollToBottom();
    }

    private LoopResult runAiLoop(String system, List<AiMessage> initial, boolean projectAccess, boolean allowFallback, Consumer<ProgressEvent> progress) throws Exception {
        List<AiMessage> convo = new ArrayList<>(initial);
        int totalRead = 0;
        long promptTokens = 0, completionTokens = 0, totalTokens = 0;
        double cost = 0;
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
            String contents = readRequestedFiles(requests, MAX_TOTAL_FILE_CHARS - totalRead);
            totalRead += contents.length();
            progress.accept(new ProgressEvent(EventKind.READ, requests));
            convo.add(AiMessage.user("REQUESTED FILE CONTENTS:\n" + contents));
            if (totalRead >= MAX_TOTAL_FILE_CHARS) {
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
                if (!applied.errors().isEmpty()) summary = t("AI could not apply changes: %s", String.join("; ", applied.errors()));
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
                progress.accept(new ProgressEvent(EventKind.WRITE, List.of(rel(target))));
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
                progress.accept(new ProgressEvent(EventKind.DELETE, List.of(delete)));
            } catch (Exception ex) {
                errors.add(delete + ": " + ex.getMessage());
            }
        }
        lastUndo = undo;
        return new ApplyResult(changed, errors);
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
        if (sessionTotal <= 0) {
            usageLabel.setText("0 tok");
        } else {
            usageLabel.setText(AiPricing.formatTokens(sessionTotal) + " tok • ~" + AiPricing.formatCost(sessionCost));
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
        for (String raw : answer.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (!line.regionMatches(true, 0, "READ:", 0, 5)) return List.of();
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
                "implement", "create", "write", "düzelt", "güncelle", "değiştir", "ekle", "sil", "kaldır", "oluştur", "yaz"};
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

        JPanel roleRow = new JPanel(new FlowLayout(user ? FlowLayout.RIGHT : FlowLayout.LEFT, 4, 0));
        roleRow.setOpaque(false);
        roleRow.setAlignmentX(user ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
        roleRow.add(new JLabel(Icons.icon(user ? "user" : "ai", user ? Theme.ACCENT_HOVER : Theme.BLUE, 11)));
        roleRow.add(Ui.label(user ? t("You") : t("eLuajs AI"), 9, Font.BOLD,
                user ? Theme.ACCENT_HOVER : Theme.BLUE));
        content.add(roleRow);
        content.add(Box.createVerticalStrut(4));

        Color textColor = user ? Theme.TEXT : (error ? Theme.DANGER : Theme.TEXT_SOFT);
        for (Segment segment : segments(text)) {
            if (segment.code()) {
                ChatBubble codeBox = new ChatBubble(Theme.CONSOLE, Theme.BORDER_SOFT, 8);
                codeBox.setLayout(new BorderLayout());
                codeBox.setBorder(new EmptyBorder(7, 9, 7, 9));
                codeBox.setAlignmentX(Component.LEFT_ALIGNMENT);
                WrapArea area = new WrapArea(segment.text(), width - 22, Theme.mono(11),
                        error ? Theme.DANGER : new Color(152, 195, 121));
                codeBox.add(area, BorderLayout.CENTER);
                content.add(codeBox);
                content.add(Box.createVerticalStrut(6));
            } else {
                String clean = segment.text().strip();
                if (clean.isBlank()) continue;
                RichText rich = new RichText(markdownToHtml(clean, textColor, width), width);
                rich.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(rich);
                content.add(Box.createVerticalStrut(4));
            }
        }

        ChatBubble bubble = new ChatBubble(user ? Theme.ACCENT_DARK : Theme.PANEL_2,
                user ? new Color(41, 118, 96) : Theme.BORDER_SOFT, 14);
        bubble.setLayout(new BorderLayout());
        bubble.setBorder(new EmptyBorder(9, 12, 9, 12));
        bubble.add(content, BorderLayout.CENTER);
        bubble.setMaximumSize(new Dimension(width + 24, Integer.MAX_VALUE));

        JPanel row = new JPanel(new FlowLayout(user ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.add(bubble);
        conversation.add(row);
        conversation.add(Box.createVerticalStrut(8));
        conversation.revalidate();
        conversation.repaint();
        scrollToBottom();
        return row;
    }

    private void addSystemNote(String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        JLabel label = Ui.label(text, 9, Font.ITALIC, Theme.MUTED);
        label.setBorder(new EmptyBorder(2, 8, 2, 8));
        row.add(label);
        conversation.add(row);
        conversation.add(Box.createVerticalStrut(5));
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
