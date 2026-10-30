package eluajs.ui;

import eluajs.update.UpdateManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import static eluajs.util.I18n.t;

/** Styled GitHub release/update dialog. */
public final class UpdateDialog extends JDialog {
    private final UpdateManager.Release release;
    private final JTextArea notes = new JTextArea();

    public UpdateDialog(Window owner, UpdateManager.Release release) {
        super(owner, t("Update available"), ModalityType.APPLICATION_MODAL);
        this.release = release;
        WindowChrome.install(this);
        setIconImage(Ui.appIcon());
        setSize(720, 580);
        setMinimumSize(new Dimension(620, 480));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);
        add(content(), BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);
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
        brand.add(Ui.label(t("Update available"), 15, Font.BOLD, Theme.TEXT));
        brand.add(Ui.badge("v" + release.version(), Theme.ACCENT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(22, 26, 12, 26));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = Ui.label(t("A new eLuajs version is available."), 22, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel versions = Ui.label(t("Current version: %s    →    Latest version: %s", UpdateManager.currentVersion(), release.version()), 12, Font.PLAIN, Theme.ACCENT);
        versions.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(6));
        header.add(versions);
        if (release.assetUrl() == null) {
            JLabel warning = Ui.label(t("This release has no eLuajs.jar asset. Open the release page to update manually."), 11, Font.PLAIN, Theme.WARNING);
            warning.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.add(Box.createVerticalStrut(8));
            header.add(warning);
        }
        root.add(header, BorderLayout.NORTH);

        JPanel notesCard = Ui.card(new BorderLayout(0, 8));
        notesCard.setBorder(new EmptyBorder(12, 14, 12, 14));
        notesCard.add(Ui.sectionLabel(t("Release notes")), BorderLayout.NORTH);
        notes.setEditable(false);
        notes.setFont(Theme.font(12, Font.PLAIN));
        notes.setBackground(Theme.CONSOLE);
        notes.setForeground(Theme.TEXT_SOFT);
        notes.setCaretColor(Theme.ACCENT);
        notes.setSelectionColor(Theme.SELECTION);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setBorder(new EmptyBorder(8, 10, 8, 10));
        notes.setText(release.notes() == null || release.notes().isBlank() ? "—" : release.notes());
        notes.setCaretPosition(0);
        JScrollPane scroll = Ui.scroll(notes);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        notesCard.add(scroll, BorderLayout.CENTER);
        root.add(notesCard, BorderLayout.CENTER);
        return root;
    }

    private JComponent actions() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG);
        wrap.setBorder(new EmptyBorder(8, 26, 20, 26));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JButton later = Ui.button(t("Later"), false);
        later.addActionListener(e -> dispose());
        JButton page = Ui.button(t("Open release page"), false);
        page.addActionListener(e -> openPage());
        JButton install = Ui.button(t("Download & Install"), true);
        install.setEnabled(release.assetUrl() != null && release.newer());
        install.addActionListener(e -> install());
        right.add(later);
        right.add(page);
        right.add(install);
        wrap.add(right, BorderLayout.EAST);
        return wrap;
    }

    private void openPage() {
        try {
            if (release.pageUrl() == null || release.pageUrl().isBlank()) throw new IllegalStateException();
            Desktop.getDesktop().browse(URI.create(release.pageUrl()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, t("Could not open release page."), t("Update"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void install() {
        int choice = JOptionPane.showConfirmDialog(this,
                t("This will update eLuajs and restart it. Save any unsaved work first."),
                t("Install update"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        JDialog progress = new JDialog(this, t("Installing update"), true);
        progress.setUndecorated(true);
        progress.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Theme.PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(18, 22, 14, 22)));
        panel.add(Ui.label(t("Downloading and installing the update..."), 12, Font.PLAIN, Theme.TEXT), BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setBackground(Theme.PANEL_2);
        bar.setForeground(Theme.ACCENT);
        bar.setBorderPainted(false);
        panel.add(bar, BorderLayout.CENTER);
        JPanel progressActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        progressActions.setOpaque(false);
        JButton cancelUpdate = Ui.button(t("Vazgeç"), false);
        progressActions.add(cancelUpdate);
        panel.add(progressActions, BorderLayout.SOUTH);
        progress.setContentPane(panel);
        progress.setSize(460, 150);
        progress.setLocationRelativeTo(this);
        progress.setAlwaysOnTop(true);

        AtomicBoolean cancelled = new AtomicBoolean();
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                UpdateManager.install(release, cancelled::get);
                return null;
            }
            @Override protected void done() {
                progress.dispose();
                if (isCancelled() || cancelled.get()) return;
                try {
                    get();
                    dispose();
                    UpdateManager.restart();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(UpdateDialog.this,
                            t("Update failed: %s", cause.getMessage()),
                            t("Update"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        cancelUpdate.addActionListener(e -> {
            cancelled.set(true);
            worker.cancel(true);
            cancelUpdate.setEnabled(false);
        });
        progress.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) { worker.execute(); }
        });
        progress.setVisible(true);
    }
}
