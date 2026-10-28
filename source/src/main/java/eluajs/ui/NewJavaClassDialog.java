package eluajs.ui;

import eluajs.model.ProjectMeta;
import eluajs.project.ProjectJavaClassManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;

public final class NewJavaClassDialog extends JDialog {
    private final ProjectMeta meta;
    private final JTextField className = Ui.field();
    private final JTextField packageName = Ui.field();
    private Path created;

    public NewJavaClassDialog(Frame owner, ProjectMeta meta) {
        super(owner, t("Yeni Java Sınıfı"), true);
        this.meta = meta;
        setIconImage(Ui.appIcon());
        setSize(570, 365);
        setMinimumSize(new Dimension(520, 340));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(content(), BorderLayout.CENTER);
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 20));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(26, 28, 24, 28));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(Ui.label(t("Yeni Java sınıfı"), 23, Font.BOLD, Theme.TEXT));
        top.add(Box.createVerticalStrut(5));
        top.add(Ui.label(t("Activity olmayan normal Java sınıfı oluşturur. Manifest değişmez."), 12, Font.PLAIN, Theme.MUTED));
        root.add(top, BorderLayout.NORTH);

        packageName.setText(meta.packageName());
        className.setToolTipText(t("Örnek: PdfManager, UserRepository, StepCounter"));
        packageName.setToolTipText(t("Örnek: %s.data", meta.packageName()));

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(Ui.formRow(t("Sınıf adı"), className));
        form.add(Box.createVerticalStrut(14));
        form.add(Ui.formRow(t("Paket"), packageName));
        root.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = Ui.button(t("Vazgeç"), false);
        cancel.addActionListener(e -> dispose());
        JButton create = Ui.button("＋  " + t("Sınıfı Oluştur"), true);
        create.addActionListener(e -> create());
        actions.add(cancel); actions.add(create);
        root.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(create);
        SwingUtilities.invokeLater(className::requestFocusInWindow);
        return root;
    }

    private void create() {
        try {
            created = ProjectJavaClassManager.create(meta.rootDir(), packageName.getText(), className.getText());
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Sınıf oluşturulamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public Path created() { return created; }
}
