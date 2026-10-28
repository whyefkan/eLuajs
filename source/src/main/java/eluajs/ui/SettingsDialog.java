package eluajs.ui;

import eluajs.ai.AiClient;
import eluajs.ai.AiConfig;
import eluajs.ai.AiProvider;
import eluajs.util.I18n;
import eluajs.util.CrashReporter;
import eluajs.update.UpdateConfig;
import eluajs.update.UpdateManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.stream.Collectors;

import static eluajs.util.I18n.t;

/** Settings shell that matches the rest of the IDE: category rail + card content. */
public final class SettingsDialog extends JDialog {
    private final AiConfig config = AiConfig.load();
    private final UpdateConfig updateConfig = UpdateConfig.load();
    private boolean languageChanged;
    private boolean loadingProvider;
    private AiProvider lastProvider;

    private final CardLayout cards = new CardLayout();
    private final JPanel contentPanel = new JPanel(cards);
    private CategoryButton generalButton;
    private CategoryButton aiButton;
    private CategoryButton updateButton;

    private final JComboBox<LangItem> languageCombo = new JComboBox<>(new LangItem[]{
            new LangItem(I18n.Lang.EN, t("English (default)")),
            new LangItem(I18n.Lang.TR, t("Turkish")),
            new LangItem(I18n.Lang.DE, t("German")),
            new LangItem(I18n.Lang.FR, t("French")),
            new LangItem(I18n.Lang.ES, t("Spanish")),
            new LangItem(I18n.Lang.RU, t("Russian"))
    });
    private final JComboBox<AiProvider> providerCombo = new JComboBox<>(AiProvider.values());
    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JTextField baseUrl = Ui.field();
    private final JPasswordField apiKey = Ui.passwordField();
    private final JTextField tokenBudget = Ui.field();
    private final JCheckBox tokenBudgetEnabled = new JCheckBox(t("Bütçe sınırı"));
    private final JCheckBox showKey = new JCheckBox(t("Show"));
    private final JLabel status = Ui.label("", 11, Font.PLAIN, Theme.MUTED);
    private final JLabel savedProviders = Ui.label("", 10, Font.PLAIN, Theme.MUTED);
    private final JComboBox<String> themeCombo = new JComboBox<>(new String[]{t("Dark"), t("Light")});
    private final JCheckBox autoUpdate = new JCheckBox(t("Check for updates automatically on startup"));
    private final JLabel updateStatus = Ui.label("", 11, Font.PLAIN, Theme.MUTED);

    public SettingsDialog(Window owner) {
        super(owner, t("eLuajs Settings"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        setIconImage(Ui.appIcon());
        setSize(900, 680);
        setMinimumSize(new Dimension(780, 600));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        initialise();
        showCategory("general", generalButton);
    }

    public boolean languageChanged() { return languageChanged; }

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
        brand.add(Ui.label(t("Settings"), 15, Font.BOLD, Theme.TEXT));
        brand.add(Ui.badge("v" + HubFrame.VERSION, Theme.ACCENT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);

        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JComponent body() {
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG);
        body.add(categoryRail(), BorderLayout.WEST);
        contentPanel.setBackground(Theme.BG);
        contentPanel.add(scrollPage(generalPage()), "general");
        contentPanel.add(scrollPage(aiPage()), "ai");
        contentPanel.add(scrollPage(updatesPage()), "updates");
        body.add(contentPanel, BorderLayout.CENTER);
        return body;
    }

    private JComponent categoryRail() {
        JPanel rail = new JPanel(new BorderLayout());
        rail.setBackground(Theme.SIDEBAR);
        rail.setPreferredSize(new Dimension(222, 1));
        rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER_SOFT));

        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Theme.SIDEBAR);
        head.setBorder(new EmptyBorder(16, 16, 10, 12));
        head.add(Ui.sectionLabel(t("Settings")), BorderLayout.WEST);
        rail.add(head, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setBackground(Theme.SIDEBAR);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(0, 8, 0, 8));
        generalButton = new CategoryButton("settings", t("General"));
        generalButton.addActionListener(e -> showCategory("general", generalButton));
        aiButton = new CategoryButton("ai", t("AI Providers"));
        aiButton.addActionListener(e -> showCategory("ai", aiButton));
        updateButton = new CategoryButton("refresh", t("Updates"));
        updateButton.addActionListener(e -> showCategory("updates", updateButton));
        list.add(generalButton);
        list.add(Box.createVerticalStrut(3));
        list.add(aiButton);
        list.add(Box.createVerticalStrut(3));
        list.add(updateButton);
        rail.add(list, BorderLayout.CENTER);

