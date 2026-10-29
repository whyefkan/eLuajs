package eluajs.ui;

import eluajs.git.GitManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** Git status, stage, commit, pull/push and diff surface. */
public final class GitDialog extends JDialog {
    private final Path root;
    private final JLabel branch = Ui.label("—", 12, Font.BOLD, Theme.ACCENT);
    private final JLabel remote = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
    private final JLabel status = Ui.label("", 11, Font.PLAIN, Theme.MUTED);
    private final DefaultListModel<GitManager.Change> changes = new DefaultListModel<>();
    private final JList<GitManager.Change> changeList = new JList<>(changes);
    private final JTextArea message = new JTextArea(4, 30);
    private final JTextArea output = new JTextArea();
    private final JTextField branchField = Ui.field();
    private final JButton init = Ui.button(t("Git Başlat"), false);
    private final JButton refresh = Ui.button(t("Yenile"), false);
    private final JButton stage = Ui.button(t("Tümünü Sahnele"), false);
    private final JButton commit = Ui.button(t("Commit"), true);
    private final JButton pull = Ui.button(t("Pull"), false);
    private final JButton push = Ui.button(t("Push"), false);
    private final JButton diff = Ui.button(t("Diff"), false);
    private final JButton checkout = Ui.button(t("Checkout"), false);
    private final JButton newBranch = Ui.button(t("Yeni Dal"), false);
    private final JButton stageFile = Ui.button(t("Seçileni Sahnele"), false);
    private final JButton fetch = Ui.button(t("Fetch"), false);
    private final JButton history = Ui.button(t("Geçmiş"), false);
    private boolean busy;

