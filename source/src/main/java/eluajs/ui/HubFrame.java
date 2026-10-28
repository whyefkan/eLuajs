package eluajs.ui;

import eluajs.android.ToolchainManager;
import eluajs.model.ProjectMeta;
import eluajs.project.ProjectStore;
import eluajs.update.UpdateManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * eLuajs project hub.
 *
 * v1.3 uses a VS Code-like shell: slim activity bar, explorer sidebar, a
 * context-aware welcome/detail surface in the middle and a compact status bar.
 * Project discovery still runs off the EDT so the hub stays responsive.
 */
public final class HubFrame extends JFrame {
    public static final String VERSION = eluajs.util.AppInfo.version();
    private static final int SIDEBAR_WIDTH = 264;

    private final ProjectStore store = new ProjectStore();
    private final CardLayout contentCards = new CardLayout();
    private final JPanel contentPanel = new JPanel(contentCards);
    private final JLabel projectCount = Ui.badge(t("%d proje", 0), Theme.BLUE);
    private final JLabel sidebarCount = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
    private final DefaultListModel<ProjectMeta> sidebarModel = new DefaultListModel<>();
    private final JList<ProjectMeta> sidebarList = new JList<>(sidebarModel);
    private final JLabel sidebarHint = Ui.label(t("Seç: detay  •  Çift tık: aç"), 10, Font.PLAIN, Theme.MUTED);
    private final AtomicInteger refreshGeneration = new AtomicInteger();
    private ProjectMeta selected;

