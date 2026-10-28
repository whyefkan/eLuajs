package eluajs.ui;

import eluajs.model.ProjectMeta;
import eluajs.project.ProjectValidator;
import eluajs.project.ProjectMigrator;
import eluajs.project.ProjectActivityManager;
import eluajs.util.FileUtil;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class EditorFrame extends JFrame {
    private final ProjectMeta meta;
    private final Runnable onCloseRefresh;
    private final JTree tree = new JTree();
    private final DefaultListModel<ProjectActivityManager.ActivityInfo> pagesModel = new DefaultListModel<>();
    private final JList<ProjectActivityManager.ActivityInfo> pagesList = new JList<>(pagesModel);
    private final SyntaxEditorPane editor = new SyntaxEditorPane();
    private final JTextArea console = new JTextArea();
    private final JLabel currentFile = Ui.label(t("Dosya seçin"), 12, Font.PLAIN, Theme.TEXT_SOFT);
    private final JLabel breadcrumb = Ui.label("", 11, Font.PLAIN, Theme.MUTED);
    private final JLabel tabDot = Ui.label("", 9, Font.PLAIN, Theme.ACCENT);
    private final JLabel editorStatus = Ui.label(t("Satır %d, Sütun %d", 1, 1), 10, Font.PLAIN, Theme.STATUS_TEXT);
    private final JLabel sidebarTitle = Ui.sectionLabel(t("DOSYALAR"));
    private final CardLayout sidebarCards = new CardLayout();
    private final JPanel sidebarBody = new JPanel(sidebarCards);
    private Ui.ActivityButton filesActivity;
    private Ui.ActivityButton pagesActivity;
    private Ui.ActivityButton aiActivity;
    private AiPanel aiPanel;
    private ProjectSearchDialog searchDialog;
    private Path openedFile;
    private boolean dirty;
    private final AtomicInteger treeReloadGeneration = new AtomicInteger();

    public EditorFrame(ProjectMeta meta, Runnable onCloseRefresh) {
        super("eLuajs " + HubFrame.VERSION + " — " + meta.appName());
        WindowChrome.install(this);
        this.meta = meta;
        this.onCloseRefresh = onCloseRefresh;
        this.aiPanel = new AiPanel(editor,
                () -> openedFile == null ? t("Dosya seçin") : meta.rootDir().relativize(openedFile).toString(),
                () -> openedFile,
                meta.rootDir(),
                this::hideAiPanel);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setIconImage(Ui.appIcon());
        setSize(1320, 820);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);
        installShortcuts();
        ProjectMigrator.Result migration = ProjectMigrator.migrate(meta.rootDir());
        reloadTree();
        openMainActivity();
        if (migration.changed()) appendConsole(migration.message());
        appendConsole(t("eLuajs %s hazır — hafif VS Code tarzı çalışma alanı. Kaydet, doğrula, debug APK veya imzalı Play Store AAB al.", HubFrame.VERSION));
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { if (onCloseRefresh != null) onCloseRefresh.run(); }
            @Override public void windowClosing(java.awt.event.WindowEvent e) { confirmClose(); }
        });
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 58));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(0, 16, 0, 16)));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        JButton back = compact("←", t("Projeler ekranına dön"));
        back.setFont(Theme.font(13, Font.BOLD));
        back.setBorder(new EmptyBorder(5, 10, 5, 10));
        back.addActionListener(e -> confirmClose());
        brand.add(back);

        JLabel icon = new JLabel(new ImageIcon(Icons.projectIcon(meta.appName(), meta.packageName(), 26)));
        brand.add(icon);
        JPanel name = new JPanel();
        name.setOpaque(false);
        name.setLayout(new BoxLayout(name, BoxLayout.Y_AXIS));
        name.add(Ui.label(meta.appName(), 14, Font.BOLD, Theme.TEXT));
        name.add(Box.createVerticalStrut(5));
        name.add(Ui.label(meta.packageName(), 10, Font.PLAIN, Theme.MUTED));
        brand.add(name);
        brand.add(Ui.badge("v" + HubFrame.VERSION, Theme.ACCENT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);

        JPanel primary = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        primary.setOpaque(false);
        JButton save = Ui.iconButton(t("Kaydet"), "save", false);
        save.setToolTipText(t("Kaydet (Ctrl+S)"));
        save.addActionListener(e -> saveCurrent());
        JButton validate = Ui.iconButton(t("Doğrula"), "check", false);
        validate.addActionListener(e -> validateProject());
        JButton build = Ui.iconButton(t("Derle / APK + AAB"), "run", true);
        build.addActionListener(e -> {
            saveCurrentSilently();
            new BuildDialog(this, meta.rootDir(), this::openFileAtLine).setVisible(true);
        });
        primary.add(save);
        primary.add(validate);
        primary.add(build);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(primary);
        right.add(WindowChrome.controls(this));
        bar.add(Ui.centerVertically(right), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JComponent body() {
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG);
        body.add(activityBar(), BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.BG);
        center.add(sidebar(), BorderLayout.WEST);
        center.add(workspace(), BorderLayout.CENTER);
        aiPanel.setVisible(false);
        center.add(aiPanel, BorderLayout.EAST);
        body.add(center, BorderLayout.CENTER);
        return body;
    }

    private JComponent activityBar() {
        JPanel bar = Ui.activityBar();

        filesActivity = Ui.activityButton("files", t("Dosyalar"), () -> showSidebar("files"));
        filesActivity.setActive(true);
        bar.add(filesActivity);
        bar.add(Box.createVerticalStrut(4));

        pagesActivity = Ui.activityButton("pages", t("Sayfalar"), () -> showSidebar("pages"));
        bar.add(pagesActivity);
        bar.add(Box.createVerticalStrut(14));

        bar.add(Ui.activityButton("search", t("Projede ara (Ctrl+Shift+F)"), this::showProjectSearch));
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("assets", t("Assets / görsel ve ses ekle"), this::openAssets));
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("library", t("Gradle kütüphaneleri"), this::openLibraries));
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("permissions", t("Android izinleri"), this::openPermissions));
        bar.add(Box.createVerticalStrut(14));
        aiActivity = Ui.activityButton("ai", t("Yapay Zeka Asistanı (Ctrl+Alt+A)"), this::toggleAiPanel);
        bar.add(aiActivity);
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("settings", t("Ayarlar"), this::openSettings));

        bar.add(Box.createVerticalGlue());
        bar.add(Ui.activityButton("home", t("Projeler ekranına dön"), this::confirmClose));
        return bar;
    }

    private JComponent sidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.PANEL);
        side.setPreferredSize(new Dimension(292, 1));
        side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER_SOFT));

        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setBackground(Theme.PANEL);
        head.setBorder(new EmptyBorder(14, 16, 10, 16));
        head.add(sidebarTitle, BorderLayout.WEST);
        side.add(head, BorderLayout.NORTH);

        JPanel actions = new JPanel(new GridLayout(2, 2, 9, 9));
        actions.setBackground(Theme.PANEL);
        actions.setBorder(new EmptyBorder(0, 14, 12, 14));
        JButton addPage = compact("＋ " + t("Sayfa"), t("Yeni Android Activity / ekran oluştur"));
        addPage.addActionListener(e -> newActivity());
        JButton delPage = compact("− " + t("Sayfa"), t("Ek Android Activity / sayfayı sil"));
        delPage.setForeground(Theme.DANGER);
        delPage.addActionListener(e -> deleteActivity());
        JButton addClass = compact("＋ " + t("Sınıf"), t("Activity olmayan normal Java sınıfı oluştur (Ctrl+Shift+N)"));
        addClass.addActionListener(e -> newJavaClass());
        JButton refreshTree = compact("⟳ " + t("Yenile"), t("Dosya ağacını yenile"));
        refreshTree.addActionListener(e -> reloadTree());
        actions.add(addPage);
        actions.add(delPage);
        actions.add(addClass);
        actions.add(refreshTree);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Theme.PANEL);
        top.add(head, BorderLayout.NORTH);
        top.add(actions, BorderLayout.SOUTH);
        side.add(top, BorderLayout.NORTH);

        sidebarBody.setBackground(Theme.PANEL);
        sidebarBody.add(filesPanel(), "files");
        sidebarBody.add(pagesPanel(), "pages");
        side.add(sidebarBody, BorderLayout.CENTER);
        showSidebar("files");
        return side;
    }

    private JComponent filesPanel() {
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setBackground(Theme.PANEL);
        tree.setForeground(Theme.TEXT);
        tree.setFont(Theme.font(13, Font.PLAIN));
        tree.setRowHeight(27);
        tree.setCellRenderer(new DarkTreeRenderer());
        tree.setBorder(new EmptyBorder(8, 10, 12, 10));
        tree.addTreeSelectionListener(this::treeSelection);
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.getViewport().setBackground(Theme.PANEL);
        treeScroll.setBorder(null);
        return treeScroll;
    }

    private void showSidebar(String view) {
        sidebarCards.show(sidebarBody, view);
        boolean files = "files".equals(view);
        sidebarTitle.setText(files ? t("DOSYALAR") : t("SAYFALAR"));
        if (filesActivity != null) filesActivity.setActive(files);
        if (pagesActivity != null) pagesActivity.setActive(!files);
    }

    private JComponent workspace() {
        editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { markDirty(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { markDirty(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        editor.addCaretListener(e -> updateEditorStatus());

        JScrollPane editorScroll = new JScrollPane(editor);
        editorScroll.getViewport().setBackground(Theme.EDITOR);
        editorScroll.setRowHeaderView(new LineNumberView(editor));
        if (editorScroll.getRowHeader() != null) editorScroll.getRowHeader().setBackground(Theme.EDITOR);
        editorScroll.setBorder(null);

        JPanel tabBar = new JPanel(new BorderLayout());
        tabBar.setBackground(Theme.TAB_BAR);
        tabBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT));
        tabBar.setPreferredSize(new Dimension(1, 38));

        JPanel activeTab = new JPanel();
        activeTab.setBackground(Theme.TAB_ACTIVE);
        activeTab.setLayout(new BoxLayout(activeTab, BoxLayout.X_AXIS));
        activeTab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, Theme.ACCENT),
                new EmptyBorder(0, 14, 0, 18)));
        tabDot.setAlignmentY(Component.CENTER_ALIGNMENT);
        currentFile.setAlignmentY(Component.CENTER_ALIGNMENT);
        activeTab.add(tabDot);
        activeTab.add(Box.createHorizontalStrut(7));
        activeTab.add(currentFile);
        tabBar.add(activeTab, BorderLayout.WEST);

        JPanel breadcrumbBar = new JPanel(new BorderLayout());
        breadcrumbBar.setBackground(Theme.EDITOR);
        breadcrumbBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(4, 15, 4, 14)));
        breadcrumbBar.add(breadcrumb, BorderLayout.WEST);

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBackground(Theme.EDITOR);
        editorPanel.add(tabBar, BorderLayout.NORTH);
        JPanel editorHead = new JPanel(new BorderLayout());
        editorHead.setBackground(Theme.EDITOR);
        editorHead.add(breadcrumbBar, BorderLayout.NORTH);
        editorHead.add(editorScroll, BorderLayout.CENTER);
        editorPanel.add(editorHead, BorderLayout.CENTER);

        console.setEditable(false);
        console.setFont(Theme.mono(12));
        console.setBackground(Theme.CONSOLE);
        console.setForeground(Theme.MUTED);
        console.setCaretColor(Theme.ACCENT);
        console.setBorder(new EmptyBorder(10, 13, 10, 13));
        console.setText(t("eLuajs hazır. Dosya seçin, kodlayın, doğrulayın ve çıktı alın.") + "\n");
        JScrollPane consoleScroll = new JScrollPane(console);
        consoleScroll.setBorder(null);
        consoleScroll.getViewport().setBackground(Theme.CONSOLE);

        JPanel consoleWrap = new JPanel(new BorderLayout());
        consoleWrap.setBackground(Theme.CONSOLE);
        JPanel consoleHead = new JPanel(new BorderLayout());
        consoleHead.setBackground(Theme.TAB_BAR);
        consoleHead.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(0, 10, 0, 8)));
        JLabel outTab = Ui.label(t("ÇIKTI"), 10, Font.BOLD, Theme.TEXT);
        outTab.setOpaque(true);
        outTab.setBackground(Theme.TAB_ACTIVE);
        outTab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.ACCENT),
                new EmptyBorder(6, 10, 5, 10)));
        JPanel outLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        outLeft.setOpaque(false);
        outLeft.add(outTab);
        consoleHead.add(outLeft, BorderLayout.WEST);
        JButton clear = compact(t("Temizle"), t("Çıktıyı temizle"));
        clear.addActionListener(e -> console.setText(""));
        consoleHead.add(clear, BorderLayout.EAST);
        consoleWrap.add(consoleHead, BorderLayout.NORTH);
        consoleWrap.add(consoleScroll, BorderLayout.CENTER);

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, consoleWrap);
        vertical.setResizeWeight(0.80);
        vertical.setDividerSize(5);
        vertical.setBackground(Theme.BORDER_SOFT);
        vertical.setBorder(null);
        return vertical;
    }

    private JComponent pagesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Theme.PANEL);
        panel.setBorder(new EmptyBorder(4, 6, 4, 6));

        pagesList.setBackground(Theme.PANEL);
        pagesList.setForeground(Theme.TEXT);
        pagesList.setSelectionBackground(Theme.SELECTION);
        pagesList.setSelectionForeground(Theme.TEXT);
        pagesList.setFont(Theme.font(13, Font.PLAIN));
        pagesList.setFixedCellHeight(34);
        pagesList.setBorder(new EmptyBorder(6, 8, 6, 8));
        pagesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                var info = pagesList.getSelectedValue();
                if (info != null && info.javaFile() != null) openFile(info.javaFile());
            }
        });
        JScrollPane scroll = new JScrollPane(pagesList);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.PANEL);
        panel.add(scroll, BorderLayout.CENTER);

        JLabel hint = Ui.label(t("Bir sayfaya tıklayarak Java dosyasını aç."), 10, Font.PLAIN, Theme.MUTED);
        hint.setBorder(new EmptyBorder(2, 6, 4, 6));
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.STATUS_BG);
        bar.setBorder(new EmptyBorder(4, 12, 4, 12));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(Ui.statusLabel(t("Java / Android")));
        left.add(Ui.statusLabel(meta.packageName()));
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(editorStatus);
        right.add(Ui.statusLabel("UTF-8"));
        right.add(Ui.statusLabel("eLuajs " + HubFrame.VERSION));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JButton compact(String text, String tooltip) {
        JButton b = Ui.button(text, false);
        b.setToolTipText(tooltip);
        b.setFont(Theme.font(11, Font.BOLD));
        b.setBorder(new EmptyBorder(6, 8, 6, 8));
        return b;
    }

    private void toggleAiPanel() {
        if (aiPanel.isVisible()) hideAiPanel(); else showAiPanel();
    }

    private void showAiPanel() {
        aiPanel.setVisible(true);
        if (aiActivity != null) aiActivity.setActive(true);
        aiPanel.reloadConfig();
        aiPanel.focusPrompt();
        revalidate();
        repaint();
    }

    private void hideAiPanel() {
        aiPanel.setVisible(false);
        if (aiActivity != null) aiActivity.setActive(false);
        revalidate();
        repaint();
    }

    private void openSettings() {
        saveCurrentSilently();
        SettingsDialog dialog = new SettingsDialog(this);
        dialog.setVisible(true);
        aiPanel.reloadConfig();
        if (dialog.languageChanged()) {
            saveCurrentSilently();
            Ui.restartUi();
        }
    }

    private void openAssets() {
        new AssetsDialog(this, meta.rootDir(), () -> {
            reloadTree();
            appendConsole(t("Asset ağacı yenilendi."));
        }).setVisible(true);
    }

    private void openLibraries() {
        new LibrariesDialog(this, meta.rootDir(), () -> {
            reloadTree();
            appendConsole(t("Gradle kütüphaneleri güncellendi."));
        }, this::openFile).setVisible(true);
    }

    private void openPermissions() {
        new PermissionsDialog(this, meta.rootDir(), () -> {
            reloadTree();
            appendConsole(t("Android izinleri güncellendi."));
        }, this::openFile).setVisible(true);
    }

    private void installShortcuts() {
        JRootPane root = getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        am.put("save", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { saveCurrent(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "projectSearch");
        am.put("projectSearch", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { showProjectSearch(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "newJavaClass");
        am.put("newJavaClass", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { newJavaClass(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK), "aiAssistant");
        am.put("aiAssistant", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { toggleAiPanel(); }
        });
    }

    private void reloadTree() {
        int generation = treeReloadGeneration.incrementAndGet();
        DefaultMutableTreeNode loading = new DefaultMutableTreeNode(t("Proje ağacı yenileniyor..."));
        tree.setModel(new DefaultTreeModel(loading));

        SwingWorker<TreeSnapshot, Void> worker = new SwingWorker<>() {
            @Override protected TreeSnapshot doInBackground() {
                DefaultMutableTreeNode root = buildNode(meta.rootDir(), 0);
                List<ProjectActivityManager.ActivityInfo> pages;
                try { pages = ProjectActivityManager.listAll(meta); }
                catch (IOException ex) { pages = List.of(); }
                return new TreeSnapshot(root, pages);
            }
            @Override protected void done() {
                if (generation != treeReloadGeneration.get()) return;
                try {
                    TreeSnapshot snapshot = get();
                    tree.setModel(new DefaultTreeModel(snapshot.root()));
                    for (int i = 0; i < Math.min(tree.getRowCount(), 9); i++) tree.expandRow(i);
                    pagesModel.clear();
                    snapshot.pages().forEach(pagesModel::addElement);
                } catch (Exception ex) {
                    appendConsole(t("Proje ağacı yenilenemedi: %s", ex.getMessage()));
                }
            }
        };
        worker.execute();
    }

    private record TreeSnapshot(DefaultMutableTreeNode root, List<ProjectActivityManager.ActivityInfo> pages) { }

    private DefaultMutableTreeNode buildNode(Path p, int depth) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FileNode(p));
        if (Files.isDirectory(p) && depth < 8) {
            try (var s = Files.list(p)) {
                s.filter(x -> !x.getFileName().toString().equals(".gradle") && !x.getFileName().toString().equals("build"))
                        .sorted(Comparator.comparing((Path x) -> !Files.isDirectory(x))
                                .thenComparing(x -> x.getFileName().toString().toLowerCase()))
                        .forEach(x -> node.add(buildNode(x, depth + 1)));
            } catch (IOException ignored) { }
        }
        return node;
    }

    private void treeSelection(TreeSelectionEvent e) {
        Object n = tree.getLastSelectedPathComponent();
        if (!(n instanceof DefaultMutableTreeNode dm) || !(dm.getUserObject() instanceof FileNode fn)) return;
        if (Files.isRegularFile(fn.path) && FileUtil.isTextFile(fn.path)) openFile(fn.path);
    }

    private void openMainActivity() {
        Path main = meta.rootDir().resolve("app/src/main/java")
                .resolve(meta.packageName().replace('.', '/')).resolve("MainActivity.java");
        if (Files.isRegularFile(main)) openFile(main);
    }

    private void openFile(Path p) {
        if (p == null || !Files.isRegularFile(p)) return;
        if (openedFile != null && openedFile.equals(p)) return;
        if (!confirmAbandonOrSave()) return;
        try {
            editor.setFile(p, Files.readString(p, StandardCharsets.UTF_8));
            openedFile = p;
            String rel = meta.rootDir().relativize(p).toString();
            currentFile.setText(p.getFileName().toString());
            currentFile.setToolTipText(p.toString());
            breadcrumb.setText(rel.replace("/", "  ›  ").replace("\\", "  ›  "));
            dirty = false;
            updateEditorStatus();
        } catch (IOException ex) {
            appendConsole(t("Dosya açılamadı: %s", ex.getMessage()));
        }
    }

    private void markDirty() {
        dirty = true;
        updateEditorStatus();
    }

    private void saveCurrent() {
        if (openedFile == null) return;
        try {
            FileUtil.recoveryCopy(meta.rootDir(), openedFile);
            FileUtil.writeStringAtomic(openedFile, editor.getText());
            dirty = false;
            updateEditorStatus();
            appendConsole(t("Kaydedildi: %s", meta.rootDir().relativize(openedFile)));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Kaydetme hatası"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCurrentSilently() {
        if (openedFile != null && dirty) saveCurrent();
    }

    private boolean confirmAbandonOrSave() {
        if (!dirty || openedFile == null) return true;
        Object[] options = {t("Kaydet"), t("Değişiklikleri At"), t("İptal")};
        int choice = JOptionPane.showOptionDialog(this,
                t("%s dosyasında kaydedilmemiş değişiklikler var.", meta.rootDir().relativize(openedFile)),
                t("Kaydedilmemiş değişiklik"), JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice == 0) { saveCurrent(); return !dirty; }
        return choice == 1;
    }

    private void confirmClose() {
        if (!confirmAbandonOrSave()) return;
        dispose();
    }

    private void openFileAtLine(Path file, Integer line) {
        if (file == null || !Files.isRegularFile(file) || !FileUtil.isTextFile(file)) {
            appendConsole(t("Dosya konumu açılamadı: %s", file));
            return;
        }
        openFile(file);
        int targetLine = line == null ? 1 : Math.max(1, line);
        Element root = editor.getDocument().getDefaultRootElement();
        int index = Math.min(targetLine - 1, Math.max(0, root.getElementCount() - 1));
        Element el = root.getElement(index);
        if (el != null) {
            editor.setCaretPosition(Math.min(el.getStartOffset(), editor.getDocument().getLength()));
            try { editor.scrollRectToVisible(editor.modelToView2D(editor.getCaretPosition()).getBounds()); }
            catch (Exception ignored) { }
            editor.requestFocusInWindow();
            appendConsole(t("Konuma gidildi: %s:%d", meta.rootDir().relativize(file), targetLine));
        }
    }

    private void showProjectSearch() {
        if (searchDialog == null || !searchDialog.isDisplayable()) {
            searchDialog = new ProjectSearchDialog(this, meta.rootDir(), this::openFileAtLine);
        }
        searchDialog.focusQuery();
    }

    private void newJavaClass() {
        saveCurrentSilently();
        NewJavaClassDialog dialog = new NewJavaClassDialog(this, meta);
        dialog.setVisible(true);
        Path made = dialog.created();
        if (made == null) return;
        reloadTree();
        openFile(made);
        appendConsole(t("Yeni Java sınıfı oluşturuldu: %s", meta.rootDir().relativize(made)));
    }

    private void updateEditorStatus() {
        try {
            int caret = editor.getCaretPosition();
            Element root = editor.getDocument().getDefaultRootElement();
            int line = root.getElementIndex(caret);
            Element el = root.getElement(line);
            int col = caret - (el == null ? 0 : el.getStartOffset());
            editorStatus.setText(t("Satır %d, Sütun %d", line + 1, col + 1) + (dirty ? "  •  " + t("Değiştirildi") : "  •  " + t("Kaydedildi")));
            editorStatus.setForeground(Theme.STATUS_TEXT);
            tabDot.setText(dirty ? "●" : "");
        } catch (Exception ignored) { }
    }

    private void newActivity() {
        saveCurrentSilently();
        NewActivityDialog dialog = new NewActivityDialog(this, meta);
        dialog.setVisible(true);
        ProjectActivityManager.CreatedActivity made = dialog.created();
        if (made == null) return;
        reloadTree();
        openFile(made.javaFile());
        appendConsole("\n" + t("--- Yeni Sayfa ---"));
        appendConsole(t("%s.java oluşturuldu ve AndroidManifest.xml'e kaydedildi.", made.className()));
        appendConsole(t("Bu sayfayı açmak için:"));
        appendConsole("import android.content.Intent;");
        appendConsole("Intent intent = new Intent(this, " + made.className() + ".class);");
        appendConsole("startActivity(intent);");
        JOptionPane.showMessageDialog(this,
                t("%s oluşturuldu.\nManifest kaydı otomatik eklendi.\n\nSayfalar arası geçiş örneği alt konsola yazıldı.", made.className()),
                t("Yeni sayfa hazır"), JOptionPane.INFORMATION_MESSAGE);
    }

    private void deleteActivity() {
        saveCurrentSilently();
        DeleteActivityDialog dialog = new DeleteActivityDialog(this, meta);
        dialog.setVisible(true);
        String deleted = dialog.deletedClassName();
        if (deleted == null) return;

        boolean deletedWasOpen = openedFile != null && openedFile.getFileName().toString().equals(deleted + ".java");
        reloadTree();
        if (deletedWasOpen) {
            openedFile = null;
            dirty = false;
            openMainActivity();
        }
        appendConsole("\n" + t("--- Sayfa Silindi ---"));
        appendConsole(t("%s.java ve Manifest kaydı kaldırıldı.", deleted));
        appendConsole(t("Not: Bu sayfaya giden Intent kodları varsa onları koddan siz kaldırmalısınız."));
    }

    private void validateProject() {
        saveCurrentSilently();
        ProjectValidator.Report r = ProjectValidator.validate(meta.rootDir());
        appendConsole("\n" + t("--- Doğrulama ---"));
        r.messages().forEach(this::appendConsole);
        JOptionPane.showMessageDialog(this, String.join("\n", r.messages()),
                r.ok() ? t("Doğrulama başarılı") : t("Doğrulama sorunları"),
                r.ok() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private void appendConsole(String s) {
        console.append(s + "\n");
        console.setCaretPosition(console.getDocument().getLength());
    }

    private static final class DarkTreeRenderer extends DefaultTreeCellRenderer {
        DarkTreeRenderer() {
            setOpaque(true);
            setOpenIcon(null);
            setClosedIcon(null);
            setLeafIcon(null);
            setBackgroundNonSelectionColor(Theme.PANEL);
            setBackgroundSelectionColor(Theme.SELECTION);
            setTextNonSelectionColor(Theme.TEXT);
            setTextSelectionColor(Theme.TEXT);
            setBorderSelectionColor(Theme.SELECTION);
        }

        @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, false);
            setBackground(selected ? Theme.SELECTION : Theme.PANEL);
            setForeground(Theme.TEXT);
            setBorder(new EmptyBorder(2, 4, 2, 4));
            Object user = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : null;
            if (user instanceof FileNode fn) {
                setIcon(Icons.fileIcon(fn.path, Files.isDirectory(fn.path), expanded));
            } else {
                setIcon(null);
            }
            return c;
        }
    }

    private static final class FileNode {
        final Path path;
        FileNode(Path path) { this.path = path; }
        @Override public String toString() {
            return path.getFileName() == null ? path.toString() : path.getFileName().toString();
        }
    }

    private static final class LineNumberView extends JComponent {
        private final JTextComponent text;

        LineNumberView(JTextComponent text) {
            this.text = text;
            setFont(Theme.mono(12));
            setForeground(new Color(91, 108, 126));
            setBackground(Theme.EDITOR);
            setOpaque(true);
            text.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { revalidate(); repaint(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { revalidate(); repaint(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
            });
        }

        private int lineCount() {
            return text.getDocument().getDefaultRootElement().getElementCount();
        }

        @Override public Dimension getPreferredSize() {
            int lines = Math.max(1, lineCount());
            int digits = Math.max(2, String.valueOf(lines).length());
            return new Dimension(14 + getFontMetrics(getFont()).charWidth('0') * digits,
                    Math.max(text.getPreferredSize().height, getParent() == null ? 0 : getParent().getHeight()));
        }

        @Override protected void paintComponent(Graphics g) {
            // A bare JComponent has no UI delegate, so super.paintComponent does
            // not fill the background. Without this the gutter viewport shows its
            // default white background behind the line numbers.
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Theme.BORDER_SOFT);
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            Element root = text.getDocument().getDefaultRootElement();
            Rectangle clip = g.getClipBounds();
            g.setFont(getFont());
            g.setColor(getForeground());
            FontMetrics fm = text.getFontMetrics(text.getFont());

            for (int i = 0; i < root.getElementCount(); i++) {
                try {
                    Rectangle r = text.modelToView2D(root.getElement(i).getStartOffset()).getBounds();
                    if (r.y + r.height < clip.y) continue;
                    if (r.y > clip.y + clip.height) break;
                    String n = String.valueOf(i + 1);
                    int x = getWidth() - 7 - g.getFontMetrics().stringWidth(n);
                    int y = r.y + fm.getAscent();
                    g.drawString(n, x, y);
                } catch (Exception ignored) { }
            }
        }
    }
}
