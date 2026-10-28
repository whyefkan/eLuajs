package eluajs.ui;

import eluajs.android.ToolchainManager;
import eluajs.project.BuildArtifacts;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lists APK/AAB artifacts and installs an APK over ADB. */
public final class ArtifactsDialog extends JDialog {
    private final Path root;
    private final DefaultListModel<BuildArtifacts.Artifact> model = new DefaultListModel<>();
    private final JList<BuildArtifacts.Artifact> list = new JList<>(model);
    private final JLabel status = Ui.label("", 11, Font.PLAIN, Theme.MUTED);
    private final JTextArea log = new JTextArea();
    private final JButton install = Ui.button(t("ADB ile Yükle"), true);
    private final JButton open = Ui.button(t("Klasörü Aç"), false);

    public ArtifactsDialog(Window owner, Path projectRoot) {
        super(owner, t("Derleme Çıktıları"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        this.root = projectRoot;
        setIconImage(Ui.appIcon());
        setSize(820, 600);
        setMinimumSize(new Dimension(680, 500));
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
        brand.add(Ui.label(t("Derleme Çıktıları"), 15, Font.BOLD, Theme.TEXT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JComponent content() {
        JPanel rootPanel = new JPanel(new BorderLayout(0, 10));
        rootPanel.setBackground(Theme.BG);
        rootPanel.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(Ui.sectionLabel(t("APK / AAB ÇIKTILARI")), BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        install.addActionListener(e -> installSelected());
        open.addActionListener(e -> openSelected());
        JButton refresh = Ui.button(t("Yenile"), false);
        refresh.addActionListener(e -> refresh());
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        actions.add(status);
        actions.add(refresh);
        actions.add(open);
        actions.add(install);
        actions.add(close);
        head.add(actions, BorderLayout.EAST);
        rootPanel.add(head, BorderLayout.NORTH);

        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.TEXT);
        list.setFixedCellHeight(34);
        list.setFont(Theme.mono(11));
        list.setBorder(new EmptyBorder(4, 6, 4, 6));
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(e -> updateActions());
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.PANEL);
        rootPanel.add(scroll, BorderLayout.CENTER);

        log.setEditable(false);
        log.setFont(Theme.mono(11));
        log.setBackground(Theme.CONSOLE);
        log.setForeground(Theme.TEXT_SOFT);
        log.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane logScroll = Ui.scroll(log);
        logScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        logScroll.setPreferredSize(new Dimension(1, 120));
        rootPanel.add(logScroll, BorderLayout.SOUTH);
        return rootPanel;
    }

    private void refresh() {
        model.clear();
        BuildArtifacts.list(root).forEach(model::addElement);
        status.setText(model.isEmpty() ? t("Henüz çıktı yok.") : t("%d çıktı", model.size()));
        if (!model.isEmpty()) list.setSelectedIndex(0);
        updateActions();
    }

    private void updateActions() {
        BuildArtifacts.Artifact artifact = list.getSelectedValue();
        boolean apk = artifact != null && artifact.kind().equals("APK");
        install.setEnabled(apk);
        open.setEnabled(artifact != null);
    }

    private void installSelected() {
        BuildArtifacts.Artifact artifact = list.getSelectedValue();
        if (artifact == null || !artifact.kind().equals("APK")) return;
        install.setEnabled(false);
        status.setText(t("ADB ile yükleniyor..."));
        append("$ adb install -r " + artifact.file());
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ToolchainManager.installApk(artifact.file(), line -> SwingUtilities.invokeLater(() -> append(line)));
                return null;
            }
            @Override protected void done() {
                install.setEnabled(true);
                try {
                    get();
                    status.setText(t("APK yüklendi."));
                    status.setForeground(Theme.ACCENT);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText(t("ADB hatası: %s", cause.getMessage()));
                    status.setForeground(Theme.DANGER);
                    append(cause.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void openSelected() {
        BuildArtifacts.Artifact artifact = list.getSelectedValue();
        if (artifact == null) return;
        try {
            Path folder = artifact.file().getParent();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder.toFile());
            } else {
                new ProcessBuilder("xdg-open", folder.toString()).start();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Klasör açılamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void append(String text) {
        if (text == null || text.isBlank()) return;
        log.append(text);
        if (!text.endsWith("\n")) log.append("\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    private final class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
            if (value instanceof BuildArtifacts.Artifact artifact) {
                label.setText(artifact.kind() + "  " + artifact.file().getFileName()
                        + "   " + BuildArtifacts.size(artifact.bytes()) + "   " + artifact.file().getParent());
                label.setToolTipText(artifact.file().toString());
            }
            label.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
            label.setForeground(Theme.TEXT);
            label.setBorder(new EmptyBorder(3, 6, 3, 6));
            return label;
        }
    }
}
