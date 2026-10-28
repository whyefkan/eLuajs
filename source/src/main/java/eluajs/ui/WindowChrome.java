package eluajs.ui;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/** Custom VS Code style window chrome: drag, resize and window controls. */
public final class WindowChrome {
    private static final int RESIZE_BORDER = 6;

    private WindowChrome() {}

    public static void install(JFrame frame) {
        frame.setUndecorated(true);
        frame.getRootPane().setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        installResizer(frame);
    }

    public static void install(JDialog dialog) {
        dialog.setUndecorated(true);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        if (dialog.isResizable()) installResizer(dialog);
    }

    public static void enableDrag(Window window, JComponent handle) {
        DragHandler handler = new DragHandler(window);
        attach(handle, handler);
    }

    public static JComponent controls(Window window) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        panel.setOpaque(false);
        if (window instanceof JFrame frame) {
            WindowButton minimize = new WindowButton("minimize", false);
            minimize.setToolTipText("Minimize");
            minimize.addActionListener(e -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED));
            WindowButton maximize = new WindowButton("maximize", false);
            maximize.setToolTipText("Maximize / Restore");
            maximize.addActionListener(e -> toggleMaximize(frame, maximize));
            frame.getRootPane().putClientProperty("eluajs.maxButton", maximize);
            WindowButton close = new WindowButton("close", true);
            close.setToolTipText("Close");
            close.addActionListener(e -> close(frame));
            panel.add(minimize);
            panel.add(maximize);
            panel.add(close);
        } else if (window instanceof Dialog dialog) {
            WindowButton close = new WindowButton("close", true);
            close.setToolTipText("Close");
            close.addActionListener(e -> close(dialog));
            panel.add(close);
        }
        return panel;
    }

    public static void close(Window window) {
        window.dispatchEvent(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING));
    }

    public static void toggleMaximize(JFrame frame, WindowButton button) {
        boolean maximized = Boolean.TRUE.equals(frame.getRootPane().getClientProperty("eluajs.maximized"));
        if (maximized) {
            Object restore = frame.getRootPane().getClientProperty("eluajs.restoreBounds");
            if (restore instanceof Rectangle rect) frame.setBounds(rect);
            frame.getRootPane().putClientProperty("eluajs.maximized", false);
            if (button != null) button.setIconName("maximize");
        } else {
            frame.getRootPane().putClientProperty("eluajs.restoreBounds", frame.getBounds());
            GraphicsConfiguration gc = frame.getGraphicsConfiguration();
            Rectangle screen = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            frame.setBounds(screen.x + insets.left, screen.y + insets.top,
                    screen.width - insets.left - insets.right,
                    screen.height - insets.top - insets.bottom);
            frame.getRootPane().putClientProperty("eluajs.maximized", true);
            if (button != null) button.setIconName("restore");
        }
    }

    private static void toggleFromTitle(Window window) {
        if (window instanceof JFrame frame) {
            Object button = frame.getRootPane().getClientProperty("eluajs.maxButton");
            toggleMaximize(frame, button instanceof WindowButton wb ? wb : null);
        }
    }

    private static void attach(Component c, DragHandler handler) {
        if (c instanceof AbstractButton || c instanceof JTextComponent || c instanceof JComboBox<?>
                || c instanceof JScrollBar || c instanceof JSpinner || c instanceof JList<?>
                || c instanceof JTable || c instanceof JTree) {
            return;
        }
        c.addMouseListener(handler);
        c.addMouseMotionListener(handler);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) attach(child, handler);
        }
    }

    private static void installResizer(Window window) {
        JRootPane root = window instanceof JFrame frame ? frame.getRootPane()
                : ((JDialog) window).getRootPane();
        ResizeGlass glass = new ResizeGlass(window);
        root.setGlassPane(glass);
        glass.setVisible(true);
    }

    private static final class DragHandler extends MouseAdapter {
        private final Window window;
        private Point pressedScreen;
        private Point startLocation;
        private boolean dragging;

        DragHandler(Window window) { this.window = window; }

        @Override public void mousePressed(MouseEvent e) {
            if (e.getButton() != MouseEvent.BUTTON1) return;
            if (window instanceof JFrame frame
                    && Boolean.TRUE.equals(frame.getRootPane().getClientProperty("eluajs.maximized"))) return;
            pressedScreen = e.getLocationOnScreen();
            startLocation = window.getLocation();
            dragging = true;
        }

        @Override public void mouseDragged(MouseEvent e) {
            if (!dragging || pressedScreen == null || startLocation == null) return;
            Point now = e.getLocationOnScreen();
            window.setLocation(startLocation.x + now.x - pressedScreen.x,
                    startLocation.y + now.y - pressedScreen.y);
        }

        @Override public void mouseReleased(MouseEvent e) { dragging = false; }

        @Override public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) toggleFromTitle(window);
        }
    }

    private static final class WindowButton extends JButton {
        private String iconName;
        private final boolean closeStyle;

        WindowButton(String iconName, boolean closeStyle) {
            this.iconName = iconName;
            this.closeStyle = closeStyle;
            setFocusable(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(null);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Dimension size = new Dimension(36, 28);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        void setIconName(String name) { this.iconName = name; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isRollover()) {
                g2.setColor(closeStyle ? new Color(196, 43, 28) : new Color(255, 255, 255, 20));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            }
            Color iconColor = closeStyle && getModel().isRollover() ? Color.WHITE : Theme.TEXT_SOFT;
            Icons.draw(g2, iconName, (getWidth() - 15) / 2, (getHeight() - 15) / 2, 15, iconColor);
            g2.dispose();
        }
    }

    private static final class ResizeGlass extends JComponent {
        private final Window window;
        private int edge = 0;
        private Point pressedScreen;
        private Rectangle startBounds;
        private boolean listenersInstalled;

        ResizeGlass(Window window) {
            this.window = window;
            setOpaque(false);
        }

        @Override public boolean contains(int x, int y) {
            return edgeAt(x, y) != 0;
        }

        @Override public void setVisible(boolean visible) {
            super.setVisible(visible);
            if (visible && !listenersInstalled) {
                listenersInstalled = true;
                addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        edge = edgeAt(e.getX(), e.getY());
                        if (edge == 0) return;
                        pressedScreen = e.getLocationOnScreen();
                        startBounds = window.getBounds();
                    }
                    @Override public void mouseReleased(MouseEvent e) {
                        edge = 0;
                        pressedScreen = null;
                        startBounds = null;
                    }
                });
                addMouseMotionListener(new MouseMotionAdapter() {
                    @Override public void mouseDragged(MouseEvent e) {
                        if (edge == 0 || pressedScreen == null || startBounds == null) return;
                        resize(e.getLocationOnScreen());
                    }
                    @Override public void mouseMoved(MouseEvent e) {
                        int edge = edgeAt(e.getX(), e.getY());
                        setCursor(Cursor.getPredefinedCursor(edge == 0 ? Cursor.DEFAULT_CURSOR : edge));
                    }
                });
            }
        }

        private int edgeAt(int x, int y) {
            int w = getWidth();
            int h = getHeight();
            boolean left = x <= RESIZE_BORDER;
            boolean right = x >= w - RESIZE_BORDER;
            boolean top = y <= RESIZE_BORDER;
            boolean bottom = y >= h - RESIZE_BORDER;
            if (top && left) return Cursor.NW_RESIZE_CURSOR;
            if (top && right) return Cursor.NE_RESIZE_CURSOR;
            if (bottom && left) return Cursor.SW_RESIZE_CURSOR;
            if (bottom && right) return Cursor.SE_RESIZE_CURSOR;
            if (top) return Cursor.N_RESIZE_CURSOR;
            if (bottom) return Cursor.S_RESIZE_CURSOR;
            if (left) return Cursor.W_RESIZE_CURSOR;
            if (right) return Cursor.E_RESIZE_CURSOR;
            return 0;
        }

        private void resize(Point now) {
            int dx = now.x - pressedScreen.x;
            int dy = now.y - pressedScreen.y;
            Rectangle r = new Rectangle(startBounds);
            Dimension min = window.getMinimumSize();
            int minW = Math.max(320, min == null ? 0 : min.width);
            int minH = Math.max(240, min == null ? 0 : min.height);
            switch (edge) {
                case Cursor.N_RESIZE_CURSOR -> { r.y += dy; r.height -= dy; }
                case Cursor.S_RESIZE_CURSOR -> r.height += dy;
                case Cursor.W_RESIZE_CURSOR -> { r.x += dx; r.width -= dx; }
                case Cursor.E_RESIZE_CURSOR -> r.width += dx;
                case Cursor.NW_RESIZE_CURSOR -> { r.x += dx; r.y += dy; r.width -= dx; r.height -= dy; }
                case Cursor.NE_RESIZE_CURSOR -> { r.y += dy; r.width += dx; r.height -= dy; }
                case Cursor.SW_RESIZE_CURSOR -> { r.x += dx; r.width -= dx; r.height += dy; }
                case Cursor.SE_RESIZE_CURSOR -> { r.width += dx; r.height += dy; }
                default -> { return; }
            }
            boolean north = edge == Cursor.N_RESIZE_CURSOR || edge == Cursor.NW_RESIZE_CURSOR || edge == Cursor.NE_RESIZE_CURSOR;
            boolean west = edge == Cursor.W_RESIZE_CURSOR || edge == Cursor.NW_RESIZE_CURSOR || edge == Cursor.SW_RESIZE_CURSOR;
            if (r.width < minW) {
                if (west) r.x = startBounds.x + startBounds.width - minW;
                r.width = minW;
            }
            if (r.height < minH) {
                if (north) r.y = startBounds.y + startBounds.height - minH;
                r.height = minH;
            }
            window.setBounds(r);
        }
    }
}
