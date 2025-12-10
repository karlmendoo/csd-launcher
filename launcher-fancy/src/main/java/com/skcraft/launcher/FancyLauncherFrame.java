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
import com.skcraft.launcher.ui.*;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

public class FancyLauncherFrame extends LauncherFrame {

    private final Launcher launcher;
    private final JComboBox<Instance> instanceSelector = new BetterComboBox<>();
    private final JLabel nameLabel = new JLabel();
    private CircularImageComponent headComponent;
    private CircularImageComponent serverLogoComponent;
    private StatusIndicator playerStatusIndicator;
    private StatusIndicator mojangStatusIndicator;
    private JLabel selectedServerLabel;
    private JPanel container;
    private Timer refreshTimer;

    // Icons
    private final Icon instanceIcon = SwingHelper.createIcon(Launcher.class, "instance_icon.png", 16, 16);
    private final Icon downloadIcon = SwingHelper.createIcon(Launcher.class, "download_icon.png", 16, 16);
    
    // Refresh intervals for periodic status updates
    private static final long INITIAL_REFRESH_DELAY_MS = 30000; // 30 seconds
    private static final long REFRESH_INTERVAL_MS = 45000; // 45 seconds
    
    // Social media URLs (configurable)
    private static final String DISCORD_URL = "https://discord.gg/yourserver";
    private static final String TWITTER_URL = "https://twitter.com/yourserver";
    private static final String INSTAGRAM_URL = "https://instagram.com/yourserver";
    private static final String YOUTUBE_URL = "https://youtube.com/yourserver";
    // Custom colors - Modern palette (from problem statement)
    private static final Color GLASS_COLOR = new Color(20, 20, 20, (int)(0.85 * 255)); // rgba(20, 20, 20, 0.85)
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

        // Make window larger for modern immersive UI
        setSize(1000, 650);
        setLocationRelativeTo(null);

        // We rebuild the UI entirely for the fancy version
        container.removeAll();
        // LayeredPane-style layout: Background fills everything, content overlays on top
        container.setLayout(new BorderLayout());
        
        // Create main content panel with Helios-style layout
        JPanel contentPanel = createHeliosStyleLayout();
        container.add(contentPanel, BorderLayout.CENTER);
        
        // Start periodic refresh for server status
        startPeriodicRefresh();
        
