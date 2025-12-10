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

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
    private final JLabel playerCountLabel = new JLabel("0/20");
    private final JLabel serverStatusDot = new JLabel("●");
    private final JLabel serverStatusLabel = new JLabel("Offline");
    private final JButton newsToggleButton = new JButton("▲ NEWS");
    private JPanel newsPanel;
    private boolean newsExpanded = false;
    private JPanel container;
    private Image logoImage;

    // Icons
    private final Icon instanceIcon = SwingHelper.createIcon(Launcher.class, "instance_icon.png", 16, 16);
    private final Icon downloadIcon = SwingHelper.createIcon(Launcher.class, "download_icon.png", 16, 16);
    
    // Custom colors - Modern Helios palette
    private static final Color SUCCESS_GREEN = new Color(92, 184, 92); // #5CB85C
    private static final Color ERROR_RED = new Color(217, 83, 79); // #D9534F
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255); // #FFFFFF
    private static final Color TEXT_SECONDARY = new Color(180, 180, 180); // #B4B4B4
    private static final Color SEPARATOR_COLOR = new Color(100, 100, 100); // #646464
    private static final Color NEWS_BG = new Color(0, 0, 0, 200); // rgba(0, 0, 0, 0.78)

    /**
     * Create a new frame.
     *
     * @param launcher the launcher
     */
    public FancyLauncherFrame(@NonNull Launcher launcher) {
        super(launcher);
        this.launcher = launcher;

        setSize(1000, 650);  // Larger window for immersive feel
        setLocationRelativeTo(null);

        // Load logo image
        try {
            logoImage = ImageIO.read(FancyLauncherFrame.class.getResourceAsStream("icon.png"));
        } catch (Exception e) {
            logoImage = null;
        }

        // We rebuild the UI entirely for the Helios-style version
        container.removeAll();
        container.setLayout(new BorderLayout());
        
        // Main layered pane for floating elements
        JLayeredPane layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                super.doLayout();
                int w = getWidth();
                int h = getHeight();
                
                // Position components absolutely
                if (getComponentCount() > 0) {
                    // Background layer (layer 0)
                    Component bg = getComponent(0);
                    bg.setBounds(0, 0, w, h);
                    
                    // Top-left logo (layer 2)
                    if (getComponentCount() > 1) {
                        Component logo = getComponent(1);
                        logo.setBounds(20, 20, 80, 80);
                    }
                    
                    // Top-right account (layer 2)
                    if (getComponentCount() > 2) {
                        Component account = getComponent(2);
                        Dimension accSize = account.getPreferredSize();
                        account.setBounds(w - accSize.width - 20, 20, accSize.width, 64);
                    }
                    
                    // Right sidebar (layer 3)
                    if (getComponentCount() > 3) {
                        Component sidebar = getComponent(3);
                        Dimension sbSize = sidebar.getPreferredSize();
                        sidebar.setBounds(w - sbSize.width - 20, 110, sbSize.width, h - 200);
                    }
                    
                    // Bottom bar (layer 1)
                    if (getComponentCount() > 4) {
                        Component bottom = getComponent(4);
                        bottom.setBounds(0, h - 60, w, 60);
                    }
                    
                    // News panel (layer 4) - when visible
                    if (getComponentCount() > 5) {
                        Component news = getComponent(5);
                        news.setBounds(20, h - 360, w - 40, 280);
                    }
                }
            }
        };
        
        // Layer 0: Background - transparent background panel
        JPanel backgroundContainer = new JPanel(new BorderLayout());
        backgroundContainer.setOpaque(false);
        layeredPane.add(backgroundContainer, Integer.valueOf(0));
        
        // Layer 2: Top-left logo
        JPanel logoPanel = createLogoPanel();
        layeredPane.add(logoPanel, Integer.valueOf(2));
        
        // Layer 2: Top-right account display
        JPanel accountPanel = createAccountDisplay();
        layeredPane.add(accountPanel, Integer.valueOf(2));
        
        // Layer 3: Right sidebar (settings icon)
        JPanel sidebar = createRightSidebar();
        layeredPane.add(sidebar, Integer.valueOf(3));
        
        // Layer 1: Bottom status bar
        JPanel bottomBar = createBottomBar();
        layeredPane.add(bottomBar, Integer.valueOf(1));
        
        // Layer 4: News panel (collapsible)
        newsPanel = createNewsPanel2();
        newsPanel.setVisible(false);
        layeredPane.add(newsPanel, Integer.valueOf(4));
        
        container.add(layeredPane, BorderLayout.CENTER);
        
        // Configure Instance Selector
        DefaultComboBoxModel<Instance> model = new DefaultComboBoxModel<>();
        instanceSelector.setModel(model);
        instanceSelector.setRenderer(new ModpackComboRenderer());
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
        updateAccountInfo(); // Populate initial account data
        
        // Start async server ping
        pingServer();
    }

    private JPanel createLogoPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw circular border (white ring)
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3));
                g2.drawOval(2, 2, getWidth()-5, getHeight()-5);
                
                // Draw logo image inside circle
                if (logoImage != null) {
                    g2.setClip(new Ellipse2D.Float(5, 5, getWidth()-10, getHeight()-10));
                    g2.drawImage(logoImage, 5, 5, getWidth()-10, getHeight()-10, null);
                }
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(80, 80));
        return panel;
    }
    
    private JPanel createAccountDisplay() {
        JPanel panel = new JPanel(new MigLayout("insets 0, gap 10", "[right][64!]", "[]"));
        panel.setOpaque(false);
        
        // Username label
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameLabel.setForeground(Color.WHITE);
        
        // Circular avatar with ring
        headLabel.setPreferredSize(new Dimension(64, 64));
        
        panel.add(nameLabel);
        panel.add(headLabel);
        
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                AccountSelectDialog.showAccountRequest(FancyLauncherFrame.this, launcher);
                updateAccountInfo();
            }
        });
        
        return panel;
    }
    
    private JPanel createRightSidebar() {
        JPanel panel = new JPanel(new MigLayout("insets 10, gap 0, flowy", "[center]", "[]15[]"));
        panel.setOpaque(false);
        
        // Settings button
        JButton settingsBtn = createIconButton("⚙", "Settings", null);
        settingsBtn.addActionListener(e -> showSettingsMenu(settingsBtn, 0, settingsBtn.getHeight()));
        
        panel.add(settingsBtn);
        
        return panel;
    }

    private JButton createIconButton(String icon, String tooltip, ActionListener action) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        if (action != null) {
            btn.addActionListener(action);
        }
        
        // Hover effect
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setForeground(new Color(200, 200, 200));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setForeground(Color.WHITE);
            }
        });
        
        return btn;
    }
    
    private JPanel createBottomBar() {
        JPanel panel = new JPanel(new MigLayout("insets 15 30 15 30, gap 0", 
            "[][][10!][][10!][][grow, center][][10!][][]", "[center]"));
        panel.setOpaque(false);
        
        // Players count
        JLabel playersHeading = new JLabel("PLAYERS");
        playersHeading.setFont(new Font("Segoe UI", Font.BOLD, 11));
        playersHeading.setForeground(TEXT_SECONDARY);
        
        playerCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        playerCountLabel.setForeground(Color.WHITE);
        
        // Separator
        JLabel sep1 = createSeparator();
        
        // Server status
        serverStatusDot.setForeground(ERROR_RED); // Default to red
        
        serverStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        serverStatusLabel.setForeground(Color.WHITE);
        
        // Separator
        JLabel sep2 = createSeparator();
        
        // News toggle with arrow
        newsToggleButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        newsToggleButton.setForeground(Color.WHITE);
        newsToggleButton.setContentAreaFilled(false);
        newsToggleButton.setBorderPainted(false);
        newsToggleButton.setFocusPainted(false);
        newsToggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newsToggleButton.addActionListener(e -> toggleNewsPanel());
        
        // PLAY button - Large and prominent
        launchButton.setText("PLAY");
        launchButton.setFont(new Font("Segoe UI", Font.BOLD, 28));
        launchButton.setForeground(Color.WHITE);
        launchButton.setContentAreaFilled(false);
        launchButton.setBorderPainted(false);
        launchButton.setFocusPainted(false);
        launchButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Hover glow effect
        launchButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                launchButton.setForeground(new Color(100, 255, 100));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                launchButton.setForeground(Color.WHITE);
            }
        });
        
        // Separator
        JLabel sep3 = createSeparator();
        
        // Modpack selector dot
        JLabel modpackDot = new JLabel("●");
        modpackDot.setForeground(SUCCESS_GREEN);
        
        // Style the modpack selector
        instanceSelector.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        instanceSelector.setForeground(Color.WHITE);
        instanceSelector.setBackground(new Color(40, 40, 40));
        
        // Layout
        panel.add(playersHeading);
        panel.add(playerCountLabel, "gapleft 5");
        panel.add(sep1, "gapleft 15, gapright 15");
        panel.add(serverStatusDot);
        panel.add(serverStatusLabel, "gapleft 5");
        panel.add(sep2, "gapleft 15, gapright 15");
        panel.add(newsToggleButton);
        panel.add(launchButton, "gapleft 30, gapright 30");
        panel.add(sep3, "gapleft 15, gapright 15");
        panel.add(modpackDot);
        panel.add(instanceSelector, "gapleft 5, w 180!");
        
        return panel;
    }

    private JLabel createSeparator() {
        JLabel sep = new JLabel("|");
        sep.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sep.setForeground(SEPARATOR_COLOR);
        return sep;
    }
    
    private JPanel createNewsPanel2() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 20", "[grow]", "[grow]"));
        panel.setBackground(NEWS_BG);
        panel.setVisible(false);
        
        // Changelog content
        WebpagePanel changelog = createNewsPanel();
        changelog.setOpaque(false);
        changelog.setBrowserBorder(new EmptyBorder(0, 0, 0, 0));
        
        panel.add(changelog, "grow");
        
        return panel;
    }

    private void toggleNewsPanel() {
        newsExpanded = !newsExpanded;
        newsPanel.setVisible(newsExpanded);
        // Update arrow direction on toggle button
        newsToggleButton.setText(newsExpanded ? "▼ NEWS" : "▲ NEWS");
    }

    private void showSettingsMenu(Component invoker, int x, int y) {
        JPopupMenu popup = new JPopupMenu();
        
        // Launcher Settings
        JMenuItem settingsItem = new JMenuItem("Launcher Settings...");
        settingsItem.addActionListener(e -> {
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
                writeVarInt(handshake, 47); // Protocol Version (1.8 is 47)
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
                    playerCountLabel.setText(online + "/" + max);
                    serverStatusDot.setForeground(SUCCESS_GREEN);
                    serverStatusLabel.setText("Online");
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    playerCountLabel.setText("0/0");
                    serverStatusDot.setForeground(ERROR_RED);
                    serverStatusLabel.setText("Offline");
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

    private void updateAccountInfo() {
        AccountList accounts = launcher.getAccounts();
        String username = "Guest";
        Icon headIcon = SwingHelper.createIcon(Launcher.class, "default_skin.png", 64, 64);

        if (accounts.getSize() > 0) {
            SavedSession session = accounts.getElementAt(0);
            username = session.getUsername();
            if (session.getAvatarImage() != null) {
                ImageIcon raw = new ImageIcon(session.getAvatarImage());
                headIcon = new ImageIcon(getCircularImageWithBorder(raw.getImage(), 64, 64));
            }
        }

        nameLabel.setText(username);
        headLabel.setIcon(headIcon);
    }

    private Image getCircularImageWithBorder(Image img, int w, int h) {
        BufferedImage avatar = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = avatar.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw white border ring
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.drawOval(1, 1, w-3, h-3);
        
        // Clip to circle and draw image
        g2.setClip(new Ellipse2D.Float(3, 3, w-6, h-6));
        g2.drawImage(img, 3, 3, w-6, h-6, null);
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
                launchButton.setText("INSTALL");
            } else if (instance.isUpdatePending()) {
                launchButton.setText("UPDATE");
            } else {
                launchButton.setText("PLAY");
            }
        }
    }

    private class ModpackComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Instance) {
                Instance instance = (Instance) value;
                setText(instance.getTitle());
                
                if (instance.isLocal() && instance.isInstalled()) {
                    setIcon(instanceIcon);
                    if (instance.isUpdatePending()) {
                        setText(instance.getTitle() + " (Update)");
                    }
                } else {
                    setIcon(downloadIcon);
                    setText(instance.getTitle() + " (Install)");
                }
            }
            return this;
        }
    }
}