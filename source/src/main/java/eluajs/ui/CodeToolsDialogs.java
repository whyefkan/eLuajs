package eluajs.ui;

import eluajs.project.AccessibilityChecker;
import eluajs.project.LocalizationManager;
import eluajs.project.ProjectSearch;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

/** Small list dialogs used by the project tools hub. */
final class CodeToolsDialogs {
    private CodeToolsDialogs() {}

    static void accessibility(Window owner, Path root, BiConsumer<Path,Integer> navigator) {
        List<AccessibilityChecker.Issue> issues = AccessibilityChecker.scan(root);
        DefaultListModel<AccessibilityChecker.Issue> model = new DefaultListModel<>();
        issues.forEach(model::addElement);
        JList<AccessibilityChecker.Issue> list = new JList<>(model);
        list.setFont(Theme.mono(11));
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, v, i, s, false);
                if (v instanceof AccessibilityChecker.Issue issue) label.setText(issue.toString());
                label.setBorder(new EmptyBorder(3, 6, 3, 6));
                label.setBackground(s ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                return label;
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    AccessibilityChecker.Issue issue = list.getSelectedValue();
                    if (issue != null && navigator != null) navigator.accept(issue.file(), issue.line());
                }
            }
        });
        showList(owner, t("Erişilebilirlik Denetçisi"), issues.isEmpty() ? t("Sorun bulunamadı.") : t("%d sorun", issues.size()), list);
    }

    static void localization(Window owner, Path root) throws Exception {
        List<LocalizationManager.LocaleReport> reports = LocalizationManager.scan(root);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (LocalizationManager.LocaleReport report : reports) {
            model.addElement(report.locale() + "  —  " + report.total() + " string"
                    + (report.missing().isEmpty() ? "" : "  |  eksik: " + String.join(", ", report.missing())));
        }
        JList<String> list = new JList<>(model);
        list.setFont(Theme.mono(11));
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        showList(owner, t("Çeviri Durumu"), reports.isEmpty() ? t("Locale bulunamadı.") : t("%d locale", reports.size()), list);
    }

    static void references(Window owner, Path root, BiConsumer<Path,Integer> navigator) {
        String query = JOptionPane.showInputDialog(owner, t("Aranacak sınıf / layout / string"), t("Referans Haritası"), JOptionPane.PLAIN_MESSAGE);
        if (query == null || query.isBlank()) return;
        try {
            List<ProjectSearch.Match> matches = ProjectSearch.search(root, query.trim(), false, 500);
            DefaultListModel<ProjectSearch.Match> model = new DefaultListModel<>();
            matches.forEach(model::addElement);
            JList<ProjectSearch.Match> list = new JList<>(model);
            list.setFont(Theme.mono(11));
            list.setBackground(Theme.PANEL);
            list.setForeground(Theme.TEXT);
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(l, v, i, s, false);
                    if (v instanceof ProjectSearch.Match match) {
                        String rel;
                        try { rel = root.relativize(match.file()).toString(); } catch (Exception ex) { rel = match.file().toString(); }
                        label.setText(rel + ":" + match.line() + "   " + match.preview());
                    }
                    label.setBorder(new EmptyBorder(3, 6, 3, 6));
                    label.setBackground(s ? Theme.SELECTION : Theme.PANEL);
                    label.setForeground(Theme.TEXT);
                    return label;
                }
            });
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2 && navigator != null) {
                        ProjectSearch.Match match = list.getSelectedValue();
                        if (match != null) navigator.accept(match.file(), match.line());
                    }
                }
            });
            showList(owner, t("Referans Haritası"), t("%d sonuç", matches.size()), list);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), t("Arama hatası"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void showList(Window owner, String title, String subtitle, JList<?> list) {
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        WindowChrome.install(dialog);
        dialog.setSize(820, 560);
        dialog.setLocationRelativeTo(owner);
        dialog.setBackground(Theme.BG);
        dialog.setLayout(new BorderLayout());
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Theme.TITLE_BAR);
        head.setBorder(new EmptyBorder(10, 16, 10, 8));
        head.add(Ui.label(title, 14, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        JPanel headRight = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        headRight.setOpaque(false);
        headRight.add(Ui.label(subtitle, 10, Font.PLAIN, Theme.MUTED));
        headRight.add(WindowChrome.controls(dialog));
        head.add(Ui.centerVertically(headRight), BorderLayout.EAST);
        dialog.add(head, BorderLayout.NORTH);
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(new EmptyBorder(10, 14, 12, 14));
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.setVisible(true);
    }
}