        // Initial data population
        updateAccountInfo();
        updateInstanceList();
        pingServer();
        checkMojangStatus();
    }
    
    /**
     * Creates the Helios-style layout with all UI elements.
     */
    private JPanel createHeliosStyleLayout() {
        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
            }
        };
        mainPanel.setOpaque(false);
        
        // Top-left: Server branding
        JPanel topLeftPanel = createServerBrandingPanel();
        
        // Top-right: User profile section
        JPanel topRightPanel = createUserProfilePanel();
        
        // Right sidebar: Social/utility icons
        JPanel rightSidebar = createRightSidebar();
        
        // Bottom bar: Status and controls
        JPanel bottomBar = createBottomControlBar();
        
        // Use a container with proper layout
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.add(topLeftPanel, BorderLayout.WEST);
        topBar.add(topRightPanel, BorderLayout.EAST);
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(rightSidebar, BorderLayout.EAST);
        
        mainPanel.add(topBar, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(bottomBar, BorderLayout.SOUTH);
        
        return mainPanel;
    }
    
    /**
     * Creates the server branding panel (top-left with circular logo).
     */
    private JPanel createServerBrandingPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
        panel.setOpaque(false);
        
        try {
            Image logoImage = ImageIO.read(FancyLauncherFrame.class.getResourceAsStream("server_logo.png"));
            serverLogoComponent = new CircularImageComponent(logoImage, 50, PRIMARY_BLUE, 3);
            panel.add(serverLogoComponent);
        } catch (Exception e) {
            // Fallback to simple colored circle
            BufferedImage fallback = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = fallback.createGraphics();
            g2.setColor(PRIMARY_BLUE);
            g2.fillOval(0, 0, 50, 50);
            g2.dispose();
            serverLogoComponent = new CircularImageComponent(fallback, 50, PRIMARY_BLUE, 3);
            panel.add(serverLogoComponent);
        }
        
        return panel;
    }
    
    /**
     * Creates the user profile panel (top-right with username and avatar).
     */
    private JPanel createUserProfilePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 20));
        panel.setOpaque(false);
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // Username label
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        nameLabel.setForeground(TEXT_PRIMARY);
        panel.add(nameLabel);
        
        // Circular avatar
        Icon defaultSkinIcon = SwingHelper.createIcon(Launcher.class, "default_skin.png", 40, 40);
        Image defaultSkin = ((ImageIcon) defaultSkinIcon).getImage();
        headComponent = new CircularImageComponent(defaultSkin, 40, BORDER_SUBTLE, 2);
        panel.add(headComponent);
        
        // Click to open account management
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                AccountSelectDialog.showAccountRequest(FancyLauncherFrame.this, launcher);
                updateAccountInfo();
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                nameLabel.setForeground(PRIMARY_BLUE);
                panel.repaint();
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                nameLabel.setForeground(TEXT_PRIMARY);
                panel.repaint();
            }
        });
        
        return panel;
    }
    
    /**
     * Creates the right sidebar with social/utility icons.
     */
    private JPanel createRightSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setOpaque(false);
        sidebar.setBorder(BorderFactory.createEmptyBorder(80, 10, 10, 15));
        
        // Settings button (gear icon)
        SocialLinkButton settingsBtn = new SocialLinkButton(IconFactory.createSettingsIcon(), "Settings");
        settingsBtn.addActionListener(e -> {
            FancyConfigurationDialog configDialog = new FancyConfigurationDialog(this, launcher);
            configDialog.setVisible(true);
        });
        sidebar.add(settingsBtn);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Discord button
        SocialLinkButton discordBtn = new SocialLinkButton(IconFactory.createDiscordIcon(), "Discord");
        discordBtn.addActionListener(e -> openURL(DISCORD_URL));
        sidebar.add(discordBtn);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Twitter button
        SocialLinkButton twitterBtn = new SocialLinkButton(IconFactory.createTwitterIcon(), "Twitter/X");
        twitterBtn.addActionListener(e -> openURL(TWITTER_URL));
        sidebar.add(twitterBtn);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Instagram button
        SocialLinkButton instagramBtn = new SocialLinkButton(IconFactory.createInstagramIcon(), "Instagram");
        instagramBtn.addActionListener(e -> openURL(INSTAGRAM_URL));
        sidebar.add(instagramBtn);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // YouTube button
        SocialLinkButton youtubeBtn = new SocialLinkButton(IconFactory.createYouTubeIcon(), "YouTube");
        youtubeBtn.addActionListener(e -> openURL(YOUTUBE_URL));
        sidebar.add(youtubeBtn);
        
        return sidebar;
    }
    
    /**
     * Creates the bottom control bar with glass effect.
     */
    private JPanel createBottomControlBar() {
        GlassPanel bottomBar = new GlassPanel(new MigLayout("fill, insets 15 25 15 25", 
            "[][][grow][][]", "[]"));
        
        // PLAYERS indicator
        playerStatusIndicator = new StatusIndicator("Checking...", StatusIndicator.Status.UNKNOWN);
        bottomBar.add(playerStatusIndicator, "gapright 20");
        
        // MOJANG STATUS indicator
        mojangStatusIndicator = new StatusIndicator("Mojang Services", StatusIndicator.Status.UNKNOWN);
        bottomBar.add(mojangStatusIndicator, "gapright 20");
        
        // NEWS section (expandable indicator)
        JLabel newsLabel = new JLabel("NEWS \u25B2");
        newsLabel.setFont(newsLabel.getFont().deriveFont(Font.BOLD, 11f));
        newsLabel.setForeground(TEXT_SECONDARY);
        newsLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newsLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Toggle news panel expansion (simplified - just refresh news)
                WebpagePanel webView = createNewsPanel();
                if (webView != null) {
                    webView.browse(launcher.getNewsURL(), false);
                }
            }
        });
        bottomBar.add(newsLabel, "");
        
        // Server selector and name
        JPanel serverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        serverPanel.setOpaque(false);
        
        // Configure Instance Selector
        DefaultComboBoxModel<Instance> model = new DefaultComboBoxModel<>();
        instanceSelector.setModel(model);
        instanceSelector.setRenderer(new InstanceComboRenderer());
        instanceSelector.setPreferredSize(new Dimension(200, 30));
        instanceSelector.addActionListener(e -> {
            // Sync selection with the invisible table
            int index = instanceSelector.getSelectedIndex();
            if (index >= 0 && index < getInstancesTable().getRowCount()) {
                getInstancesTable().setRowSelectionInterval(index, index);
            }
            updateLaunchButton();
            updateSelectedServerLabel();
        });
        
        // Add listener to auto-refresh the dropdown when instances load
        getInstancesTable().getModel().addTableModelListener(e -> updateInstanceList());
        
        selectedServerLabel = new JLabel("");
        selectedServerLabel.setFont(selectedServerLabel.getFont().deriveFont(Font.PLAIN, 11f));
        selectedServerLabel.setForeground(TEXT_SECONDARY);
        
        serverPanel.add(instanceSelector);
        bottomBar.add(serverPanel, "growx, pushx");
        
        // Large PLAY button
        launchButton.setText("PLAY");
        launchButton.setFont(launchButton.getFont().deriveFont(Font.BOLD, 16f));
        launchButton.setPreferredSize(new Dimension(120, 40));
        stylePillButton(launchButton, SUCCESS_GREEN, Color.WHITE);
        bottomBar.add(launchButton, "");
        
        return bottomBar;
    }
    
    /**
     * Opens a URL in the default browser.
     */
    private void openURL(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Could not open URL: " + url, 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Starts periodic refresh of server status.
     */
    private void startPeriodicRefresh() {
        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                pingServer();
                checkMojangStatus();
            }
        }, INITIAL_REFRESH_DELAY_MS, REFRESH_INTERVAL_MS);
    }
    
    /**
     * Updates the selected server label.
     */
    private void updateSelectedServerLabel() {
        Instance instance = (Instance) instanceSelector.getSelectedItem();
        if (instance != null && selectedServerLabel != null) {
            selectedServerLabel.setText(instance.getTitle());
        }
    }
    
    /**
     * Checks Mojang service status.
     */
    private void checkMojangStatus() {
        launcher.getExecutor().submit(() -> {
            try {
                // Simple check - try to connect to Mojang's session server
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("sessionserver.mojang.com", 443), 3000);
                socket.close();
                
                SwingUtilities.invokeLater(() -> {
                    if (mojangStatusIndicator != null) {
                        mojangStatusIndicator.setStatus(StatusIndicator.Status.ONLINE);
                        mojangStatusIndicator.setText("Mojang Services");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (mojangStatusIndicator != null) {
                        mojangStatusIndicator.setStatus(StatusIndicator.Status.WARNING);
                        mojangStatusIndicator.setText("Mojang Services");
                    }
                });
            }
        });
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
            // Use the fancy configuration dialog
            FancyConfigurationDialog configDialog = 
                new FancyConfigurationDialog(this, launcher);
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
            
            updateSelectedServerLabel();
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
                    if (playerStatusIndicator != null) {
                        playerStatusIndicator.setStatus(StatusIndicator.Status.ONLINE);
                        playerStatusIndicator.setText(online + "/" + max + " Players");
                    }
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (playerStatusIndicator != null) {
                        playerStatusIndicator.setStatus(StatusIndicator.Status.OFFLINE);
                        playerStatusIndicator.setText("Server Offline");
                    }
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
        if (headComponent != null) {
            panel.add(headComponent);
        }

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                AccountSelectDialog.showAccountRequest(FancyLauncherFrame.this, launcher);
                updateAccountInfo();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                panel.setOpaque(true);
                panel.setBackground(new Color(70, 70, 70));
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
        Icon defaultSkinIcon = SwingHelper.createIcon(Launcher.class, "default_skin.png", 40, 40);
        Image headImage = ((ImageIcon) defaultSkinIcon).getImage();

        if (accounts.getSize() > 0) {
            SavedSession session = accounts.getElementAt(0);
            username = session.getUsername();
            if (session.getAvatarImage() != null) {
                headImage = new ImageIcon(session.getAvatarImage()).getImage();
            }
        }

        nameLabel.setText(username);
        if (headComponent != null) {
            headComponent.setImage(headImage);
        }
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
                launchButton.setBackground(PRIMARY_BLUE);
                launchButton.setForeground(TEXT_PRIMARY);
            } else if (instance.isUpdatePending()) {
                launchButton.setText("UPDATE");
                launchButton.setBackground(WARNING_ORANGE);
                launchButton.setForeground(TEXT_PRIMARY);
            } else {
                launchButton.setText("PLAY");
                launchButton.setBackground(SUCCESS_GREEN);
                launchButton.setForeground(TEXT_PRIMARY);
            }
        }
    }
    
    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
        super.dispose();
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