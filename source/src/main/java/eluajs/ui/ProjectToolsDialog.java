package eluajs.ui;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Project tools window: left list, selected tool content on the right. */
final class ProjectToolsDialog extends JDialog {
    record Tool(String title, String description, Supplier<JComponent> content) {
        @Override public String toString() { return title; }
    }

    private final Map<Tool, JComponent> cache = new HashMap<>();
    private final JPanel host = new JPanel(new BorderLayout());

    ProjectToolsDialog(Frame owner, List<Tool> tools) {
        super(owner, t("Proje Araçları"), ModalityType.MODELESS);
        WindowChrome.install(this);
        setIconImage(Ui.appIcon());
        setSize(1040, 720);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);

        DefaultListModel<Tool> model = new DefaultListModel<>();
        tools.forEach(model::addElement);
        JList<Tool> list = new JList<>(model);
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.TEXT);
        list.setFixedCellHeight(42);
        list.setFont(Theme.font(13, Font.BOLD));
        list.setBorder(new EmptyBorder(6, 8, 6, 8));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, false);
                if (value instanceof Tool tool) label.setText(tool.title());
                label.setBorder(new EmptyBorder(5, 10, 5, 10));
                label.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                return label;
            }
        });
        JScrollPane listScroll = Ui.scroll(list);
        listScroll.setBorder(new EmptyBorder(14, 14, 14, 8));
        listScroll.setPreferredSize(new Dimension(280, 1));
        add(listScroll, BorderLayout.WEST);

        host.setBackground(Theme.BG);
        host.setBorder(new EmptyBorder(0, 4, 0, 10));
        add(host, BorderLayout.CENTER);

        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Tool tool = list.getSelectedValue();
            show(tool);
        });
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private void show(Tool tool) {
        host.removeAll();
        if (tool == null) {
            host.revalidate();
            host.repaint();
            return;
        }
        JComponent content = cache.get(tool);
        if (content == null) {
            try {
                content = tool.content() == null ? placeholder(tool) : tool.content().get();
            } catch (Exception ex) {
                content = placeholder(tool);
            }
            if (content == null) content = placeholder(tool);
            cache.put(tool, content);
        }
        host.add(content, BorderLayout.CENTER);
        host.revalidate();
        host.repaint();
    }

    private JComponent placeholder(Tool tool) {
        JPanel page = new JPanel();
        page.setBackground(Theme.BG);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(30, 32, 30, 32));
        JLabel title = Ui.label(tool.title(), 22, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel description = Ui.label("<html><body style='width:430px'>" + tool.description() + "</body></html>",
                12, Font.PLAIN, Theme.MUTED);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(title);
        page.add(Box.createVerticalStrut(10));
        page.add(description);
        page.add(Box.createVerticalGlue());
        JScrollPane scroll = Ui.scroll(page);
        scroll.setBorder(null);
        return scroll;
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 54));
        bar.setBorder(new EmptyBorder(0, 16, 0, 8));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(24, 24, Image.SCALE_SMOOTH))));
        brand.add(Ui.label(t("Proje Araçları"), 15, Font.BOLD, Theme.TEXT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }
}
