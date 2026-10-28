package eluajs.ui;

import eluajs.model.ProjectMeta;
import eluajs.project.ProjectActivityManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class NewActivityDialog extends JDialog {
    private final ProjectMeta meta;
    private final JTextField className = Ui.field();
    private final JTextField title = Ui.field();
    private ProjectActivityManager.CreatedActivity created;

    public NewActivityDialog(Window owner, ProjectMeta meta) {
        super(owner, t("Yeni Sayfa / Activity"), ModalityType.APPLICATION_MODAL);
        this.meta = meta;
        setIconImage(Ui.appIcon());
        setSize(590, 430);
        setMinimumSize(new Dimension(540, 400));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(content(), BorderLayout.CENTER);
    }

    public ProjectActivityManager.CreatedActivity created() { return created; }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 22));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(28, 30, 26, 30));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(Ui.label(t("Yeni Android sayfası"), 23, Font.BOLD, Theme.TEXT));
        head.add(Box.createVerticalStrut(6));
        head.add(Ui.label(t("eLuajs Java Activity dosyasını oluşturur ve AndroidManifest.xml'e ekler."), 12, Font.PLAIN, Theme.MUTED));
        root.add(head, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        className.setText(t("IkinciSayfa"));
        title.setText(t("İkinci Sayfa"));
        form.add(Ui.formRow(t("Sayfa / Activity adı"), className));
        form.add(Box.createVerticalStrut(14));
        form.add(Ui.formRow(t("Ekranda görünecek başlık"), title));
        form.add(Box.createVerticalStrut(13));
        JLabel hint = Ui.label(t("Örnek: IkinciSayfa → IkinciSayfaActivity.java"), 11, Font.PLAIN, Theme.MUTED);
        form.add(hint);
        root.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = Ui.button(t("Vazgeç"), false);
        cancel.addActionListener(e -> dispose());
        JButton create = Ui.button("＋  " + t("Sayfayı Oluştur"), true);
        create.addActionListener(e -> create());
        actions.add(cancel);
        actions.add(create);
        root.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(create);
        return root;
    }

    private void create() {
        try {
            created = ProjectActivityManager.create(meta, className.getText(), title.getText());
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Sayfa oluşturulamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
