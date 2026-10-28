package eluajs.ui;

import eluajs.model.ProjectMeta;
import eluajs.project.ProjectValidator;
import eluajs.project.ProjectMigrator;
import eluajs.project.ProjectSearch;
import eluajs.project.Problem;
import eluajs.project.ProjectLayout;
import eluajs.project.ProjectFileManager;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class EditorFrame extends JFrame {
    private final ProjectMeta meta;
    private final Runnable onCloseRefresh;
    private final JTree tree = new JTree();
    private final DefaultListModel<ProjectActivityManager.ActivityInfo> pagesModel = new DefaultListModel<>();
    private final JList<ProjectActivityManager.ActivityInfo> pagesList = new JList<>(pagesModel);
    private SyntaxEditorPane editor = new SyntaxEditorPane();
    private final SyntaxEditorPane emptyEditor = editor;
    private final SyntaxEditorPane splitEditor = new SyntaxEditorPane();
    private final JTabbedPane editorTabs = new JTabbedPane();
    private final CardLayout editorCards = new CardLayout();
    private final JPanel editorHost = new JPanel(editorCards);
    private final Map<Path, EditorTab> openTabs = new LinkedHashMap<>();
    private final JPanel splitPanel = new JPanel(new BorderLayout());
    private final JSplitPane editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JLabel splitTitle = Ui.label("", 10, Font.BOLD, Theme.MUTED);
    private final JButton splitSaveButton = Ui.button(t("Kaydet"), false);
    private Path splitFile;
    private boolean splitDirty;
    private boolean splitSuppressDirty;
    private EditorTab activeTab;
    private boolean tabsUpdating;
    private boolean splitVisible;
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
    private final CardLayout bottomCards = new CardLayout();
    private final JPanel bottomContent = new JPanel(bottomCards);
    private final JPanel bottomTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final Map<String, JButton> bottomTabButtons = new HashMap<>();
    private final DefaultListModel<Problem> problemsModel = new DefaultListModel<>();
    private final JList<Problem> problemsList = new JList<>(problemsModel);
    private final JLabel problemsStatus = Ui.label(t("Sorun yok."), 11, Font.PLAIN, Theme.MUTED);
    private final JTextField searchQuery = Ui.field();
    private final JTextField searchReplace = Ui.field();
    private final JCheckBox searchCase = new JCheckBox(t("Büyük/küçük harf duyarlı"));
    private final DefaultListModel<ProjectSearch.Match> searchModel = new DefaultListModel<>();
    private final JList<ProjectSearch.Match> searchResults = new JList<>(searchModel);
    private final JLabel searchStatus = Ui.label(t("Aranacak metni yazın."), 11, Font.PLAIN, Theme.MUTED);
    private final JButton searchButton = Ui.button("⌕  " + t("Ara"), true);
    private final JButton replaceAllButton = Ui.button(t("Tümünü Değiştir"), false);
    private String activeBottomTab = "output";
    private Path openedFile;
    private boolean dirty;
    private final AtomicInteger treeReloadGeneration = new AtomicInteger();

    public EditorFrame(ProjectMeta meta, Runnable onCloseRefresh) {
        super("eLuajs " + HubFrame.VERSION + " — " + meta.appName());
        WindowChrome.install(this);
        this.meta = meta;
        this.onCloseRefresh = onCloseRefresh;
        this.aiPanel = new AiPanel(this::activeEditor,
                () -> openedFile == null ? t("Dosya seçin") : meta.rootDir().relativize(openedFile).toString(),
                () -> openedFile,
                meta.rootDir(),
                this::hideAiPanel,
                this::saveCurrentSilently,
                this::refreshAfterAiEdit);
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
        JButton split = Ui.iconButton(t("Böl"), "split", false);
        split.setToolTipText(t("Yan yana önizleme (Ctrl+\\)"));
        split.addActionListener(e -> toggleSplit());
        JButton build = Ui.iconButton(t("Derle / APK + AAB"), "run", true);
        build.addActionListener(e -> {
            saveCurrentSilently();
            clearProblems();
            BuildDialog dialog = new BuildDialog(this, meta.rootDir(), this::openFileAtLine, this::addProblem);
            dialog.setBuildFix(log -> {
                showAiPanel();
                aiPanel.fixBuild(log);
            });
            dialog.setVisible(true);
        });
        primary.add(save);
        primary.add(validate);
        primary.add(split);
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
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("git", t("Git"), () -> new GitDialog(this, meta.rootDir()).setVisible(true)));
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("tune", t("Proje Ayarları"), this::openProjectSettings));
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

        JPanel actions = new JPanel(new GridLayout(3, 2, 9, 9));
        actions.setBackground(Theme.PANEL);
        actions.setBorder(new EmptyBorder(0, 14, 12, 14));
        JButton addFile = compact("＋ " + t("Dosya"), t("Yeni dosya oluştur"));
        addFile.addActionListener(e -> newTreeFile(treeTarget()));
        JButton addFolder = compact("＋ " + t("Klasör"), t("Yeni klasör oluştur"));
        addFolder.addActionListener(e -> newTreeFolder(treeTarget()));
        JButton addPage = compact("＋ " + t("Sayfa"), t("Yeni Android Activity / ekran oluştur"));
        addPage.addActionListener(e -> newActivity());
        JButton delPage = compact("− " + t("Sayfa"), t("Ek Android Activity / sayfayı sil"));
        delPage.setForeground(Theme.DANGER);
        delPage.addActionListener(e -> deleteActivity());
        JButton addClass = compact("＋ " + t("Sınıf"), t("Activity olmayan normal Java sınıfı oluştur (Ctrl+Shift+N)"));
        addClass.addActionListener(e -> newJavaClass());
        JButton refreshTree = compact("⟳ " + t("Yenile"), t("Dosya ağacını yenile"));
        refreshTree.addActionListener(e -> reloadTree());
        actions.add(addFile);
        actions.add(addFolder);
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
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showTreeMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showTreeMenu(e); }
        });
        JScrollPane treeScroll = Ui.scroll(tree);
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
        emptyEditor.setEditable(false);
        emptyEditor.setBackground(Theme.EDITOR);
        emptyEditor.setForeground(Theme.MUTED);
        emptyEditor.setCaretColor(Theme.ACCENT);
        emptyEditor.setText("\n\n  " + t("Dosya seçin"));
        JScrollPane emptyScroll = Ui.scroll(emptyEditor);
        emptyScroll.setBorder(null);
        emptyScroll.getViewport().setBackground(Theme.EDITOR);

        editorHost.setBackground(Theme.EDITOR);
        editorHost.add(emptyScroll, "empty");
        editorTabs.setFont(Theme.font(12, Font.PLAIN));
        editorTabs.setUI(new Theme.FlatTabbedPaneUI());
        editorTabs.setBackground(Theme.TAB_BAR);
        editorTabs.setForeground(Theme.TEXT_SOFT);
        editorTabs.setBorder(new EmptyBorder(0, 0, 0, 0));
        editorTabs.addChangeListener(e -> {
            if (!tabsUpdating) {
                syncActiveTab();
                updateTabStyles();
            }
        });
        editorTabs.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    int index = editorTabs.indexAtLocation(e.getX(), e.getY());
                    if (index >= 0 && editorTabs.getComponentAt(index) instanceof EditorTab tab) closeTab(tab);
                }
            }
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int index = editorTabs.indexAtLocation(e.getX(), e.getY());
                    if (index >= 0) editorTabs.setSelectedIndex(index);
                }
                maybeTabMenu(e);
            }
            @Override public void mouseReleased(MouseEvent e) { maybeTabMenu(e); }
        });
        editorHost.add(editorTabs, "tabs");
        editorCards.show(editorHost, "empty");

        splitEditor.setBackground(Theme.EDITOR);
        splitEditor.setForeground(Theme.TEXT);
        splitEditor.setCaretColor(Theme.ACCENT);
        splitEditor.setSelectionColor(Theme.SELECTION);
        JScrollPane splitScroll = Ui.scroll(splitEditor);
        splitScroll.setBorder(null);
        splitScroll.getViewport().setBackground(Theme.EDITOR);
        splitScroll.setRowHeaderView(new LineNumberView(splitEditor));
        if (splitScroll.getRowHeader() != null) splitScroll.getRowHeader().setBackground(Theme.EDITOR);
        JPanel splitHead = new JPanel(new BorderLayout());
        splitHead.setBackground(Theme.TAB_BAR);
        splitHead.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(4, 10, 4, 10)));
        splitHead.add(splitTitle, BorderLayout.WEST);
        JPanel splitTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        splitTools.setOpaque(false);
        splitSaveButton.setFont(Theme.font(10, Font.BOLD));
        splitSaveButton.setBorder(new EmptyBorder(3, 8, 3, 8));
        splitSaveButton.addActionListener(e -> saveSplit());
        JButton splitClose = compact("×", t("Bölmeyi Kapat"));
        splitClose.addActionListener(e -> closeSplit());
        splitTools.add(splitSaveButton);
        splitTools.add(splitClose);
        splitHead.add(splitTools, BorderLayout.EAST);
        splitPanel.setBackground(Theme.EDITOR);
        splitPanel.add(splitHead, BorderLayout.NORTH);
        splitPanel.add(splitScroll, BorderLayout.CENTER);
        splitPanel.setVisible(false);
        splitEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { markSplitDirty(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { markSplitDirty(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        splitEditor.addCaretListener(e -> {
            if (splitFile != null) splitTitle.setText((splitDirty ? "● " : "") + meta.rootDir().relativize(splitFile));
        });
        splitEditor.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "split-save");
        splitEditor.getActionMap().put("split-save", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { saveSplit(); }
        });

        editorSplit.setLeftComponent(editorHost);
        editorSplit.setBorder(null);
        editorSplit.setDividerSize(5);
        editorSplit.setResizeWeight(0.5);
        editorSplit.setBackground(Theme.BORDER_SOFT);

        JPanel editorHead = new JPanel(new BorderLayout());
        editorHead.setBackground(Theme.EDITOR);
        editorHead.add(editorSplit, BorderLayout.CENTER);

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBackground(Theme.EDITOR);
        editorPanel.add(editorHead, BorderLayout.CENTER);

        console.setEditable(false);
        console.setFont(Theme.mono(12));
        console.setBackground(Theme.CONSOLE);
        console.setForeground(Theme.MUTED);
        console.setCaretColor(Theme.ACCENT);
        console.setBorder(new EmptyBorder(10, 13, 10, 13));
        console.setText(t("eLuajs hazır. Dosya seçin, kodlayın, doğrulayın ve çıktı alın.") + "\n");
        JComponent consoleWrap = bottomPanel();

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, consoleWrap);
        vertical.setResizeWeight(0.80);
        vertical.setDividerSize(5);
        vertical.setBackground(Theme.BORDER_SOFT);
        vertical.setBorder(null);
        return vertical;
    }

    private JComponent bottomPanel() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.CONSOLE);
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Theme.TAB_BAR);
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(0, 6, 0, 8)));
        bottomTabs.setOpaque(false);
        bottomTabs.add(tabButton("output", t("ÇIKTI")));
        bottomTabs.add(tabButton("problems", t("SORUNLAR")));
        bottomTabs.add(tabButton("search", t("ARAMA")));
        head.add(bottomTabs, BorderLayout.WEST);
        JButton clear = compact(t("Temizle"), t("Çıktıyı temizle"));
        clear.addActionListener(e -> clearActiveBottom());
        head.add(clear, BorderLayout.EAST);
        wrap.add(head, BorderLayout.NORTH);

        bottomContent.setOpaque(false);
        bottomContent.add(outputPanel(), "output");
        bottomContent.add(problemsPanel(), "problems");
        bottomContent.add(searchPanel(), "search");
        wrap.add(bottomContent, BorderLayout.CENTER);
        showBottomCard("output");
        return wrap;
    }

    private JButton tabButton(String key, String label) {
        JButton button = new JButton(label);
        button.setFont(Theme.font(10, Font.BOLD));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBackground(Theme.TAB_BAR);
        button.setForeground(Theme.MUTED);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(8, 12, 6, 12));
        button.addActionListener(e -> showBottomCard(key));
        bottomTabButtons.put(key, button);
        return button;
    }

    private void showBottomCard(String key) {
        activeBottomTab = key;
        bottomCards.show(bottomContent, key);
        for (Map.Entry<String, JButton> entry : bottomTabButtons.entrySet()) {
            boolean active = entry.getKey().equals(key);
            JButton button = entry.getValue();
            button.setBackground(active ? Theme.TAB_ACTIVE : Theme.TAB_BAR);
            button.setForeground(active ? Theme.TEXT : Theme.MUTED);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 2, 0, active ? Theme.ACCENT : Theme.TAB_BAR),
                    new EmptyBorder(8, 12, 6, 12)));
        }
        if (key.equals("search")) SwingUtilities.invokeLater(searchQuery::requestFocusInWindow);
    }

    private void updateProblemsTab() {
        JButton button = bottomTabButtons.get("problems");
        if (button != null) {
            button.setText(t("SORUNLAR") + (problemsModel.isEmpty() ? "" : "  " + problemsModel.size()));
            bottomTabs.revalidate();
            bottomTabs.repaint();
        }
    }

    private void updateSearchTab() {
        JButton button = bottomTabButtons.get("search");
        if (button != null) {
            button.setText(t("ARAMA") + (searchModel.isEmpty() ? "" : "  " + searchModel.size()));
            bottomTabs.revalidate();
            bottomTabs.repaint();
        }
    }

    private void clearActiveBottom() {
        switch (activeBottomTab) {
            case "problems" -> clearProblems();
            case "search" -> {
                searchModel.clear();
                searchStatus.setText(t("Aranacak metni yazın."));
                updateSearchTab();
            }
            default -> console.setText("");
        }
    }

    private JComponent outputPanel() {
        JScrollPane scroll = Ui.scroll(console);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.CONSOLE);
        return scroll;
    }

    private JComponent problemsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(Theme.CONSOLE);
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        statusRow.add(problemsStatus, BorderLayout.WEST);
        panel.add(statusRow, BorderLayout.NORTH);

        problemsList.setBackground(Theme.CONSOLE);
        problemsList.setForeground(Theme.TEXT);
        problemsList.setSelectionBackground(Theme.SELECTION);
        problemsList.setSelectionForeground(Theme.TEXT);
        problemsList.setFixedCellHeight(28);
        problemsList.setBorder(new EmptyBorder(4, 2, 4, 2));
        problemsList.setCellRenderer(new ProblemRenderer());
        problemsList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedProblem();
            }
        });
        JScrollPane scroll = Ui.scroll(problemsList);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        scroll.getViewport().setBackground(Theme.CONSOLE);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JComponent searchPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 7));
        panel.setBackground(Theme.CONSOLE);
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JPanel firstRow = new JPanel(new BorderLayout(8, 0));
        firstRow.setOpaque(false);
        searchQuery.setToolTipText(t("Aranacak metin"));
        searchQuery.addActionListener(e -> runSearch());
        firstRow.add(searchQuery, BorderLayout.CENTER);
        JPanel searchActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchActions.setOpaque(false);
        searchCase.setOpaque(false);
        searchCase.setForeground(Theme.MUTED);
        searchCase.setFont(Theme.font(11, Font.PLAIN));
        searchCase.setFocusPainted(false);
        searchButton.addActionListener(e -> runSearch());
        searchActions.add(searchCase);
        searchActions.add(searchButton);
        firstRow.add(searchActions, BorderLayout.EAST);

        JPanel secondRow = new JPanel(new BorderLayout(8, 0));
        secondRow.setOpaque(false);
        searchReplace.setToolTipText(t("Yeni metin"));
        searchReplace.addActionListener(e -> replaceAll());
        secondRow.add(searchReplace, BorderLayout.CENTER);
        replaceAllButton.addActionListener(e -> replaceAll());
        secondRow.add(replaceAllButton, BorderLayout.EAST);

        searchStatus.setBorder(new EmptyBorder(4, 1, 0, 0));
        form.add(firstRow);
        form.add(Box.createVerticalStrut(6));
        form.add(secondRow);
        form.add(Box.createVerticalStrut(2));
        form.add(searchStatus);
        panel.add(form, BorderLayout.NORTH);

        searchResults.setBackground(Theme.CONSOLE);
        searchResults.setForeground(Theme.TEXT);
        searchResults.setSelectionBackground(Theme.SELECTION);
        searchResults.setSelectionForeground(Theme.TEXT);
        searchResults.setFixedCellHeight(28);
        searchResults.setBorder(new EmptyBorder(4, 2, 4, 2));
        searchResults.setCellRenderer(new SearchRenderer());
        searchResults.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedSearchResult();
            }
        });
        JScrollPane scroll = Ui.scroll(searchResults);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        scroll.getViewport().setBackground(Theme.CONSOLE);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void showProblems(List<Problem> problems) {
        problemsModel.clear();
        for (Problem problem : problems) addProblem(problem);
        problemsStatus.setText(problemsModel.isEmpty() ? t("Sorun yok.") : t("%d sorun", problemsModel.size()));
        updateProblemsTab();
        showBottomCard("problems");
    }

    private void addProblem(Problem problem) {
        if (problem == null) return;
        for (int i = 0; i < problemsModel.size(); i++) {
            if (problemsModel.get(i).equals(problem)) return;
        }
        problemsModel.addElement(problem);
        problemsStatus.setText(t("%d sorun", problemsModel.size()));
        updateProblemsTab();
    }

    private void clearProblems() {
        problemsModel.clear();
        problemsStatus.setText(t("Sorun yok."));
        updateProblemsTab();
    }

    private void openSelectedProblem() {
        Problem problem = problemsList.getSelectedValue();
        if (problem != null && problem.file() != null) openFileAtLine(problem.file(), problem.line());
    }

    private void openSelectedSearchResult() {
        ProjectSearch.Match match = searchResults.getSelectedValue();
        if (match != null) openFileAtLine(match.file(), match.line());
    }

    private void runSearch() {
        if (!searchButton.isEnabled()) return;
        String query = searchQuery.getText().trim();
        if (query.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            searchStatus.setText(t("Aranacak metin boş olamaz."));
            return;
        }
        searchButton.setEnabled(false);
        searchModel.clear();
        updateSearchTab();
        searchStatus.setText(t("Aranıyor..."));
        SwingWorker<List<ProjectSearch.Match>, Void> worker = new SwingWorker<>() {
            @Override protected List<ProjectSearch.Match> doInBackground() throws Exception {
                return ProjectSearch.search(meta.rootDir(), query, searchCase.isSelected(), 500);
            }
            @Override protected void done() {
                searchButton.setEnabled(true);
                try {
                    List<ProjectSearch.Match> found = get();
                    found.forEach(searchModel::addElement);
                    searchStatus.setText(t("%d sonuç", found.size())
                            + (found.size() >= 500 ? t(" (ilk 500)") : "")
                            + t(". Çift tıklayarak açın."));
                    if (!found.isEmpty()) searchResults.setSelectedIndex(0);
                } catch (Exception ex) {
                    searchStatus.setText(t("Arama başarısız."));
                }
                updateSearchTab();
            }
        };
        worker.execute();
    }

    private void replaceAll() {
        if (!replaceAllButton.isEnabled()) return;
        String query = searchQuery.getText().trim();
        if (query.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            searchStatus.setText(t("Aranacak metin boş olamaz."));
            return;
        }
        String replacement = searchReplace.getText();
        int choice = JOptionPane.showConfirmDialog(this,
                t("'%s' metni '%s' ile değiştirilsin mi?\n\nYedekler .eluajs/recovery altına alınır.", query, replacement),
                t("Tümünü Değiştir"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        saveCurrentSilently();
        if (dirty) {
            searchStatus.setText(t("Değiştirme iptal edildi: açık dosya kaydedilemedi."));
            return;
        }
        searchButton.setEnabled(false);
        replaceAllButton.setEnabled(false);
        searchStatus.setText(t("Değiştiriliyor..."));
        SwingWorker<ProjectSearch.ReplaceResult, Void> worker = new SwingWorker<>() {
            @Override protected ProjectSearch.ReplaceResult doInBackground() throws Exception {
                return ProjectSearch.replaceAll(meta.rootDir(), query, replacement, searchCase.isSelected());
            }
            @Override protected void done() {
                searchButton.setEnabled(true);
                replaceAllButton.setEnabled(true);
                try {
                    ProjectSearch.ReplaceResult result = get();
                    appendConsole(t("Değiştirme tamamlandı: %d dosyada %d değişiklik.", result.files(), result.replacements()));
                    for (String error : result.errors()) appendConsole(t("Değiştirilemedi: %s", error));
                    if (openedFile != null) {
                        Path open = openedFile.toAbsolutePath().normalize();
                        boolean changed = result.changed().stream()
                                .anyMatch(path -> path.toAbsolutePath().normalize().equals(open));
                        if (changed) reloadOpenFile();
                    }
                    if (result.replacements() == 0) searchStatus.setText(t("Eşleşme bulunamadı."));
                    else searchStatus.setText(t("%d dosyada %d değişiklik yapıldı.", result.files(), result.replacements()));
                    runSearch();
                } catch (Exception ex) {
                    searchStatus.setText(t("Değiştirme hatası: %s", ex.getMessage()));
                    JOptionPane.showMessageDialog(EditorFrame.this, ex.getMessage(), t("Değiştirme hatası"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void reloadOpenFile() {
        EditorTab tab = activeTab;
        if (tab == null || !Files.isRegularFile(tab.file)) return;
        try {
            int caret = tab.pane.getCaretPosition();
            setTabText(tab, Files.readString(tab.file, StandardCharsets.UTF_8));
            tab.pane.setCaretPosition(Math.min(caret, tab.pane.getDocument().getLength()));
            dirty = false;
            updateEditorStatus();
        } catch (IOException ex) {
            appendConsole(t("Dosya açılamadı: %s", ex.getMessage()));
        }
    }

    private final class ProblemRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
            if (value instanceof Problem problem) {
                String icon = switch (problem.severity()) {
                    case ERROR -> "●";
                    case WARNING -> "▲";
                    default -> "i";
                };
                label.setText(icon + "  " + problem.location(meta.rootDir()) + "   " + problem.message());
                label.setForeground(switch (problem.severity()) {
                    case ERROR -> Theme.DANGER;
                    case WARNING -> Theme.WARNING;
                    default -> Theme.BLUE;
                });
                label.setToolTipText(problem.file() == null ? problem.message() : problem.file() + ":" + problem.line());
            }
            label.setBorder(new EmptyBorder(3, 6, 3, 6));
            label.setBackground(selected ? Theme.SELECTION : Theme.CONSOLE);
            label.setFont(Theme.mono(11));
            return label;
        }
    }

    private final class SearchRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
            if (value instanceof ProjectSearch.Match match) {
                String path;
                try { path = meta.rootDir().relativize(match.file()).toString(); }
                catch (Exception ex) { path = match.file().toString(); }
                label.setText(path + ":" + match.line() + "   " + match.preview());
                label.setToolTipText(match.file() + ":" + match.line());
            }
            label.setBorder(new EmptyBorder(3, 6, 3, 6));
            label.setBackground(selected ? Theme.SELECTION : Theme.CONSOLE);
            label.setForeground(Theme.TEXT);
            label.setFont(Theme.mono(11));
            return label;
        }
    }

    private SyntaxEditorPane activeEditor() {
        return editor;
    }

    private void syncActiveTab() {
        if (tabsUpdating) return;
        int index = editorTabs.getSelectedIndex();
        EditorTab selected = index >= 0 && editorTabs.getComponentAt(index) instanceof EditorTab tab ? tab : null;
        activeTab = selected;
        if (selected == null) {
            editor = emptyEditor;
            openedFile = null;
            dirty = false;
            editorCards.show(editorHost, "empty");
            breadcrumb.setText(t("Dosya seçin"));
            currentFile.setText(t("Dosya seçin"));
            tabDot.setText("");
        } else {
            editor = selected.pane;
            openedFile = selected.file;
            dirty = selected.dirty;
            editorCards.show(editorHost, "tabs");
            String rel = relative(selected.file);
            breadcrumb.setText(rel.replace("/", "  ›  ").replace("\\", "  ›  "));
            currentFile.setText(selected.file.getFileName().toString());
            currentFile.setToolTipText(selected.file.toString());
            tabDot.setText(selected.dirty ? "●" : "");
        }
        updateEditorStatus();
        updateTabStyles();
    }

    private void updateTabStyles() {
        for (EditorTab tab : openTabs.values()) {
            boolean active = tab == activeTab;
            if (tab.titleLabel != null) tab.titleLabel.setForeground(active ? Theme.TEXT : Theme.MUTED);
            if (tab.closeButton != null) tab.closeButton.setForeground(active ? Theme.TEXT_SOFT : Theme.MUTED);
            if (tab.header != null) tab.header.setBackground(active ? Theme.TAB_ACTIVE : Theme.TAB_BAR);
        }
        if (editorTabs != null) {
            editorTabs.repaint();
        }
    }

    private String relative(Path file) {
        try { return meta.rootDir().relativize(file).toString(); }
        catch (Exception ex) { return file.toString(); }
    }

    private EditorTab createTab(Path file, String text) {
        EditorTab tab = new EditorTab(file, text);
        tab.pane.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { markTabDirty(tab); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { markTabDirty(tab); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        tab.pane.addCaretListener(e -> {
            if (tab == activeTab) updateEditorStatus();
        });
        return tab;
    }

    private void markTabDirty(EditorTab tab) {
        if (tab == null || tab.suppressDirty) return;
        tab.dirty = true;
        updateTabTitle(tab);
        if (tab == activeTab) {
            dirty = true;
            updateEditorStatus();
        }
    }

    private void updateTabTitle(EditorTab tab) {
        if (tab == null) return;
        int index = editorTabs.indexOfComponent(tab);
        if (index < 0) return;
        String title = (tab.dirty ? "● " : "") + tab.file.getFileName();
        editorTabs.setTitleAt(index, title);
        if (tab.titleLabel != null) tab.titleLabel.setText(title);
        if (tab.titleLabel != null) tab.titleLabel.setToolTipText(relative(tab.file));
    }

    private JComponent tabHeader(EditorTab tab) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        header.setOpaque(true);
        header.setBackground(Theme.TAB_BAR);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tab.header = header;
        tab.titleLabel = Ui.label("", 12, Font.PLAIN, Theme.MUTED);
        tab.titleLabel.setBorder(new EmptyBorder(4, 2, 4, 0));
        tab.titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JButton close = new JButton("×");
        tab.closeButton = close;
        close.setFont(Theme.font(14, Font.BOLD));
        close.setForeground(Theme.MUTED);
        close.setOpaque(false);
        close.setContentAreaFilled(false);
        close.setBorderPainted(false);
        close.setFocusPainted(false);
        close.setPreferredSize(new Dimension(22, 22));
        close.setMargin(new Insets(0, 0, 0, 0));
        close.setToolTipText(t("Close"));
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> closeTab(tab));
        close.getModel().addChangeListener(e -> close.setForeground(
                close.getModel().isRollover() ? Theme.DANGER : (tab == activeTab ? Theme.TEXT_SOFT : Theme.MUTED)));
        header.add(tab.titleLabel);
        header.add(close);
        MouseAdapter menu = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) editorTabs.setSelectedComponent(tab);
                if (e.isPopupTrigger()) showTabMenu(tab, header, e.getX(), e.getY());
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showTabMenu(tab, header, e.getX(), e.getY());
            }
        };
        header.addMouseListener(menu);
        tab.titleLabel.addMouseListener(menu);
        String title = (tab.dirty ? "● " : "") + tab.file.getFileName();
        tab.titleLabel.setText(title);
        tab.titleLabel.setToolTipText(relative(tab.file));
        return header;
    }

    private void maybeTabMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int index = editorTabs.indexAtLocation(e.getX(), e.getY());
        if (index < 0 || !(editorTabs.getComponentAt(index) instanceof EditorTab tab)) return;
        editorTabs.setSelectedComponent(tab);
        showTabMenu(tab, editorTabs, e.getX(), e.getY());
    }

    private void showTabMenu(EditorTab tab, Component component, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem close = new JMenuItem(t("Kapat"));
        close.addActionListener(e -> closeTab(tab));
        JMenuItem closeOthers = new JMenuItem(t("Diğerlerini Kapat"));
        closeOthers.addActionListener(e -> closeOtherTabs(tab));
        JMenuItem closeAll = new JMenuItem(t("Tüm Sekmeleri Kapat"));
        closeAll.addActionListener(e -> closeAllTabs());
        JMenuItem copyPath = new JMenuItem(t("Dosya Yolunu Kopyala"));
        copyPath.addActionListener(e -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(tab.file.toString()), null);
        });
        JMenuItem split = new JMenuItem(splitVisible ? t("Bölmeyi Kapat") : t("Yana Böl"));
        split.addActionListener(e -> toggleSplit());
        JMenuItem openSide = new JMenuItem(t("Yana Aç"));
        openSide.addActionListener(e -> openSplitFile(tab.file));
        menu.add(split);
        menu.add(openSide);
        menu.addSeparator();
        menu.add(close);
        menu.add(closeOthers);
        menu.add(closeAll);
        menu.addSeparator();
        menu.add(copyPath);
        menu.show(component, x, y);
    }

    private boolean closeTab(EditorTab tab) {
        if (tab == null || !openTabs.containsValue(tab)) return false;
        editorTabs.setSelectedComponent(tab);
        if (!confirmAbandonOrSave()) return false;
        removeTab(tab, true);
        return true;
    }

    private void closeTabSilently(EditorTab tab) {
        removeTab(tab, false);
    }

    private void removeTab(EditorTab tab, boolean selectNeighbor) {
        int index = editorTabs.indexOfComponent(tab);
        if (index < 0) return;
        openTabs.remove(tab.file);
        tabsUpdating = true;
        editorTabs.remove(tab);
        tabsUpdating = false;
        if (editorTabs.getTabCount() == 0) {
            activeTab = null;
            syncActiveTab();
        } else if (selectNeighbor) {
            editorTabs.setSelectedIndex(Math.min(index, editorTabs.getTabCount() - 1));
            syncActiveTab();
        } else {
            syncActiveTab();
        }
    }

    private void closeOtherTabs(EditorTab keep) {
        for (EditorTab tab : new ArrayList<>(openTabs.values())) {
            if (tab == keep) continue;
            if (!closeTab(tab)) break;
        }
        if (keep != null && openTabs.containsValue(keep)) editorTabs.setSelectedComponent(keep);
    }

    private void closeAllTabs() {
        for (EditorTab tab : new ArrayList<>(openTabs.values())) {
            if (!closeTab(tab)) break;
        }
    }

    private boolean confirmCloseAllTabs() {
        for (EditorTab tab : new ArrayList<>(openTabs.values())) {
            if (!tab.dirty) continue;
            editorTabs.setSelectedComponent(tab);
            if (!confirmAbandonOrSave()) return false;
        }
        return true;
    }

    private void toggleSplit() {
        if (splitVisible) {
            closeSplit();
            return;
        }
        splitVisible = true;
        splitPanel.setVisible(true);
        editorSplit.setRightComponent(splitPanel);
        editorSplit.setDividerLocation(0.5);
        if (activeTab != null) openSplitFile(activeTab.file);
        else {
            splitFile = null;
            splitDirty = false;
            splitSuppressDirty = true;
            splitEditor.setText("");
            splitSuppressDirty = false;
            splitTitle.setText(t("Split view"));
            splitSaveButton.setEnabled(false);
        }
        editorSplit.revalidate();
        editorSplit.repaint();
    }

    private void openSplitFile(Path file) {
        if (file == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !FileUtil.isSafeProjectPath(meta.rootDir(), file)) return;
        try {
            splitSuppressDirty = true;
            splitEditor.setFile(file, Files.readString(file, StandardCharsets.UTF_8));
            splitFile = file.toAbsolutePath().normalize();
            splitDirty = false;
            splitTitle.setText(relative(splitFile));
            splitSaveButton.setEnabled(false);
            if (!splitVisible) {
                splitVisible = true;
                splitPanel.setVisible(true);
                editorSplit.setRightComponent(splitPanel);
                editorSplit.setDividerLocation(0.5);
                editorSplit.revalidate();
                editorSplit.repaint();
            }
        } catch (IOException ex) {
            appendConsole(t("Dosya açılamadı: %s", ex.getMessage()));
        } finally {
            splitSuppressDirty = false;
        }
    }

    private void markSplitDirty() {
        if (splitSuppressDirty || splitFile == null) return;
        splitDirty = true;
        splitTitle.setText("● " + relative(splitFile));
        splitSaveButton.setEnabled(true);
    }

    private void saveSplit() {
        if (splitFile == null) return;
        try {
            FileUtil.recoveryCopy(meta.rootDir(), splitFile);
            FileUtil.writeStringAtomic(splitFile, splitEditor.getText());
            splitDirty = false;
            splitTitle.setText(relative(splitFile));
            splitSaveButton.setEnabled(false);
            appendConsole(t("Kaydedildi: %s", relative(splitFile)));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Kaydetme hatası"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean confirmSplitClose() {
        if (!splitDirty || splitFile == null) return true;
        Object[] options = {t("Kaydet"), t("Değişiklikleri At"), t("İptal")};
        int choice = JOptionPane.showOptionDialog(this,
                t("%s dosyasında kaydedilmemiş değişiklikler var.", relative(splitFile)),
                t("Kaydedilmemiş değişiklik"), JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice == 0) { saveSplit(); return !splitDirty; }
        return choice == 1;
    }

    private void closeSplit() {
        if (!confirmSplitClose()) return;
        splitVisible = false;
        splitDirty = false;
        splitFile = null;
        splitPanel.setVisible(false);
        editorSplit.setRightComponent(null);
        splitEditor.setText("");
        splitTitle.setText(t("Split view"));
        splitSaveButton.setEnabled(false);
        editorSplit.revalidate();
        editorSplit.repaint();
    }

    private final class EditorTab extends JPanel {
        private final Path file;
        private final SyntaxEditorPane pane;
        private final JScrollPane scroll;
        private JLabel titleLabel;
        private JButton closeButton;
        private JPanel header;
        private boolean dirty;
        private boolean suppressDirty;

        private EditorTab(Path file, String text) {
            super(new BorderLayout());
            this.file = file.toAbsolutePath().normalize();
            this.pane = new SyntaxEditorPane();
            this.pane.setFile(this.file, text);
            this.scroll = Ui.scroll(pane);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(Theme.EDITOR);
            scroll.setRowHeaderView(new LineNumberView(pane));
            if (scroll.getRowHeader() != null) scroll.getRowHeader().setBackground(Theme.EDITOR);
            setBackground(Theme.EDITOR);
            JPanel body = new JPanel(new BorderLayout());
            body.setBackground(Theme.EDITOR);
            JPanel crumb = new JPanel(new BorderLayout());
            crumb.setBackground(Theme.EDITOR);
            crumb.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                    new EmptyBorder(4, 14, 4, 12)));
            crumb.add(Ui.label(relative(this.file), 10, Font.PLAIN, Theme.MUTED), BorderLayout.WEST);
            body.add(crumb, BorderLayout.NORTH);
            body.add(scroll, BorderLayout.CENTER);
            add(body, BorderLayout.CENTER);
        }
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
        JScrollPane scroll = Ui.scroll(pagesList);
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

    private void refreshAfterAiEdit() {
        reloadTree();
        for (EditorTab tab : new ArrayList<>(openTabs.values())) {
            if (!Files.isRegularFile(tab.file)) {
                appendConsole(t("AI deleted the open file: %s", relative(tab.file)));
                closeTabSilently(tab);
                continue;
            }
            if (tab.dirty) {
                appendConsole(t("AI updated project files. %s has unsaved changes, so it was not reloaded automatically.", relative(tab.file)));
                continue;
            }
            try {
                setTabText(tab, Files.readString(tab.file, StandardCharsets.UTF_8));
                appendConsole(t("AI updated: %s", relative(tab.file)));
            } catch (IOException ex) {
                appendConsole(t("Could not reload the file after AI edits: %s", ex.getMessage()));
            }
        }
        if (splitFile != null) {
            if (!Files.isRegularFile(splitFile)) {
                closeSplit();
            } else if (!splitDirty) {
                try {
                    splitSuppressDirty = true;
                    splitEditor.setFile(splitFile, Files.readString(splitFile, StandardCharsets.UTF_8));
                    splitSuppressDirty = false;
                } catch (IOException ignored) { }
            }
        }
        syncActiveTab();
    }

    private void setTabText(EditorTab tab, String text) {
        tab.suppressDirty = true;
        try {
            tab.pane.setFile(tab.file, text);
        } finally {
            tab.suppressDirty = false;
        }
        tab.dirty = false;
        updateTabTitle(tab);
    }

    private void openProjectSettings() {
        if (!saveAllTabs()) return;
        new ProjectSettingsDialog(this, meta.rootDir(), () -> {
            appendConsole(t("Proje ayarları kaydedildi."));
            reloadTree();
        }).setVisible(true);
    }

    private void openSettings() {
        if (!saveAllTabs()) return;
        SettingsDialog dialog = new SettingsDialog(this);
        dialog.setVisible(true);
        aiPanel.reloadConfig();
        if (dialog.languageChanged()) {
            if (!saveAllTabs()) return;
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
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, InputEvent.CTRL_DOWN_MASK), "splitEditor");
        am.put("splitEditor", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { toggleSplit(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), "closeTab");
        am.put("closeTab", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { closeTab(activeTab); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK), "nextTab");
        am.put("nextTab", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (editorTabs.getTabCount() == 0) return;
                editorTabs.setSelectedIndex((editorTabs.getSelectedIndex() + 1) % editorTabs.getTabCount());
            }
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

    private static final java.util.Set<String> TREE_SKIP = java.util.Set.of(
            ".git", ".gradle", ".idea", ".eluajs", "build", "release", "out", "dist", "node_modules");

    private DefaultMutableTreeNode buildNode(Path p, int depth) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FileNode(p));
        if (Files.isDirectory(p) && depth < 8) {
            try (var s = Files.list(p)) {
                s.filter(x -> !Files.isSymbolicLink(x))
                        .filter(x -> !TREE_SKIP.contains(x.getFileName().toString()))
                        .sorted(Comparator.comparing((Path x) -> !Files.isDirectory(x))
                                .thenComparing(x -> x.getFileName().toString().toLowerCase()))
                        .forEach(x -> node.add(buildNode(x, depth + 1)));
            } catch (IOException ignored) { }
        }
        return node;
    }

    private Path treeTarget() {
        TreePath selection = tree.getSelectionPath();
        if (selection == null) return meta.rootDir();
        if (!(selection.getLastPathComponent() instanceof DefaultMutableTreeNode node)) return meta.rootDir();
        if (!(node.getUserObject() instanceof FileNode file)) return meta.rootDir();
        return Files.isDirectory(file.path) ? file.path : file.path.getParent();
    }

    private Path treeSelectedPath() {
        TreePath selection = tree.getSelectionPath();
        if (selection == null) return null;
        if (!(selection.getLastPathComponent() instanceof DefaultMutableTreeNode node)) return null;
        if (!(node.getUserObject() instanceof FileNode file)) return null;
        return file.path;
    }

    private void showTreeMenu(MouseEvent e) {
        TreePath selection = tree.getPathForLocation(e.getX(), e.getY());
        if (selection != null) tree.setSelectionPath(selection);
        Path target = treeTarget();
        Path selected = treeSelectedPath();
        boolean canEdit = selected != null && !selected.equals(meta.rootDir());

        JPopupMenu menu = new JPopupMenu();
        JMenuItem newFile = new JMenuItem(t("Yeni Dosya"));
        newFile.addActionListener(a -> newTreeFile(target));
        JMenuItem newFolder = new JMenuItem(t("Yeni Klasör"));
        newFolder.addActionListener(a -> newTreeFolder(target));
        JMenuItem newLayout = new JMenuItem(t("Yeni XML Layout"));
        newLayout.addActionListener(a -> newTreeLayout());
        JMenuItem rename = new JMenuItem(t("Yeniden Adlandır"));
        rename.setEnabled(canEdit);
        rename.addActionListener(a -> renameTreePath(selected));
        JMenuItem duplicate = new JMenuItem(t("Çoğalt"));
        duplicate.setEnabled(canEdit);
        duplicate.addActionListener(a -> duplicateTreePath(selected));
        JMenuItem delete = new JMenuItem(t("Sil"));
        delete.setEnabled(canEdit);
        delete.addActionListener(a -> deleteTreePath(selected));
        JMenuItem copyPath = new JMenuItem(t("Yolu Kopyala"));
        copyPath.setEnabled(selected != null);
        copyPath.addActionListener(a -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(selected.toString()), null));
        JMenuItem reveal = new JMenuItem(t("Klasörü Aç"));
        reveal.setEnabled(selected != null);
        reveal.addActionListener(a -> openInSystem(selected.getParent()));

        menu.add(newFile);
        menu.add(newFolder);
        menu.add(newLayout);
        menu.addSeparator();
        menu.add(rename);
        menu.add(duplicate);
        menu.add(delete);
        menu.addSeparator();
        menu.add(copyPath);
        menu.add(reveal);
        menu.show(tree, e.getX(), e.getY());
    }

    private void newTreeFile(Path parent) {
        String name = JOptionPane.showInputDialog(this, t("Dosya adı"), t("YeniDosya.txt"));
        if (name == null || name.isBlank()) return;
        try {
            Path made = ProjectFileManager.createFile(meta.rootDir(), parent, name.trim());
            reloadTree();
            openFile(made);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Dosya oluşturulamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void newTreeFolder(Path parent) {
        String name = JOptionPane.showInputDialog(this, t("Klasör adı"), t("YeniKlasör"));
        if (name == null || name.isBlank()) return;
        try {
            ProjectFileManager.createDirectory(meta.rootDir(), parent, name.trim());
            reloadTree();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Klasör oluşturulamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void newTreeLayout() {
        String name = JOptionPane.showInputDialog(this, t("Layout adı"), "activity_new");
        if (name == null || name.isBlank()) return;
        try {
            Path made = ProjectFileManager.createLayout(meta.rootDir(), name.trim());
            reloadTree();
            openFile(made);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Layout oluşturulamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renameTreePath(Path path) {
        if (path == null || !saveAllTabs()) return;
        String name = JOptionPane.showInputDialog(this, t("Yeni ad"), path.getFileName().toString());
        if (name == null || name.isBlank() || name.equals(path.getFileName().toString())) return;
        try {
            Path made = ProjectFileManager.rename(meta.rootDir(), path, name.trim());
            closeTabsUnder(path);
            reloadTree();
            if (Files.isRegularFile(made)) openFile(made);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Yeniden adlandırılamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void duplicateTreePath(Path path) {
        if (path == null || !saveAllTabs()) return;
        try {
            Path made = ProjectFileManager.duplicate(meta.rootDir(), path);
            reloadTree();
            if (Files.isRegularFile(made)) openFile(made);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Çoğaltılamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteTreePath(Path path) {
        if (path == null) return;
        int choice = JOptionPane.showConfirmDialog(this,
                t("'%s' silinsin mi?", meta.rootDir().relativize(path)),
                t("Sil"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION || !saveAllTabs()) return;
        try {
            ProjectFileManager.delete(meta.rootDir(), path);
            closeTabsUnder(path);
            reloadTree();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Silme hatası"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void closeTabsUnder(Path path) {
        Path base = path.toAbsolutePath().normalize();
        for (EditorTab tab : new ArrayList<>(openTabs.values())) {
            if (tab.file.equals(base) || tab.file.startsWith(base)) closeTabSilently(tab);
        }
    }

    private void openInSystem(Path path) {
        if (path == null) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                new ProcessBuilder("xdg-open", path.toString()).start();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Klasör açılamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void treeSelection(TreeSelectionEvent e) {
        Object n = tree.getLastSelectedPathComponent();
        if (!(n instanceof DefaultMutableTreeNode dm) || !(dm.getUserObject() instanceof FileNode fn)) return;
        if (Files.isRegularFile(fn.path) && FileUtil.isTextFile(fn.path)) openFile(fn.path);
    }

    private void openMainActivity() {
        Path main = ProjectLayout.mainSource(meta.rootDir());
        if (main != null && Files.isRegularFile(main)) openFile(main);
    }

    private void openFile(Path p) {
        if (p == null || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                || !FileUtil.isSafeProjectPath(meta.rootDir(), p)) return;
        Path file = p.toAbsolutePath().normalize();
        EditorTab existing = openTabs.get(file);
        if (existing != null) {
            editorTabs.setSelectedComponent(existing);
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            EditorTab tab = createTab(file, text);
            openTabs.put(file, tab);
            tabsUpdating = true;
            editorTabs.addTab(file.getFileName().toString(), tab);
            editorTabs.setTabComponentAt(editorTabs.getTabCount() - 1, tabHeader(tab));
            editorTabs.setSelectedComponent(tab);
            tabsUpdating = false;
            syncActiveTab();
        } catch (IOException ex) {
            appendConsole(t("Dosya açılamadı: %s", ex.getMessage()));
        }
    }

    private void saveCurrent() {
        saveTab(activeTab, false);
    }

    private void saveCurrentSilently() {
        if (activeTab != null && activeTab.dirty) saveTab(activeTab, true);
    }

    private boolean saveTab(EditorTab tab, boolean quiet) {
        if (tab == null) return true;
        try {
            FileUtil.recoveryCopy(meta.rootDir(), tab.file);
            FileUtil.writeStringAtomic(tab.file, tab.pane.getText());
            tab.dirty = false;
            if (tab == activeTab) dirty = false;
            updateTabTitle(tab);
            if (tab == activeTab) updateEditorStatus();
            if (!quiet) appendConsole(t("Kaydedildi: %s", relative(tab.file)));
            return true;
        } catch (IOException ex) {
            if (!quiet) JOptionPane.showMessageDialog(this, ex.getMessage(), t("Kaydetme hatası"), JOptionPane.ERROR_MESSAGE);
            else appendConsole(t("Kaydedilemedi: %s", ex.getMessage()));
            return false;
        }
    }

    private boolean saveAllTabs() {
        for (EditorTab tab : new ArrayList<>(openTabs.values())) {
            if (!tab.dirty) continue;
            if (!saveTab(tab, false)) return false;
        }
        return true;
    }

    private boolean confirmAbandonOrSave() {
        EditorTab tab = activeTab;
        if (tab == null || !tab.dirty) return true;
        Object[] options = {t("Kaydet"), t("Değişiklikleri At"), t("İptal")};
        int choice = JOptionPane.showOptionDialog(this,
                t("%s dosyasında kaydedilmemiş değişiklikler var.", relative(tab.file)),
                t("Kaydedilmemiş değişiklik"), JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice == 0) { saveCurrent(); return !tab.dirty; }
        if (choice == 1) {
            tab.dirty = false;
            dirty = false;
            updateTabTitle(tab);
            updateEditorStatus();
            return true;
        }
        return false;
    }

    private void confirmClose() {
        if (!confirmCloseAllTabs()) return;
        if (!confirmSplitClose()) return;
        dispose();
    }

    private void openFileAtLine(Path file, Integer line) {
        if (file == null || !Files.isRegularFile(file) || !FileUtil.isTextFile(file)
                || !FileUtil.isSafeProjectPath(meta.rootDir(), file)) {
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
        showBottomCard("search");
        searchQuery.requestFocusInWindow();
        searchQuery.selectAll();
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

        EditorTab deletedTab = null;
        for (EditorTab tab : new ArrayList<>(openTabs.values())) {
            if (tab.file.getFileName().toString().equals(deleted + ".java")) deletedTab = tab;
        }
        boolean deletedWasActive = deletedTab != null && deletedTab == activeTab;
        if (deletedTab != null) closeTabSilently(deletedTab);
        reloadTree();
        if (deletedWasActive && openTabs.isEmpty()) openMainActivity();
        appendConsole("\n" + t("--- Sayfa Silindi ---"));
        appendConsole(t("%s.java ve Manifest kaydı kaldırıldı.", deleted));
        appendConsole(t("Not: Bu sayfaya giden Intent kodları varsa onları koddan siz kaldırmalısınız."));
    }

    private void validateProject() {
        saveCurrentSilently();
        ProjectValidator.Report r = ProjectValidator.validate(meta.rootDir());
        appendConsole("\n" + t("--- Doğrulama ---"));
        r.messages().forEach(this::appendConsole);
        showProblems(r.problems());
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
            setForeground(Theme.MUTED);
            setBackground(Theme.EDITOR);
            text.addPropertyChangeListener("font", ev -> {
                setFont(Theme.mono(Math.max(9, text.getFont().getSize() - 2)));
                revalidate();
                repaint();
            });
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