    public GitDialog(Frame owner, Path projectRoot) {
        super(owner, t("Git"), true);
        WindowChrome.install(this);
        this.root = projectRoot;
        setIconImage(Ui.appIcon());
        setSize(980, 720);
        setMinimumSize(new Dimension(820, 620));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);
        add(content(), BorderLayout.CENTER);
        refresh();
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 54));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(0, 16, 0, 8)));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(24, 24, Image.SCALE_SMOOTH))));
        brand.add(Ui.label(t("Git"), 15, Font.BOLD, Theme.TEXT));
        brand.add(Ui.badge("Git", Theme.BLUE));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JComponent content() {
        JPanel rootPanel = new JPanel(new BorderLayout(0, 12));
        rootPanel.setBackground(Theme.BG);
        rootPanel.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel info = Ui.card(new BorderLayout(8, 4));
        info.setBorder(new EmptyBorder(12, 15, 12, 15));
        JPanel branchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        branchRow.setOpaque(false);
        branchRow.add(Ui.sectionLabel(t("DAL")));
        branchRow.add(branch);
        branchRow.add(remote);
        info.add(branchRow, BorderLayout.NORTH);
        JPanel branchActions = new JPanel(new BorderLayout(8, 0));
        branchActions.setOpaque(false);
        branchField.setToolTipText(t("Dal adı"));
        branchActions.add(branchField, BorderLayout.CENTER);
        JPanel branchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        branchButtons.setOpaque(false);
        checkout.addActionListener(e -> checkoutBranch());
        newBranch.addActionListener(e -> createBranch());
        branchButtons.add(checkout);
        branchButtons.add(newBranch);
        branchActions.add(branchButtons, BorderLayout.EAST);
        info.add(branchActions, BorderLayout.CENTER);
        rootPanel.add(info, BorderLayout.NORTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(5);
        mainSplit.setResizeWeight(0.44);
        mainSplit.setBackground(Theme.BORDER_SOFT);

        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        JPanel changesHead = new JPanel(new BorderLayout());
        changesHead.setOpaque(false);
        changesHead.add(Ui.sectionLabel(t("DEĞİŞİKLİKLER")), BorderLayout.WEST);
        JPanel changeActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        changeActions.setOpaque(false);
        stage.addActionListener(e -> stageAll());
        diff.addActionListener(e -> showDiff());
        stageFile.addActionListener(e -> stageSelected());
        fetch.addActionListener(e -> fetchRemote());
        history.addActionListener(e -> showHistory());
        changeActions.add(diff);
        changeActions.add(stageFile);
        changeActions.add(stage);
        changesHead.add(changeActions, BorderLayout.EAST);
        left.add(changesHead, BorderLayout.NORTH);
        changeList.setBackground(Theme.PANEL);
        changeList.setForeground(Theme.TEXT);
        changeList.setSelectionBackground(Theme.SELECTION);
        changeList.setSelectionForeground(Theme.TEXT);
        changeList.setFont(Theme.mono(11));
        changeList.setFixedCellHeight(26);
        changeList.setBorder(new EmptyBorder(4, 5, 4, 5));
        JScrollPane changeScroll = Ui.scroll(changeList);
        changeScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        changeScroll.getViewport().setBackground(Theme.PANEL);
        left.add(changeScroll, BorderLayout.CENTER);
        mainSplit.setLeftComponent(left);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        JPanel commitHead = new JPanel(new BorderLayout());
        commitHead.setOpaque(false);
        commitHead.add(Ui.sectionLabel(t("COMMIT")), BorderLayout.WEST);
        JPanel commitActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        commitActions.setOpaque(false);
        pull.addActionListener(e -> pull());
        push.addActionListener(e -> push());
        commit.addActionListener(e -> commit());
        commitActions.add(pull);
        commitActions.add(push);
        commitActions.add(commit);
        commitHead.add(commitActions, BorderLayout.EAST);
        right.add(commitHead, BorderLayout.NORTH);
        message.setFont(Theme.font(12, Font.PLAIN));
        message.setBackground(Theme.PANEL_2);
        message.setForeground(Theme.TEXT);
        message.setCaretColor(Theme.ACCENT);
        message.setLineWrap(true);
        message.setBorder(new EmptyBorder(8, 9, 8, 9));
        JScrollPane messageScroll = Ui.scroll(message);
        messageScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        right.add(messageScroll, BorderLayout.CENTER);
        mainSplit.setRightComponent(right);
        rootPanel.add(mainSplit, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setOpaque(false);
        JPanel bottomHead = new JPanel(new BorderLayout());
        bottomHead.setOpaque(false);
        bottomHead.add(Ui.sectionLabel(t("GIT ÇIKTISI")), BorderLayout.WEST);
        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        bottomActions.setOpaque(false);
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        init.addActionListener(e -> initRepo());
        refresh.addActionListener(e -> refresh());
        bottomActions.add(status);
        bottomActions.add(history);
        bottomActions.add(fetch);
        bottomActions.add(init);
        bottomActions.add(refresh);
        bottomActions.add(close);
        bottomHead.add(bottomActions, BorderLayout.EAST);
        bottom.add(bottomHead, BorderLayout.NORTH);
        output.setEditable(false);
        output.setFont(Theme.mono(11));
        output.setBackground(Theme.CONSOLE);
        output.setForeground(Theme.TEXT_SOFT);
        output.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane outputScroll = Ui.scroll(output);
        outputScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        outputScroll.setPreferredSize(new Dimension(1, 150));
        bottom.add(outputScroll, BorderLayout.CENTER);
        rootPanel.add(bottom, BorderLayout.SOUTH);
        return rootPanel;
    }

    private void refresh() {
        if (busy) return;
        setBusy(true, t("Git durumu okunuyor..."));
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private String branchName = "";
            private String remoteText = "";
            private boolean repo;
            private boolean gitAvailable;
            private List<GitManager.Change> found = List.of();

            @Override protected Void doInBackground() {
                gitAvailable = GitManager.available();
                repo = gitAvailable && GitManager.isRepository(root);
                if (repo) {
                    branchName = GitManager.branch(root);
                    remoteText = GitManager.remote(root);
                    found = GitManager.changes(root);
                }
                return null;
            }

            @Override protected void done() {
                setBusy(false, "");
                changes.clear();
                found.forEach(changes::addElement);
                branch.setText(branchName.isBlank() ? "—" : branchName);
                branchField.setText(branchName);
                remote.setText(remoteText.isBlank() ? t("Uzak depo yok") : remoteText);
                init.setVisible(gitAvailable && !repo);
                stage.setEnabled(repo);
                commit.setEnabled(repo);
                pull.setEnabled(repo);
                push.setEnabled(repo);
                diff.setEnabled(repo);
                checkout.setEnabled(repo);
                newBranch.setEnabled(repo);
                if (!gitAvailable) status.setText(t("Git bulunamadı."));
                else if (!repo) status.setText(t("Henüz Git deposu değil."));
                else status.setText(t("%d değişiklik", changes.size()));
            }
        };
        worker.execute();
    }

    private void setBusy(boolean value, String text) {
        busy = value;
        refresh.setEnabled(!value);
        if (text != null && !text.isBlank()) status.setText(text);
        status.setForeground(Theme.MUTED);
    }

    private void append(String text) {
        if (text == null || text.isBlank()) return;
        output.append(text);
        if (!text.endsWith("\n")) output.append("\n");
        output.setCaretPosition(output.getDocument().getLength());
    }

    private void act(String label, Supplier<GitManager.Result> action) {
        if (busy) return;
        setBusy(true, label);
        append("$ " + label);
        SwingWorker<GitManager.Result, Void> worker = new SwingWorker<>() {
            @Override protected GitManager.Result doInBackground() { return action.get(); }
            @Override protected void done() {
                try {
                    GitManager.Result result = get();
                    append(result.output());
                    status.setText(result.ok() ? t("Tamamlandı") : t("Hata (kod %d)", result.exitCode()));
                    status.setForeground(result.ok() ? Theme.ACCENT : Theme.DANGER);
                } catch (Exception ex) {
                    append(ex.getMessage());
                    status.setText(t("Hata: %s", ex.getMessage()));
                    status.setForeground(Theme.DANGER);
                }
                busy = false;
                refresh.setEnabled(true);
                refresh();
            }
        };
        worker.execute();
    }

    private void initRepo() {
        act(t("Git deposu oluşturuluyor..."), () -> GitManager.init(root));
    }

    private void stageAll() {
        act(t("Değişiklikler sahneleniyor..."), () -> GitManager.stageAll(root));
    }

    private void commit() {
        String text = message.getText().trim();
        if (text.isEmpty()) {
            status.setText(t("Commit mesajı boş olamaz."));
            status.setForeground(Theme.DANGER);
            message.requestFocusInWindow();
            return;
        }
        act(t("Commit oluşturuluyor..."), () -> GitManager.commit(root, text));
    }

    private void pull() {
        act(t("Pull çalışıyor..."), () -> GitManager.pull(root));
    }

    private void push() {
        act(t("Push çalışıyor..."), () -> GitManager.push(root));
    }

    private void checkoutBranch() {
        String name = branchField.getText().trim();
        if (name.isEmpty()) return;
        act(t("Dal değiştiriliyor..."), () -> GitManager.checkout(root, name));
    }

    private void createBranch() {
        String name = branchField.getText().trim();
        if (name.isEmpty()) return;
        act(t("Yeni dal oluşturuluyor..."), () -> GitManager.createBranch(root, name));
    }

    private void stageSelected() {
        GitManager.Change change = changeList.getSelectedValue();
        if (change == null) { status.setText(t("Önce bir dosya seçin.")); return; }
        act(t("Dosya sahneleniyor..."), () -> GitManager.stage(root, change.path()));
    }

    private void fetchRemote() {
        act(t("Fetch çalışıyor..."), () -> GitManager.fetch(root));
    }

    private void showHistory() {
        act(t("Git geçmişi okunuyor..."), () -> {
            GitManager.Result result = GitManager.log(root, 40);
            SwingUtilities.invokeLater(() -> showText(t("Git Geçmişi"), result.output()));
            return result;
        });
    }

    private void showDiff() {
        GitManager.Change change = changeList.getSelectedValue();
        if (change == null) {
            status.setText(t("Önce bir dosya seçin."));
            return;
        }
        act(t("Diff hazırlanıyor..."), () -> {
            GitManager.Result result = GitManager.diff(root, change.path());
            SwingUtilities.invokeLater(() -> showText(t("Diff") + " — " + change.path(), result.output()));
            return result;
        });
    }

    private void showText(String title, String text) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setSize(900, 640);
        dialog.setLocationRelativeTo(this);
        JTextArea area = new JTextArea(text == null || text.isBlank() ? t("Değişiklik yok.") : text);
        area.setEditable(false);
        area.setFont(Theme.mono(12));
        area.setBackground(Theme.CONSOLE);
        area.setForeground(Theme.TEXT_SOFT);
        area.setBorder(new EmptyBorder(10, 12, 10, 12));
        dialog.add(Ui.scroll(area));
        dialog.setVisible(true);
    }
}