    public HubFrame() {
        super(t("eLuajs %s — Hafif Android Java IDE", VERSION));
        WindowChrome.install(this);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setIconImage(Ui.appIcon());
        setSize(1180, 760);
        setMinimumSize(new Dimension(960, 620));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(activityBar(), BorderLayout.WEST);
        left.add(sidebar(), BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG);
        body.add(left, BorderLayout.WEST);
        body.add(content(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);
        refresh();
        Timer startupUpdate = new Timer(1500, e -> UpdateManager.checkOnStartup(this,
                release -> new UpdateDialog(this, release).setVisible(true)));
        startupUpdate.setRepeats(false);
        startupUpdate.start();
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 52));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(0, 16, 0, 16)));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        JLabel icon = new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
        brand.add(icon);
        brand.add(Ui.label("eLuajs", 15, Font.BOLD, Theme.TEXT));
        brand.add(Ui.badge("v" + VERSION, Theme.ACCENT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);

        JLabel tagline = Ui.label(t("Hafif Android Java IDE"), 11, Font.PLAIN, Theme.MUTED);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(tagline);
        right.add(WindowChrome.controls(this));
        bar.add(Ui.centerVertically(right), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JComponent activityBar() {
        JPanel bar = Ui.activityBar();

        JPanel logoWrap = new JPanel(new GridBagLayout());
        logoWrap.setOpaque(false);
        logoWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        JLabel logo = new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(32, 32, Image.SCALE_SMOOTH)));
        logoWrap.add(logo);
        bar.add(logoWrap);
        bar.add(Box.createVerticalStrut(14));

        Ui.ActivityButton projects = Ui.activityButton("files", t("Projeler"), () -> sidebarList.requestFocusInWindow());
        projects.setActive(true);
        bar.add(projects);
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("plus", t("Yeni proje"), this::newProject));
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("import", t("Proje İçe Aktar"), this::importProject));
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("home", t("Başlangıç ekranı"), this::showWelcome));
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("folder", t("Projeler klasörünü aç"), this::openProjectsFolder));

        bar.add(Box.createVerticalGlue());
        bar.add(Ui.activityButton("settings", t("Ayarlar"), this::openSettings));
        bar.add(Box.createVerticalStrut(4));
        bar.add(Ui.activityButton("about", t("Hakkında"), this::showAbout));
        return bar;
    }

    private JComponent sidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.SIDEBAR);
        side.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 1));
        side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER_SOFT));

        JPanel head = new JPanel(new BorderLayout(6, 0));
        head.setBackground(Theme.SIDEBAR);
        head.setBorder(new EmptyBorder(14, 16, 10, 10));

        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        title.setOpaque(false);
        title.add(Ui.sectionLabel(t("PROJELER")));
        title.add(sidebarCount);
        head.add(title, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        JButton add = compact("＋", t("Yeni proje"));
        add.addActionListener(e -> newProject());
        JButton importProject = compact("↓", t("Proje İçe Aktar"));
        importProject.addActionListener(e -> importProject());
        JButton refresh = compact("⟳", t("Yenile"));
        refresh.addActionListener(e -> refresh());
        actions.add(add);
        actions.add(importProject);
        actions.add(refresh);
        head.add(actions, BorderLayout.EAST);
        side.add(head, BorderLayout.NORTH);

        sidebarList.setBackground(Theme.SIDEBAR);
        sidebarList.setForeground(Theme.TEXT);
        sidebarList.setSelectionBackground(Theme.PANEL_2);
        sidebarList.setSelectionForeground(Theme.TEXT);
        sidebarList.setFixedCellHeight(60);
        sidebarList.setCellRenderer(new ProjectListRenderer());
        sidebarList.setBorder(new EmptyBorder(0, 0, 0, 0));
        sidebarList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            ProjectMeta meta = sidebarList.getSelectedValue();
            if (meta != null) showDetail(meta);
        });
        sidebarList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int index = sidebarList.locationToIndex(e.getPoint());
                if (index < 0 || index >= sidebarModel.size()) return;
                sidebarList.setSelectedIndex(index);
                ProjectMeta meta = sidebarModel.get(index);
                if (SwingUtilities.isRightMouseButton(e)) {
                    projectMenu(meta).show(sidebarList, e.getX(), e.getY());
                } else if (e.getClickCount() == 2) {
                    openProject(meta);
                }
            }
        });

        JScrollPane scroll = Ui.scroll(sidebarList);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.SIDEBAR);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        side.add(scroll, BorderLayout.CENTER);

        JPanel hint = new JPanel(new BorderLayout());
        hint.setBackground(Theme.SIDEBAR);
        hint.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_SOFT),
                new EmptyBorder(9, 16, 11, 12)));
        hint.add(sidebarHint, BorderLayout.WEST);
        side.add(hint, BorderLayout.SOUTH);
        return side;
    }

    private JButton compact(String text, String tooltip) {
        JButton b = Ui.button(text, false);
        b.setToolTipText(tooltip);
        b.setFont(Theme.font(12, Font.BOLD));
        b.setBorder(new EmptyBorder(4, 9, 4, 9));
        return b;
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(22, 28, 20, 28));

        contentPanel.setOpaque(false);
        contentPanel.add(welcomeView(), "welcome");
        contentPanel.add(new ScrollBody(), "detail");
        root.add(contentPanel, BorderLayout.CENTER);
        return root;
    }

    private JComponent welcomeView() {
        ScrollBody body = new ScrollBody();
        body.setBorder(new EmptyBorder(6, 4, 12, 10));

        JPanel hero = new JPanel(new BorderLayout(18, 0));
        hero.setOpaque(false);
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        JLabel icon = new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(68, 68, Image.SCALE_SMOOTH)));
        hero.add(icon, BorderLayout.WEST);

        JPanel heroText = new JPanel();
        heroText.setOpaque(false);
        heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));

        JPanel titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLine.setOpaque(false);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.add(Ui.label("eLuajs", 32, Font.BOLD, Theme.TEXT));
        titleLine.add(projectCount);
        heroText.add(titleLine);
        heroText.add(Box.createVerticalStrut(8));

        JLabel tagline = Ui.label(t("Hafif Android Java IDE  •  Linux x86_64"), 12, Font.PLAIN, Theme.ACCENT);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroText.add(tagline);
        heroText.add(Box.createVerticalStrut(8));

        JLabel desc = Ui.label(t("Hafif ve hızlı Android Java IDE. Kodla, doğrula, debug APK veya imzalı Play Store AAB al."), 12, Font.PLAIN, Theme.MUTED);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroText.add(desc);
        hero.add(heroText, BorderLayout.CENTER);
        body.add(hero);
        body.add(Box.createVerticalStrut(18));

        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        quick.setOpaque(false);
        quick.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton create = Ui.button("＋  " + t("Yeni Proje"), true);
        create.setPreferredSize(new Dimension(150, 40));
        create.addActionListener(e -> newProject());
        JButton importProject = Ui.button("↓  " + t("İçe Aktar"), false);
        importProject.setPreferredSize(new Dimension(150, 40));
        importProject.addActionListener(e -> importProject());
        JButton folder = Ui.button("▦  " + t("Projeler Klasörü"), false);
        folder.setPreferredSize(new Dimension(165, 40));
        folder.addActionListener(e -> openProjectsFolder());
        JButton reload = Ui.button("⟳  " + t("Yenile"), false);
        reload.setPreferredSize(new Dimension(110, 40));
        reload.addActionListener(e -> refresh());
        quick.add(create);
        quick.add(importProject);
        quick.add(folder);
        quick.add(reload);
        body.add(quick);
        body.add(Box.createVerticalStrut(22));

        body.add(sectionRow(t("BAŞLARKEN")));
        body.add(Box.createVerticalStrut(9));
        body.add(stepCard("1", t("Proje oluştur"), t("Uygulama adı, paket adı ve sürümü belirle; gerçek Gradle projesi oluşsun.")));
        body.add(Box.createVerticalStrut(8));
        body.add(stepCard("2", t("Kodla ve doğrula"), t("Java + XML dosyalarını düzenle. Ctrl+Space tamamlama, Ctrl+Shift+F arama.")));
        body.add(Box.createVerticalStrut(8));
        body.add(stepCard("3", t("Çıktı al"), t("Doğrula; debug APK veya imzalı Play Store AAB üret.")));
        body.add(Box.createVerticalStrut(22));

        JPanel bottom = new JPanel(new GridLayout(1, 2, 12, 0));
        bottom.setOpaque(false);
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 158));
        bottom.add(environmentCard());
        bottom.add(tipsCard());
        body.add(bottom);
        return scroll(body);
    }

    private JComponent sectionRow(String text) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Ui.sectionLabel(text), BorderLayout.WEST);
        JPanel line = new JPanel();
        line.setBackground(Theme.BORDER_SOFT);
        line.setPreferredSize(new Dimension(1, 1));
        row.add(line, BorderLayout.SOUTH);
        return row;
    }

    private JComponent stepCard(String number, String title, String text) {
        JPanel card = Ui.softCard(new BorderLayout(14, 0));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(new EmptyBorder(11, 14, 11, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        JPanel numberWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        numberWrap.setOpaque(false);
        numberWrap.setPreferredSize(new Dimension(40, 1));
        numberWrap.add(Ui.badge(number, Theme.ACCENT));
        card.add(numberWrap, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel t = Ui.label(title, 13, Font.BOLD, Theme.TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel d = Ui.label(text, 11, Font.PLAIN, Theme.MUTED);
        d.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(t);
        info.add(Box.createVerticalStrut(2));
        info.add(d);
        card.add(info, BorderLayout.CENTER);
        return card;
    }

    private JComponent environmentCard() {
        JPanel card = Ui.card(new BorderLayout(0, 11));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.add(Ui.sectionLabel(t("ORTAM DURUMU")), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
        grid.setOpaque(false);
        grid.add(envChip("JDK", ToolchainManager.javaReady()));
        grid.add(envChip("Android SDK", ToolchainManager.sdkReady()));
        grid.add(envChip("Gradle", ToolchainManager.gradleReady()));
        grid.add(envChip("Build Tools", ToolchainManager.buildToolsReady()));
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JComponent envChip(String name, boolean ready) {
        JPanel chip = new JPanel(new BorderLayout(6, 0));
        chip.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        left.setOpaque(false);
        left.add(Ui.label(ready ? "●" : "○", 10, Font.PLAIN, ready ? Theme.ACCENT : Theme.WARNING));
        left.add(Ui.label(name, 11, Font.PLAIN, Theme.TEXT_SOFT));
        chip.add(left, BorderLayout.WEST);
        chip.add(Ui.label(ready ? t("Hazır") : t("Gerekli"), 10, Font.BOLD, ready ? Theme.ACCENT : Theme.WARNING), BorderLayout.EAST);
        return chip;
    }

    private JComponent tipsCard() {
        JPanel card = Ui.card(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.add(Ui.sectionLabel(t("İPUÇLARI")), BorderLayout.NORTH);
        JLabel tips = new JLabel("<html>"
                + "<b>Ctrl+S</b> &nbsp;" + t("Kaydet") + "<br>"
                + "<b>Ctrl+Shift+F</b> &nbsp;" + t("Projede ara") + "<br>"
                + "<b>Ctrl+Shift+N</b> &nbsp;" + t("Yeni Java sınıfı") + "<br>"
                + "<b>Ctrl+Space</b> &nbsp;" + t("Kod tamamlama")
                + "</html>");
        tips.setFont(Theme.font(11, Font.PLAIN));
        tips.setForeground(Theme.TEXT_SOFT);
        card.add(tips, BorderLayout.CENTER);
        return card;
    }

    private JComponent detailView(ProjectMeta meta) {
        ScrollBody body = new ScrollBody();
        body.setBorder(new EmptyBorder(6, 4, 12, 10));

        JPanel head = new JPanel(new BorderLayout(18, 0));
        head.setOpaque(false);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        JLabel icon = new JLabel(new ImageIcon(Icons.projectIcon(meta.appName(), meta.packageName(), 68)));
        head.add(icon, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JPanel titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLine.setOpaque(false);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.add(Ui.label(meta.appName(), 27, Font.BOLD, Theme.TEXT));
        titleLine.add(Ui.badge("v" + meta.versionName(), Theme.BLUE));
        info.add(titleLine);
        info.add(Box.createVerticalStrut(7));

        JLabel pkg = Ui.label(meta.packageName(), 12, Font.PLAIN, Theme.ACCENT);
        pkg.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(pkg);
        info.add(Box.createVerticalStrut(8));

        JLabel modified = Ui.label(t("Son değişiklik  %s", lastModified(meta)), 11, Font.PLAIN, Theme.MUTED);
        modified.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(modified);
        head.add(info, BorderLayout.CENTER);
        body.add(head);
        body.add(Box.createVerticalStrut(20));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton open = Ui.button(t("Projeyi Aç  →"), true);
        open.setPreferredSize(new Dimension(158, 40));
        open.addActionListener(e -> openProject(meta));
        JButton folder = Ui.button("▦  " + t("Klasörü Aç"), false);
        folder.setPreferredSize(new Dimension(145, 40));
        folder.addActionListener(e -> openFolder(meta.rootDir()));
        boolean managed = store.isManaged(meta);
        JButton del = Ui.dangerButton(managed ? t("Sil") : t("Listeden Kaldır"));
        del.setPreferredSize(new Dimension(managed ? 74 : 140, 40));
        del.addActionListener(e -> delete(meta));
        actions.add(open);
        actions.add(folder);
        actions.add(del);
        body.add(actions);
        body.add(Box.createVerticalStrut(22));

        body.add(sectionRow(t("PROJE BİLGİLERİ")));
        body.add(Box.createVerticalStrut(9));

        JPanel props = Ui.card(new GridLayout(2, 2, 16, 12));
        props.setAlignmentX(Component.LEFT_ALIGNMENT);
        props.setBorder(new EmptyBorder(16, 18, 16, 18));
        props.setMaximumSize(new Dimension(Integer.MAX_VALUE, 116));
        props.add(property(t("Sürüm"), meta.versionName()));
        props.add(property("Version Code", String.valueOf(meta.versionCode())));
        props.add(property(t("Paket adı"), meta.packageName()));
        props.add(property(t("Proje konumu"), meta.rootDir().toString()));
        body.add(props);
        body.add(Box.createVerticalStrut(12));

        JPanel hint = Ui.softCard(new BorderLayout(12, 0));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(12, 15, 12, 15));
        hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        hint.add(Ui.label("▶", 15, Font.PLAIN, Theme.ACCENT), BorderLayout.WEST);
        hint.add(Ui.label(t("APK / AAB almak için projeyi aç ve üstteki Derle düğmesini kullan."), 11, Font.PLAIN, Theme.TEXT_SOFT), BorderLayout.CENTER);
        body.add(hint);
        return scroll(body);
    }

    private JComponent property(String label, String value) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel l = Ui.label(label, 10, Font.BOLD, Theme.MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel v = Ui.label(value, 12, Font.PLAIN, Theme.TEXT_SOFT);
        v.setAlignmentX(Component.LEFT_ALIGNMENT);
        v.setToolTipText(value);
        p.add(l);
        p.add(Box.createVerticalStrut(3));
        p.add(v);
        return p;
    }

    private JScrollPane scroll(JComponent body) {
        JScrollPane scroll = Ui.scroll(body);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private String lastModified(ProjectMeta meta) {
        try {
            return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(Files.getLastModifiedTime(meta.rootDir()).toMillis()));
        } catch (IOException ex) {
            return "-";
        }
    }

    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.STATUS_BG);
        bar.setBorder(new EmptyBorder(4, 12, 4, 12));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(Ui.statusLabel("eLuajs " + VERSION));
        left.add(Ui.statusLabel("Linux x86_64"));
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(Ui.statusLabel(ToolchainManager.javaReady() ? t("● JDK hazır") : t("○ JDK gerekli")));
        right.add(Ui.statusLabel(ToolchainManager.sdkReady() ? t("● Android SDK hazır") : t("○ Android SDK gerekli")));
        right.add(Ui.statusLabel("UTF-8"));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void refresh() {
        int generation = refreshGeneration.incrementAndGet();
        projectCount.setText("…");
        SwingWorker<List<ProjectMeta>, Void> worker = new SwingWorker<>() {
            @Override protected List<ProjectMeta> doInBackground() { return store.list(); }
            @Override protected void done() {
                if (generation != refreshGeneration.get()) return;
                try { renderProjects(get()); }
                catch (Exception ex) { renderProjects(List.of()); }
            }
        };
        worker.execute();
    }

    private void renderProjects(List<ProjectMeta> projects) {
        sidebarModel.clear();
        projectCount.setText(t("%d proje", projects.size()));
        sidebarCount.setText(String.valueOf(projects.size()));
        sidebarHint.setText(projects.isEmpty() ? t("Henüz proje yok") : t("Seç: detay  •  Çift tık: aç"));

        ProjectMeta stillThere = null;
        for (ProjectMeta meta : projects) {
            sidebarModel.addElement(meta);
            if (selected != null && selected.rootDir().equals(meta.rootDir())) stillThere = meta;
        }

        if (stillThere != null) {
            selected = stillThere;
            int index = sidebarModel.indexOf(stillThere);
            sidebarList.setSelectedIndex(index);
            showDetail(stillThere);
        } else {
            selected = null;
            showWelcome();
        }
    }

    private void showWelcome() {
        selected = null;
        sidebarList.clearSelection();
        contentCards.show(contentPanel, "welcome");
    }

    private void showDetail(ProjectMeta meta) {
        selected = meta;
        contentPanel.remove(1);
        contentPanel.add(detailView(meta), "detail", 1);
        contentCards.show(contentPanel, "detail");
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPopupMenu projectMenu(ProjectMeta meta) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem(t("Projeyi Aç"));
        open.addActionListener(e -> openProject(meta));
        JMenuItem reveal = new JMenuItem(t("Klasörü Aç"));
        reveal.addActionListener(e -> openFolder(meta.rootDir()));
        JMenuItem del = new JMenuItem(store.isManaged(meta) ? t("Sil") : t("Listeden Kaldır"));
        del.addActionListener(e -> delete(meta));
        menu.add(open);
        menu.add(reveal);
        menu.addSeparator();
        menu.add(del);
        return menu;
    }

    private void openProject(ProjectMeta meta) {
        selected = meta;
        new EditorFrame(meta, this::refresh).setVisible(true);
    }

    private void newProject() {
        NewProjectDialog dialog = new NewProjectDialog(this, store.projectsRoot());
        dialog.setVisible(true);
        ProjectMeta created = dialog.created();
        if (created != null) {
            selected = created;
            refresh();
            new EditorFrame(created, this::refresh).setVisible(true);
        }
    }

    private void importProject() {
        ImportProjectDialog dialog = new ImportProjectDialog(this);
        dialog.setVisible(true);
        ProjectMeta imported = dialog.imported();
        if (imported != null) {
            selected = imported;
            refresh();
            new EditorFrame(imported, this::refresh).setVisible(true);
        }
    }

    private void delete(ProjectMeta meta) {
        boolean managed = store.isManaged(meta);
        String message = managed
                ? t("'%s' projesi kalıcı olarak silinsin mi?\n\n%s", meta.appName(), meta.rootDir())
                : t("'%s' eLuajs listesinden kaldırılsın mı?\n\nProje dosyaları silinmez:\n%s", meta.appName(), meta.rootDir());
        int choice = JOptionPane.showConfirmDialog(this, message,
                managed ? t("Projeyi Sil") : t("Listeden Kaldır"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            if (selected != null && selected.rootDir().equals(meta.rootDir())) selected = null;
            store.delete(meta);
            refresh();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Silme hatası"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openProjectsFolder() {
        try {
            Files.createDirectories(store.projectsRoot());
            openFolder(store.projectsRoot());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Klasör açılamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openFolder(java.nio.file.Path path) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                JOptionPane.showMessageDialog(this, path.toString(), t("Proje klasörü"), JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, path.toString(), t("Proje klasörü"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openSettings() {
        SettingsDialog dialog = new SettingsDialog(this);
        dialog.setVisible(true);
        if (dialog.languageChanged()) Ui.restartUi();
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                t("eLuajs %s\nHafif Android Java IDE\n\nGerçek Android Gradle projeleri oluşturur, doğrular,\ndebug APK ve imzalı Play Store AAB üretir.\n\nEfkwn Mobile", VERSION),
                t("eLuajs Hakkında"), JOptionPane.INFORMATION_MESSAGE);
    }

    private static final class ScrollBody extends JPanel implements Scrollable {
        ScrollBody() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(20, visibleRect.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private final class ProjectListRenderer extends JPanel implements ListCellRenderer<ProjectMeta> {
        private final JLabel icon = new JLabel();
        private final JLabel name = Ui.label("", 13, Font.BOLD, Theme.TEXT);
        private final JLabel detail = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
        private final JLabel version = Ui.label("", 10, Font.BOLD, Theme.BLUE);

        ProjectListRenderer() {
            super(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(9, 14, 9, 11));
            icon.setPreferredSize(new Dimension(30, 30));
            add(icon, BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            top.setOpaque(false);
            top.setAlignmentX(Component.LEFT_ALIGNMENT);
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            top.add(name);
            top.add(Box.createHorizontalStrut(6));
            top.add(version);
            text.add(top);
            text.add(Box.createVerticalStrut(2));
            text.add(detail);
            add(text, BorderLayout.CENTER);
        }

        @Override public Component getListCellRendererComponent(JList<? extends ProjectMeta> list, ProjectMeta meta,
                int index, boolean selected, boolean hasFocus) {
            setBackground(selected ? Theme.PANEL_2 : Theme.SIDEBAR);
            icon.setIcon(new ImageIcon(Icons.projectIcon(meta.appName(), meta.packageName(), 28)));
            name.setText(meta.appName());
            detail.setText(meta.packageName());
            version.setText("v" + meta.versionName());
            return this;
        }
    }
}
