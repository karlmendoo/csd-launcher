/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skcraft.launcher.auth.AccountList;
import com.skcraft.launcher.auth.SavedSession;
import com.skcraft.launcher.dialog.AccountSelectDialog;
import com.skcraft.launcher.dialog.LauncherFrame;
import com.skcraft.launcher.dialog.component.BetterComboBox;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.swing.WebpagePanel;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class FancyLauncherFrame extends LauncherFrame {

    private final Launcher launcher;
    private final JComboBox<Instance> instanceSelector = new BetterComboBox<>();
    private final JLabel nameLabel = new JLabel();
    private final JLabel headLabel = new JLabel();
    private final JLabel serverStatusLabel = new JLabel("Pinging...");
    private JPanel container;

    // Icons
    private final Icon instanceIcon = SwingHelper.createIcon(Launcher.class, "instance_icon.png", 16, 16);
    private final Icon downloadIcon = SwingHelper.createIcon(Launcher.class, "download_icon.png", 16, 16);
    
    // Custom colors - Modern palette
    private static final Color GLASS_COLOR = new Color(20, 20, 20, (int)(0.85 * 255)); // rgba(20, 20, 20, 0.85)
    private static final Color HOVER_COLOR = new Color(255, 255, 255, 20); // rgba(255, 255, 255, 0.08)
    private static final Color PRIMARY_BLUE = new Color(0, 120, 212); // #0078D4
    private static final Color SUCCESS_GREEN = new Color(40, 167, 69); // #28a745
    private static final Color WARNING_ORANGE = new Color(253, 126, 20); // #fd7e14
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255); // #FFFFFF
    private static final Color TEXT_SECONDARY = new Color(176, 176, 176); // #B0B0B0
    private static final Color BORDER_SUBTLE = new Color(255, 255, 255, (int)(0.1 * 255)); // rgba(255, 255, 255, 0.1)

    /**
     * Create a new frame.
     *
     * @param launcher the launcher
     */
    public FancyLauncherFrame(@NonNull Launcher launcher) {
        super(launcher);
        this.launcher = launcher;

        setSize(850, 550);
        setLocationRelativeTo(null);

        // We rebuild the UI entirely for the fancy version
        container.removeAll();
        // Fixed layout rows to prevent cutoff: [Header][Content][Footer]
        container.setLayout(new MigLayout("fill, insets 0, gap 0", "[grow]", "[60!][grow][60!]"));

        // 1. Top Bar (Logo + Account Manager)
        // Modified columns to include server status: [Logo][Status][Spacer][Account]
        JPanel topBar = new GlassPanel(new MigLayout("fill, insets 10 20 10 20", "[][][grow][right]", "[]"));
        // Logo
        JLabel logoLabel = new JLabel("Changelogs");
        logoLabel.setFont(logoLabel.getFont().deriveFont(Font.BOLD, 18f));
        logoLabel.setForeground(TEXT_PRIMARY);

        // Server Status
        serverStatusLabel.setFont(serverStatusLabel.getFont().deriveFont(Font.BOLD, 12f));
        serverStatusLabel.setForeground(TEXT_SECONDARY);
        
        // Account Manager
        JPanel accountPanel = createAccountPanel();
        updateAccountInfo(); // Populate initial data
        
        topBar.add(logoLabel);
        topBar.add(serverStatusLabel, "gapleft 20");
        topBar.add(new JLabel("")); // Spacer
        topBar.add(accountPanel);

        // Start async ping
        pingServer();

        // 2. Center (Webpage) - Now full width
        WebpagePanel webView = createNewsPanel();
        webView.setOpaque(false);

        // 3. Bottom Bar (Controls)
        // Columns: [Refresh] [SelfUpdate] [Checkbox] [Selector(Grow)] [Options] [Launch]
        JPanel bottomBar = new GlassPanel(new MigLayout("fill, insets 10 20 10 20", "[][][][grow][][]", "[]"));

        // Style the Options button to be minimalist (Icon only)
        optionsButton.setText("Settings");
        optionsButton.setFont(optionsButton.getFont().deriveFont(Font.BOLD, 12f));
        optionsButton.setToolTipText("Launcher Options");
        optionsButton.setContentAreaFilled(true);
        optionsButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        optionsButton.setForeground(TEXT_SECONDARY);
        optionsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Remove default action listeners (which opened config directly)
        for (java.awt.event.ActionListener al : optionsButton.getActionListeners()) {
            optionsButton.removeActionListener(al);
        }

        // Add menu popup behavior
        optionsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showSettingsMenu(e.getComponent(), e.getX(), e.getY());
            }
        });

        // Configure Instance Selector
        DefaultComboBoxModel<Instance> model = new DefaultComboBoxModel<>();
        instanceSelector.setModel(model);
        instanceSelector.setRenderer(new InstanceComboRenderer());
        instanceSelector.addActionListener(e -> {
            // Sync selection with the invisible table so base logic works
            int index = instanceSelector.getSelectedIndex();
            if (index >= 0 && index < getInstancesTable().getRowCount()) {
                getInstancesTable().setRowSelectionInterval(index, index);
            }
            // Update button text based on state
            updateLaunchButton();
        });

        // Add listener to auto-refresh the dropdown when instances load
        getInstancesTable().getModel().addTableModelListener(e -> updateInstanceList());
        updateInstanceList(); // Initial population

        bottomBar.add(refreshButton);
        bottomBar.add(selfUpdateButton, "gapleft 5, hidemode 3");
        bottomBar.add(updateCheck, "gapleft 10");
        bottomBar.add(instanceSelector, "growx, width 200:300:400, gapright 10");
        bottomBar.add(optionsButton);
        bottomBar.add(launchButton, "w 100!, h 32!");

        // Assemble using explicit cells to prevent overlapping/cutoff
        container.add(topBar, "cell 0 0, grow");
        container.add(webView, "cell 0 1, grow");
        container.add(bottomBar, "cell 0 2, grow");
        
        SwingHelper.removeOpaqueness(updateCheck);
        updateCheck.setForeground(TEXT_PRIMARY);
        
        // Style buttons with pill-shaped appearance
        stylePillButton(launchButton, new Color(34, 139, 34), Color.WHITE);      // Green Play
        stylePillButton(refreshButton, new Color(60, 60, 60), Color.WHITE);       // Gray
        stylePillButton(optionsButton, new Color(60, 60, 60), Color.WHITE);       // Gray
        stylePillButton(selfUpdateButton, new Color(60, 60, 60), Color.WHITE);    // Gray
    }

    private void styleButton(JButton button) {
        button.setForeground(TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void stylePillButton(JButton button, Color bgColor, Color fgColor) {
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBackground(bgColor);
        button.setForeground(fgColor);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                AbstractButton b = (AbstractButton) c;
                int arc = c.getHeight();
                
                if (b.getModel().isPressed()) {
                    g2.setColor(b.getBackground().darker());
                } else if (b.getModel().isRollover()) {
                    g2.setColor(b.getBackground().brighter());
                } else {
                    g2.setColor(b.getBackground());
                }
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
                
                g2.setColor(b.getForeground());
                g2.setFont(b.getFont());
                FontMetrics fm = g2.getFontMetrics();
                String text = b.getText();
                int x = (c.getWidth() - fm.stringWidth(text)) / 2;
                int y = (c.getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(text, x, y);
                
                g2.dispose();
            }
        });
    }

    private void showSettingsMenu(Component invoker, int x, int y) {
        JPopupMenu popup = new JPopupMenu();
        
        // Launcher Settings
        JMenuItem settingsItem = new JMenuItem("Launcher Settings...");
        settingsItem.addActionListener(e -> {
            // Call the protected method from LauncherFrame
            com.skcraft.launcher.dialog.ConfigurationDialog configDialog = 
                new com.skcraft.launcher.dialog.ConfigurationDialog(this, launcher);
            configDialog.setVisible(true);
        });
        popup.add(settingsItem);

        popup.addSeparator();

        Instance instance = (Instance) instanceSelector.getSelectedItem();
        if (instance != null) {
            // Open Folder
            JMenuItem openFolderItem = new JMenuItem("Open Modpack Folder");
            openFolderItem.addActionListener(e -> SwingHelper.browseDir(instance.getContentDir(), this));
            popup.add(openFolderItem);

            // Repair / Reinstall
            JMenuItem repairItem = new JMenuItem("Repair / Reinstall...");
            repairItem.addActionListener(e -> {
                int result = JOptionPane.showConfirmDialog(this,
                        "This will check all files for integrity and redownload any missing or corrupt mods.\n" +
                        "Your saves and options will be kept.\n\n" +
                        "Continue?",
                        "Repair Modpack", JOptionPane.YES_NO_OPTION);
                
                if (result == JOptionPane.YES_OPTION) {
                    // Force update flag
                    instance.setUpdatePending(true);
                    com.skcraft.launcher.persistence.Persistence.commitAndForget(instance);
                    // Update UI button state
                    updateLaunchButton();
                    // Trigger update process immediately
                    launch();
                }
            });
            popup.add(repairItem);
        }

        popup.show(invoker, x, y - popup.getPreferredSize().height);
    }


    private void updateInstanceList() {
        SwingUtilities.invokeLater(() -> {
            DefaultComboBoxModel<Instance> model = (DefaultComboBoxModel<Instance>) instanceSelector.getModel();
            Object selected = instanceSelector.getSelectedItem();
            
            model.removeAllElements();
            for (Instance instance : launcher.getInstances().getInstances()) {
                model.addElement(instance);
            }
            
            if (model.getSize() > 0) {
                // Restore selection if possible, otherwise default to first
                if (selected != null && model.getIndexOf(selected) != -1) {
                    instanceSelector.setSelectedItem(selected);
                } else {
                    instanceSelector.setSelectedIndex(0);
                }
            }
        });
    }

    private void pingServer() {
        launcher.getExecutor().submit(() -> {
            String host = "35.221.228.109";
            int port = 6969;

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 4000);

                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                // Handshake
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream handshake = new DataOutputStream(b);
                handshake.writeByte(0x00); // Packet ID
                writeVarInt(handshake, 47); // Protocol Version (1.8 is 47, generally accepted for ping)
                writeVarInt(handshake, host.length());
                handshake.write(host.getBytes(StandardCharsets.UTF_8));
                handshake.writeShort(port);
                writeVarInt(handshake, 1); // State (1 for Status)

                writeVarInt(out, b.size()); // Length
                out.write(b.toByteArray()); // Data

                // Status Request
                out.writeByte(0x01); // Length
                out.writeByte(0x00); // Packet ID

                // Read Response
                readVarInt(in); // Packet Length
                readVarInt(in); // Packet ID
                int jsonLength = readVarInt(in);

                byte[] data = new byte[jsonLength];
                in.readFully(data);
                String json = new String(data, StandardCharsets.UTF_8);

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json);
                JsonNode players = root.get("players");
                int online = players.get("online").asInt();
                int max = players.get("max").asInt();

                SwingUtilities.invokeLater(() -> {
                    serverStatusLabel.setText("\u25CF " + online + "/" + max + " Online");
                    serverStatusLabel.setForeground(new Color(92, 184, 92)); // Success Green
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    serverStatusLabel.setText("\u25CF Offline");
                    serverStatusLabel.setForeground(new Color(217, 83, 79)); // Error Red
                });
            }
        });
    }

    private void writeVarInt(DataOutputStream out, int paramInt) throws IOException {
        while (true) {
            if ((paramInt & 0xFFFFFF80) == 0) {
                out.writeByte(paramInt);
                return;
            }
            out.writeByte(paramInt & 0x7F | 0x80);
            paramInt >>>= 7;
        }
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int i = 0;
        int j = 0;
        while (true) {
            int k = in.readByte();
            i |= (k & 0x7F) << j++ * 7;
            if (j > 5) throw new RuntimeException("VarInt too big");
            if ((k & 0x80) != 128) break;
        }
        return i;
    }

    private JPanel createAccountPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 5, fill", "[right][40!]", "[]")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (isOpaque() && getBackground() != null) {
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBackground(new Color(0, 0, 0, 0));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(TEXT_PRIMARY);

        panel.add(nameLabel);
        panel.add(headLabel);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                AccountSelectDialog.showAccountRequest(FancyLauncherFrame.this, launcher);
                updateAccountInfo();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                panel.setOpaque(true);
                panel.setBackground(new Color(70, 70, 70)); // Solid color, not transparent
                panel.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                panel.setOpaque(false);
                panel.setBackground(new Color(0, 0, 0, 0));
                panel.repaint();
            }
        });

        return panel;
    }

    private void updateAccountInfo() {
        AccountList accounts = launcher.getAccounts();
        String username = "Guest";
        Icon headIcon = SwingHelper.createIcon(Launcher.class, "default_skin.png", 32, 32);

        if (accounts.getSize() > 0) {
            SavedSession session = accounts.getElementAt(0);
            username = session.getUsername();
            if (session.getAvatarImage() != null) {
                ImageIcon raw = new ImageIcon(session.getAvatarImage());
                headIcon = new ImageIcon(getCircularImage(raw.getImage(), 32, 32));
            }
        }

        nameLabel.setText("Welcome, " + username);
        headLabel.setIcon(headIcon);
    }

    private Image getCircularImage(Image img, int w, int h) {
        BufferedImage avatar = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = avatar.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new Ellipse2D.Float(0, 0, w, h));
        g2.drawImage(img, 0, 0, w, h, null);
        g2.dispose();
        return avatar;
    }

    @Override
    protected JPanel createContainerPanel() {
        this.container = new FancyBackgroundPanel();
        return this.container;
    }

    @Override
    protected WebpagePanel createNewsPanel() {
        WebpagePanel panel = super.createNewsPanel();
        panel.setBrowserBorder(new EmptyBorder(0, 0, 0, 0));
        return panel;
    }

    private void updateLaunchButton() {
        Instance instance = (Instance) instanceSelector.getSelectedItem();
        if (instance != null) {
            if (!instance.isInstalled()) {
                launchButton.setText("Install");
                launchButton.setBackground(PRIMARY_BLUE);
                launchButton.setForeground(TEXT_PRIMARY);
            } else if (instance.isUpdatePending()) {
                launchButton.setText("Update");
                launchButton.setBackground(WARNING_ORANGE);
                launchButton.setForeground(TEXT_PRIMARY);
            } else {
                launchButton.setText("Play");
                launchButton.setBackground(SUCCESS_GREEN);
                launchButton.setForeground(TEXT_PRIMARY);
            }
        }
    }

    private class InstanceComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Instance) {
                Instance instance = (Instance) value;
                setText(instance.getTitle());
                
                if (instance.isLocal() && instance.isInstalled()) {
                    setIcon(instanceIcon);
                    if (instance.isUpdatePending()) {
                        setText(instance.getTitle() + " (Update Available)");
                    }
                } else {
                    setIcon(downloadIcon);
                    setText(instance.getTitle() + " (Install)");
                }
            }
            return this;
        }
    }

    private static class GlassPanel extends JPanel {
        public GlassPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(GLASS_COLOR);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}