        JPanel note = new JPanel();
        note.setBackground(Theme.SIDEBAR);
        note.setLayout(new BoxLayout(note, BoxLayout.Y_AXIS));
        note.setBorder(new EmptyBorder(10, 16, 14, 12));
        JLabel version = Ui.label("eLuajs " + HubFrame.VERSION, 10, Font.PLAIN, Theme.MUTED);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel dev = Ui.label("Efkwn Mobile", 9, Font.PLAIN, Theme.MUTED);
        dev.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.add(version);
        note.add(Box.createVerticalStrut(2));
        note.add(dev);
        rail.add(note, BorderLayout.SOUTH);
        return rail;
    }

    private void showCategory(String id, CategoryButton active) {
        cards.show(contentPanel, id);
        if (generalButton != null) generalButton.setActive(generalButton == active);
        if (aiButton != null) aiButton.setActive(aiButton == active);
        if (updateButton != null) updateButton.setActive(updateButton == active);
    }

    private JComponent scrollPage(JComponent page) {
        ScrollBody body = new ScrollBody();
        body.setBackground(Theme.BG);
        body.add(page, BorderLayout.NORTH);
        JScrollPane scroll = Ui.scroll(body);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private static final class ScrollBody extends JPanel implements Scrollable {
        ScrollBody() { super(new BorderLayout()); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(40, orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width);
        }
    }

    private JComponent generalPage() {
        JPanel page = new JPanel();
        page.setBackground(Theme.BG);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(24, 28, 24, 28));
        page.add(pageHeader(t("General"), t("Language and basic preferences.")));
        page.add(Box.createVerticalStrut(20));

        JPanel languageCard = Ui.card(new BorderLayout(0, 10));
        languageCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        languageCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        languageCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 154));
        languageCard.add(Ui.sectionLabel(t("Language")), BorderLayout.NORTH);

        JPanel languageBody = new JPanel();
        languageBody.setOpaque(false);
        languageBody.setLayout(new BoxLayout(languageBody, BoxLayout.Y_AXIS));
        JLabel langDesc = Ui.label(t("Choose the interface language."), 11, Font.PLAIN, Theme.MUTED);
        langDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        languageBody.add(langDesc);
        languageBody.add(Box.createVerticalStrut(10));
        styleCombo(languageCombo);
        languageCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        languageCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < languageCombo.getItemCount(); i++) {
            if (languageCombo.getItemAt(i).lang() == I18n.language()) {
                languageCombo.setSelectedIndex(i);
                break;
            }
        }
        languageBody.add(languageCombo);
        languageBody.add(Box.createVerticalStrut(6));
        JLabel note = Ui.label(t("Language changes are applied when you restart eLuajs."), 10, Font.PLAIN, Theme.MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        languageBody.add(note);
        languageCard.add(languageBody, BorderLayout.CENTER);
        page.add(languageCard);
        page.add(Box.createVerticalStrut(12));

        JPanel appearance = Ui.softCard(new BorderLayout(0, 7));
        appearance.setAlignmentX(Component.LEFT_ALIGNMENT);
        appearance.setBorder(new EmptyBorder(14, 18, 14, 18));
        appearance.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
        appearance.add(Ui.sectionLabel(t("Appearance")), BorderLayout.NORTH);
        JPanel themeBody = new JPanel();
        themeBody.setOpaque(false);
        themeBody.setLayout(new BoxLayout(themeBody, BoxLayout.Y_AXIS));
        styleCombo(themeCombo);
        themeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        themeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        themeCombo.setSelectedIndex(Theme.isLight() ? 1 : 0);
        themeBody.add(themeCombo);
        JLabel themeNote = Ui.label(t("Theme changes are applied when you restart eLuajs."), 10, Font.PLAIN, Theme.MUTED);
        themeNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        themeBody.add(Box.createVerticalStrut(5));
        themeBody.add(themeNote);
        appearance.add(themeBody, BorderLayout.CENTER);
        page.add(appearance);
        page.add(Box.createVerticalStrut(12));

        JPanel crash = Ui.softCard(new BorderLayout(0, 7));
        crash.setAlignmentX(Component.LEFT_ALIGNMENT);
        crash.setBorder(new EmptyBorder(14, 18, 14, 18));
        crash.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        crash.add(Ui.sectionLabel(t("Reliability")), BorderLayout.NORTH);
        JPanel crashBody = new JPanel(new BorderLayout(8, 0));
        crashBody.setOpaque(false);
        crashBody.add(Ui.label(t("Unexpected errors are written to ~/.eluajs/crash.log."), 11, Font.PLAIN, Theme.TEXT_SOFT), BorderLayout.CENTER);
        JButton openCrash = Ui.button(t("Open crash log"), false);
        openCrash.addActionListener(e -> openCrashLog());
        crashBody.add(openCrash, BorderLayout.EAST);
        crash.add(crashBody, BorderLayout.CENTER);
        page.add(crash);
        page.add(Box.createVerticalGlue());
        return page;
    }

    private void openCrashLog() {
        try {
            if (!java.nio.file.Files.isRegularFile(CrashReporter.logFile())) {
                JOptionPane.showMessageDialog(this, t("No crash log yet."), t("Crash log"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(CrashReporter.logFile().toFile());
            } else {
                JTextArea area = new JTextArea(CrashReporter.tail(60_000));
                area.setEditable(false);
                area.setFont(Theme.mono(11));
                JScrollPane scroll = Ui.scroll(area);
                scroll.setPreferredSize(new Dimension(760, 480));
                JOptionPane.showMessageDialog(this, scroll, t("Crash log"), JOptionPane.PLAIN_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Crash log"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private JComponent aiPage() {
        JPanel page = new JPanel();
        page.setBackground(Theme.BG);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(24, 28, 24, 28));
        page.add(pageHeader(t("AI Providers"), t("Connect a provider and choose a model.")));
        page.add(Box.createVerticalStrut(20));

        styleCombo(providerCombo);
        styleCombo(modelCombo);
        modelCombo.setEditable(true);
        styleComboEditor(modelCombo);
        modelCombo.setToolTipText(t("You can type any model supported by the provider."));
        baseUrl.setToolTipText(t("Base URL (optional)"));
        showKey.setOpaque(false);
        showKey.setForeground(Theme.MUTED);
        showKey.setFont(Theme.font(10, Font.PLAIN));
        showKey.setFocusPainted(false);
        showKey.addActionListener(e -> apiKey.setEchoChar(showKey.isSelected() ? (char) 0 : '\u2022'));

        JPanel card = Ui.card(new BorderLayout(0, 13));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 430));
        card.add(Ui.sectionLabel(t("Active provider")), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JPanel top = new JPanel(new GridLayout(1, 2, 14, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        top.add(fieldColumn(t("Provider"), providerCombo));
        top.add(fieldColumn(t("Model"), modelCombo));
        form.add(top);
        form.add(Box.createVerticalStrut(5));
        JLabel modelHint = Ui.label(t("You can type any model supported by the provider."), 10, Font.PLAIN, Theme.MUTED);
        modelHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(modelHint);
        form.add(Box.createVerticalStrut(12));

        JPanel baseRow = fieldColumn(t("Base URL (optional)"), baseUrl);
        baseRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        baseRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        form.add(baseRow);
        form.add(Box.createVerticalStrut(12));

        JPanel keyRow = new JPanel(new BorderLayout(8, 0));
        keyRow.setOpaque(false);
        keyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        keyRow.add(apiKey, BorderLayout.CENTER);
        JPanel keyTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        keyTools.setOpaque(false);
        JButton test = Ui.button(t("Test connection"), false);
        test.setFont(Theme.font(10, Font.BOLD));
        test.setBorder(new EmptyBorder(6, 8, 6, 8));
        test.addActionListener(e -> testConnection(test));
        keyTools.add(showKey);
        keyTools.add(test);
        keyRow.add(keyTools, BorderLayout.EAST);
        JPanel keyColumn = new JPanel();
        keyColumn.setOpaque(false);
        keyColumn.setLayout(new BoxLayout(keyColumn, BoxLayout.Y_AXIS));
        JLabel keyLabel = Ui.label(t("API key"), 9, Font.BOLD, Theme.MUTED);
        keyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyColumn.add(keyLabel);
        keyColumn.add(Box.createVerticalStrut(3));
        keyColumn.add(keyRow);
        form.add(keyColumn);
        form.add(Box.createVerticalStrut(8));

        JPanel budgetRow = new JPanel();
        budgetRow.setOpaque(false);
        budgetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        budgetRow.setLayout(new BoxLayout(budgetRow, BoxLayout.Y_AXIS));
        budgetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        JPanel budgetLabelRow = new JPanel(new BorderLayout(8, 0));
        budgetLabelRow.setOpaque(false);
        budgetLabelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        budgetLabelRow.add(Ui.label(t("Token budget"), 9, Font.BOLD, Theme.MUTED), BorderLayout.WEST);
        tokenBudgetEnabled.setOpaque(false);
        tokenBudgetEnabled.setForeground(Theme.MUTED);
        tokenBudgetEnabled.setFont(Theme.font(10, Font.PLAIN));
        tokenBudgetEnabled.setFocusPainted(false);
        budgetLabelRow.add(tokenBudgetEnabled, BorderLayout.EAST);
        budgetRow.add(budgetLabelRow);
        budgetRow.add(Box.createVerticalStrut(3));
        budgetRow.add(tokenBudget);
        budgetRow.setBorder(new EmptyBorder(0, 0, 0, 8));
        tokenBudget.setVisible(tokenBudgetEnabled.isSelected());
        budgetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, tokenBudgetEnabled.isSelected() ? 58 : 28));
        tokenBudgetEnabled.addActionListener(e -> {
            boolean show = tokenBudgetEnabled.isSelected();
            tokenBudget.setVisible(show);
            tokenBudget.setEnabled(show);
            budgetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, show ? 58 : 28));
            budgetRow.revalidate();
            budgetRow.repaint();
        });
        form.add(budgetRow);
        form.add(Box.createVerticalStrut(8));

        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(status);
        card.add(form, BorderLayout.CENTER);
        page.add(card);
        page.add(Box.createVerticalStrut(12));

        JPanel saved = Ui.softCard(new BorderLayout(0, 8));
        saved.setAlignmentX(Component.LEFT_ALIGNMENT);
        saved.setBorder(new EmptyBorder(13, 18, 13, 18));
        saved.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
        saved.add(Ui.sectionLabel(t("Saved providers")), BorderLayout.NORTH);
        saved.add(savedProviders, BorderLayout.CENTER);

        JLabel note = Ui.label(t("Keys are stored locally in ~/.eluajs/ai.properties and are never uploaded except to the provider you call."), 10, Font.PLAIN, Theme.MUTED);
        saved.add(note, BorderLayout.SOUTH);
        page.add(saved);
        page.add(Box.createVerticalGlue());

        providerCombo.addActionListener(e -> {
            if (loadingProvider) return;
            storeProviderFields(lastProvider);
            loadProviderFields();
        });
        return page;
    }

    private JComponent updatesPage() {
        JPanel page = new JPanel();
        page.setBackground(Theme.BG);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(24, 28, 24, 28));
        page.add(pageHeader(t("Updates"), t("Check GitHub releases and update eLuajs.")));
        page.add(Box.createVerticalStrut(20));

        JPanel card = Ui.card(new BorderLayout(0, 12));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 390));
        card.add(Ui.sectionLabel(t("Official repository")), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(0, 0, 0, 8));

        JPanel official = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        official.setOpaque(false);
        official.setAlignmentX(Component.LEFT_ALIGNMENT);
        official.add(Ui.label(t("Official repository:"), 11, Font.PLAIN, Theme.MUTED));
        official.add(Box.createHorizontalStrut(8));
        official.add(Ui.badge(UpdateManager.OFFICIAL_REPO, Theme.ACCENT));
        form.add(official);
        form.add(Box.createVerticalStrut(6));
        JLabel officialHint = Ui.label(t("Updates always come from the official eLuajs repository."), 10, Font.PLAIN, Theme.MUTED);
        officialHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(officialHint);
        form.add(Box.createVerticalStrut(10));

        autoUpdate.setOpaque(false);
        autoUpdate.setForeground(Theme.MUTED);
        autoUpdate.setFont(Theme.font(11, Font.PLAIN));
        autoUpdate.setFocusPainted(false);
        autoUpdate.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(autoUpdate);
        form.add(Box.createVerticalStrut(8));

        JPanel versionRow = new JPanel(new BorderLayout(8, 0));
        versionRow.setOpaque(false);
        versionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        versionRow.add(Ui.label(t("Current version: %s", UpdateManager.currentVersion()), 11, Font.PLAIN, Theme.TEXT_SOFT), BorderLayout.WEST);
        JButton check = Ui.button(t("Check now"), true);
        check.addActionListener(e -> checkUpdates(check));
        versionRow.add(check, BorderLayout.EAST);
        form.add(versionRow);
        form.add(Box.createVerticalStrut(8));

        updateStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(updateStatus);
        card.add(form, BorderLayout.CENTER);
        page.add(card);
        page.add(Box.createVerticalStrut(12));

        JPanel note = Ui.softCard(new BorderLayout(0, 7));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(14, 18, 14, 18));
        note.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        note.add(Ui.sectionLabel(t("How updates work")), BorderLayout.NORTH);
        JLabel info = Ui.label(t("Releases must include an eLuajs.jar asset. The app downloads it, replaces app/eLuajs.jar, and restarts."), 11, Font.PLAIN, Theme.TEXT_SOFT);
        note.add(info, BorderLayout.CENTER);
        page.add(note);
        page.add(Box.createVerticalGlue());
        return page;
    }

    private void checkUpdates(JButton button) {
        updateConfig.setAutoCheck(autoUpdate.isSelected());
        updateConfig.save();
        button.setEnabled(false);
        updateStatus.setText(t("Checking for updates..."));
        updateStatus.setForeground(Theme.MUTED);
        SwingWorker<UpdateManager.Release, Void> worker = new SwingWorker<>() {
            @Override protected UpdateManager.Release doInBackground() throws Exception {
                return UpdateManager.check();
            }
            @Override protected void done() {
                button.setEnabled(true);
                try {
                    UpdateManager.Release release = get();
                    if (release.newer()) {
                        updateStatus.setText(t("Update available: v%s", release.version()));
                        updateStatus.setForeground(Theme.ACCENT);
                        updateConfig.save();
                        new UpdateDialog(SwingUtilities.getWindowAncestor(SettingsDialog.this), release).setVisible(true);
                    } else {
                        updateStatus.setText(t("You are up to date. (v%s)", UpdateManager.currentVersion()));
                        updateStatus.setForeground(Theme.ACCENT);
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    updateStatus.setText(t("Update check failed: %s", cause.getMessage()));
                    updateStatus.setForeground(Theme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private JComponent pageHeader(String title, String subtitle) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = Ui.label(title, 24, Font.BOLD, Theme.TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel s = Ui.label(subtitle, 11, Font.PLAIN, Theme.MUTED);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(t);
        header.add(Box.createVerticalStrut(4));
        header.add(s);
        return header;
    }

    private JPanel fieldColumn(String label, JComponent component) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        JLabel l = Ui.label(label, 9, Font.BOLD, Theme.MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        column.add(l);
        column.add(Box.createVerticalStrut(3));
        column.add(component);
        return column;
    }

    private JComponent footer() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.TITLE_BAR);
        wrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_SOFT),
                new EmptyBorder(10, 20, 12, 20)));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = Ui.button(t("Cancel"), false);
        cancel.addActionListener(e -> dispose());
        JButton save = Ui.button(t("Save"), true);
        save.addActionListener(e -> save());
        buttons.add(cancel);
        buttons.add(save);
        wrap.add(buttons, BorderLayout.EAST);
        return wrap;
    }

    private void initialise() {
        loadingProvider = true;
        lastProvider = config.provider();
        providerCombo.setSelectedItem(lastProvider);
        loadProviderFields();
        loadingProvider = false;
        refreshSavedProviders();
        autoUpdate.setSelected(updateConfig.autoCheck());
        themeCombo.setSelectedIndex(Theme.isLight() ? 1 : 0);
    }

    private void loadProviderFields() {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        if (provider == null) return;
        lastProvider = provider;
        modelCombo.removeAllItems();
        for (String model : provider.models()) modelCombo.addItem(model);
        modelCombo.setSelectedItem(config.model(provider));
        modelCombo.getEditor().setItem(config.model(provider));
        baseUrl.setText(config.baseUrl(provider));
        apiKey.setText(config.key(provider));
        apiKey.setEchoChar(showKey.isSelected() ? (char) 0 : '\u2022');
        tokenBudget.setText(String.valueOf(config.tokenBudget()));
        tokenBudgetEnabled.setSelected(config.tokenBudgetEnabled());
        tokenBudget.setEnabled(config.tokenBudgetEnabled());
        tokenBudget.setVisible(config.tokenBudgetEnabled());
        if (tokenBudget.getParent() != null) {
            tokenBudget.getParent().setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    config.tokenBudgetEnabled() ? 58 : 28));
            tokenBudget.getParent().revalidate();
        }
        if (!provider.requiresKey()) {
            status.setText(t("Local provider — no API key required."));
            status.setForeground(Theme.ACCENT);
        } else {
            status.setText(config.hasKey(provider) ? t("API key saved.") : t("No API key saved for this provider."));
            status.setForeground(config.hasKey(provider) ? Theme.ACCENT : Theme.MUTED);
        }
        refreshSavedProviders();
    }

    private void storeProviderFields() {
        storeProviderFields((AiProvider) providerCombo.getSelectedItem());
    }

    private void storeProviderFields(AiProvider provider) {
        if (provider == null) return;
        Object model = modelCombo.isEditable() ? modelCombo.getEditor().getItem() : modelCombo.getSelectedItem();
        config.setModel(provider, model == null ? "" : String.valueOf(model));
        config.setBaseUrl(provider, baseUrl.getText());
        config.setKey(provider, new String(apiKey.getPassword()));
        config.setTokenBudgetEnabled(tokenBudgetEnabled.isSelected());
        try { config.setTokenBudget(Integer.parseInt(tokenBudget.getText().trim())); }
        catch (NumberFormatException ex) { config.setTokenBudget(24000); }
    }

    private void refreshSavedProviders() {
        String saved = Arrays.stream(AiProvider.values())
                .filter(p -> !p.requiresKey() || config.hasKey(p))
                .map(AiProvider::displayName)
                .collect(Collectors.joining("  •  "));
        savedProviders.setText(saved.isEmpty() ? "—" : saved);
    }

    private void testConnection(JButton button) {
        storeProviderFields();
        config.setProvider((AiProvider) providerCombo.getSelectedItem());
        button.setEnabled(false);
        status.setText(t("Thinking..."));
        status.setForeground(Theme.MUTED);
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override protected String doInBackground() throws Exception {
                return AiClient.complete(config, "You are a connectivity test.", "Reply with exactly: OK");
            }
            @Override protected void done() {
                button.setEnabled(true);
                try {
                    String reply = get();
                    status.setText(t("Connection OK: %s", reply));
                    status.setForeground(Theme.ACCENT);
                    refreshSavedProviders();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText(t("Connection failed: %s", cause.getMessage()));
                    status.setForeground(Theme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void save() {
        storeProviderFields();
        config.setProvider((AiProvider) providerCombo.getSelectedItem());
        config.save();
        updateConfig.setAutoCheck(autoUpdate.isSelected());
        updateConfig.save();
        boolean light = themeCombo.getSelectedIndex() == 1;
        if (light != Theme.isLight()) {
            Theme.setLight(light);
            languageChanged = true;
        }
        LangItem selected = (LangItem) languageCombo.getSelectedItem();
        if (selected != null && selected.lang() != I18n.language()) {
            I18n.setLanguage(selected.lang());
            languageChanged = true;
        }
        dispose();
    }

    private void styleCombo(JComboBox<?> combo) {
        Ui.styleCombo(combo);
        combo.setFont(Theme.font(12, Font.PLAIN));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                label.setBackground(selected ? Theme.SELECTION : Theme.PANEL_2);
                label.setForeground(Theme.TEXT);
                label.setBorder(new EmptyBorder(4, 8, 4, 8));
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
            field.setFont(Theme.font(12, Font.PLAIN));
            field.setBorder(new EmptyBorder(7, 9, 7, 9));
        }
    }

    private record LangItem(I18n.Lang lang, String label) {
        @Override public String toString() { return label; }
    }

    private static final class CategoryButton extends JButton {
        private final String iconName;
        private boolean active;

        CategoryButton(String iconName, String text) {
            super(text);
            this.iconName = iconName;
            setFont(Theme.font(12, Font.PLAIN));
            setForeground(Theme.MUTED);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(new EmptyBorder(0, 38, 0, 10));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension size = new Dimension(206, 40);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        }

        void setActive(boolean active) {
            this.active = active;
            setForeground(active ? Theme.TEXT : Theme.MUTED);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active) {
                g2.setColor(Theme.PANEL_2);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(0, 9, 3, getHeight() - 18, 3, 3);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(255, 255, 255, 8));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }
            Icons.draw(g2, iconName, 12, (getHeight() - 16) / 2, 16, active ? Theme.ACCENT : Theme.MUTED);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
