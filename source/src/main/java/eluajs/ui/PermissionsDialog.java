package eluajs.ui;

import eluajs.project.ProjectPermissionManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class PermissionsDialog extends JDialog {
    private final Path projectRoot;
    private final Runnable onChanged;
    private final Consumer<Path> openFile;
    private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();
    private final JTextField custom = Ui.field();
    private final JLabel status = Ui.label(t("Manifest izinlerini seçin."), 11, Font.PLAIN, Theme.MUTED);

    public PermissionsDialog(Window owner, Path projectRoot, Runnable onChanged, Consumer<Path> openFile) {
        super(owner, t("Android İzinleri"), ModalityType.APPLICATION_MODAL);
        this.projectRoot = projectRoot;
        this.onChanged = onChanged;
        this.openFile = openFile;
        setIconImage(Ui.appIcon());
        setSize(780, 650);
        setMinimumSize(new Dimension(680, 540));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(content());
        load();
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 17));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(24, 28, 24, 28));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(Ui.label(t("Android izinleri"), 23, Font.BOLD, Theme.TEXT));
        header.add(Box.createVerticalStrut(5));
        header.add(Ui.label(t("Sık kullanılan izinları kutucuklardan yönet; eLuajs AndroidManifest.xml dosyasını düzenler."), 12, Font.PLAIN, Theme.MUTED));
        root.add(header, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setBackground(Theme.PANEL);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(10, 12, 10, 12));
        for (var permission : ProjectPermissionManager.COMMON) {
            JCheckBox box = new JCheckBox(permission.title());
            box.setOpaque(false);
            box.setForeground(Theme.TEXT);
            box.setFont(Theme.font(13, Font.BOLD));
            box.setToolTipText(permission.name());
            boxes.put(permission.name(), box);

            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(5, 4, 5, 4));
            row.add(box, BorderLayout.WEST);
            JLabel detail = Ui.label(permission.detail() + "   •   " + permission.name(), 10, Font.PLAIN, Theme.MUTED);
            row.add(detail, BorderLayout.CENTER);
            list.add(row);
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel center = new JPanel(new BorderLayout(0, 13));
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);

        JPanel customPanel = Ui.panel(new BorderLayout(8, 0));
        customPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(10, 12, 10, 12)));
        JPanel ctext = new JPanel();
        ctext.setOpaque(false); ctext.setLayout(new BoxLayout(ctext, BoxLayout.Y_AXIS));
        ctext.add(Ui.label(t("Özel izin"), 12, Font.BOLD, Theme.TEXT));
        ctext.add(Ui.label(t("Listede olmayan manifest iznini tam adıyla ekleyebilirsin."), 10, Font.PLAIN, Theme.MUTED));
        customPanel.add(ctext, BorderLayout.WEST);
        custom.setPreferredSize(new Dimension(310, 38));
        custom.setToolTipText("Örn: android.permission.NFC");
        customPanel.add(custom, BorderLayout.CENTER);
        JButton addCustom = Ui.button("＋ " + t("Ekle"), false);
        addCustom.addActionListener(e -> addCustom());
        customPanel.add(addCustom, BorderLayout.EAST);
        center.add(customPanel, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(status, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        JButton open = Ui.button(t("Manifest'i Aç"), false);
        open.addActionListener(e -> {
            if (openFile != null) openFile.accept(projectRoot.resolve("app/src/main/AndroidManifest.xml"));
            dispose();
        });
        JButton close = Ui.button(t("Vazgeç"), false);
        close.addActionListener(e -> dispose());
        JButton save = Ui.button(t("İzinleri Uygula"), true);
        save.addActionListener(e -> save());
        actions.add(open); actions.add(close); actions.add(save);
        bottom.add(actions, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private void load() {
        try {
            Set<String> current = ProjectPermissionManager.list(projectRoot);
            for (var entry : boxes.entrySet()) entry.getValue().setSelected(current.contains(entry.getKey()));
            long customCount = current.stream().filter(p -> !boxes.containsKey(p)).count();
            status.setText(t("%d manifest izni var", current.size()) + (customCount > 0 ? t(" • %d özel", customCount) : "") + ".");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("İzinler okunamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save() {
        try {
            Set<String> selected = new LinkedHashSet<>();
            for (var entry : boxes.entrySet()) if (entry.getValue().isSelected()) selected.add(entry.getKey());
            ProjectPermissionManager.applyCommon(projectRoot, selected);
            if (onChanged != null) onChanged.run();
            status.setText(t("İzinler AndroidManifest.xml dosyasına uygulandı."));
            load();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("İzinler uygulanamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addCustom() {
        try {
            ProjectPermissionManager.addCustom(projectRoot, custom.getText());
            custom.setText("");
            if (onChanged != null) onChanged.run();
            load();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("İzin eklenemedi"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
