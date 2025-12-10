/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher;

import com.skcraft.launcher.dialog.AboutDialog;
import com.skcraft.launcher.dialog.ConsoleFrame;
import com.skcraft.launcher.launch.runtime.JavaRuntime;
import com.skcraft.launcher.launch.runtime.JavaRuntimeFinder;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.DecimalFormat;
import java.util.Dictionary;
import java.util.Hashtable;

/**
 * Modern full-screen settings dialog matching Helios Launcher UI style.
 */
public class FancyConfigurationDialog extends JDialog {

    private final Launcher launcher;
    private final Configuration config;
    
    // Layout
    private final JPanel navigationPanel = new JPanel();
    private final JPanel contentPanel = new JPanel();
    private final CardLayout cardLayout = new CardLayout();
    
    // Navigation buttons
    private JButton accountButton;
    private JButton minecraftButton;
    private JButton modsButton;
    private JButton javaButton;
    private JButton launcherButton;
    private JButton aboutButton;
    private JButton updatesButton;
    private JButton doneButton;
    
    // Java settings components
    private JSlider minMemorySlider;
    private JSlider maxMemorySlider;
    private JLabel minMemoryLabel;
    private JLabel maxMemoryLabel;
    private JLabel totalMemoryLabel;
    private JLabel availableMemoryLabel;
    private JTextField jvmArgsField;
    private JLabel javaVersionLabel;
    private JLabel javaPathLabel;
    private JButton chooseJavaButton;
    private JavaRuntime selectedRuntime;
    
    // Minecraft settings
    private JSpinner widthSpinner;
    private JSpinner heightSpinner;
    
    // Colors matching FancyLauncherFrame
    private static final Color GLASS_COLOR = new Color(20, 20, 20, (int)(0.85 * 255));
    private static final Color HOVER_COLOR = new Color(255, 255, 255, 20);
    private static final Color SELECTED_COLOR = new Color(0, 120, 212, 100);
    private static final Color PRIMARY_BLUE = new Color(0, 120, 212);
    private static final Color SUCCESS_GREEN = new Color(40, 167, 69);
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    private static final Color TEXT_SECONDARY = new Color(176, 176, 176);
    private static final Color BORDER_SUBTLE = new Color(255, 255, 255, (int)(0.1 * 255));
    private static final Color PANEL_BG = new Color(30, 30, 30);
    
    private String currentPanel = "java";

    public FancyConfigurationDialog(Window owner, @NonNull Launcher launcher) {
        super(owner, "Settings", ModalityType.DOCUMENT_MODAL);
        
        this.launcher = launcher;
        this.config = launcher.getConfig();
        this.selectedRuntime = config.getJavaRuntime();
        
        setLayout(new BorderLayout());
        setSize(900, 600);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        initComponents();
        loadSettings();
    }
    
    private void initComponents() {
        // Main container with navigation and content
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(PANEL_BG);
        
        // Left navigation panel
        navigationPanel.setLayout(new BoxLayout(navigationPanel, BoxLayout.Y_AXIS));
        navigationPanel.setBackground(new Color(25, 25, 25));
        navigationPanel.setPreferredSize(new Dimension(200, 600));
        navigationPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_SUBTLE));
        
        // Add navigation buttons
        accountButton = createNavButton("Account", "account");
        minecraftButton = createNavButton("Minecraft", "minecraft");
        modsButton = createNavButton("Mods", "mods");
        javaButton = createNavButton("Java", "java");
        launcherButton = createNavButton("Launcher", "launcher");
        aboutButton = createNavButton("About", "about");
        updatesButton = createNavButton("Updates", "updates");
        
        navigationPanel.add(Box.createVerticalStrut(20));
        navigationPanel.add(accountButton);
        navigationPanel.add(minecraftButton);
        navigationPanel.add(modsButton);
        navigationPanel.add(javaButton);
        navigationPanel.add(launcherButton);
        navigationPanel.add(aboutButton);
        navigationPanel.add(updatesButton);
        navigationPanel.add(Box.createVerticalGlue());
        
        // Done button at bottom
        doneButton = createNavButton("Done", null);
        doneButton.setBackground(SUCCESS_GREEN);
        doneButton.addActionListener(e -> save());
        navigationPanel.add(doneButton);
        navigationPanel.add(Box.createVerticalStrut(20));
        
        // Right content panel
        contentPanel.setLayout(cardLayout);
        contentPanel.setBackground(PANEL_BG);
        
        // Create panels for each section
        contentPanel.add(createAccountPanel(), "account");
        contentPanel.add(createMinecraftPanel(), "minecraft");
        contentPanel.add(createModsPanel(), "mods");
        contentPanel.add(createJavaPanel(), "java");
        contentPanel.add(createLauncherPanel(), "launcher");
        contentPanel.add(createAboutPanel(), "about");
        contentPanel.add(createUpdatesPanel(), "updates");
        
        // Show Java panel by default
        cardLayout.show(contentPanel, "java");
        javaButton.setBackground(SELECTED_COLOR);
        
        mainPanel.add(navigationPanel, BorderLayout.WEST);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        
        add(mainPanel);
    }
    
    private JButton createNavButton(String text, String panelId) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(200, 40));
        button.setPreferredSize(new Dimension(200, 40));
        button.setForeground(TEXT_PRIMARY);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        
        if (panelId != null) {
            button.addActionListener(e -> switchPanel(panelId, button));
            
            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!currentPanel.equals(panelId)) {
                        button.setBackground(HOVER_COLOR);
                        button.setContentAreaFilled(true);
                    }
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    if (!currentPanel.equals(panelId)) {
                        button.setContentAreaFilled(false);
                    }
                }
            });
        }
        
        return button;
    }
    
    private void switchPanel(String panelId, JButton button) {
        // Reset all buttons
        for (Component c : navigationPanel.getComponents()) {
            if (c instanceof JButton && c != doneButton) {
                c.setBackground(null);
                ((JButton) c).setContentAreaFilled(false);
            }
        }
        
        // Highlight selected
        button.setBackground(SELECTED_COLOR);
        button.setContentAreaFilled(true);
        
        currentPanel = panelId;
        cardLayout.show(contentPanel, panelId);
    }
    
    private JPanel createJavaPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 40", "[grow]", "[][][][][][]"));
        panel.setBackground(PANEL_BG);
        
        // Title
        JLabel title = new JLabel("Java Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(TEXT_PRIMARY);
        panel.add(title, "wrap, gapbottom 20");
        
        // Memory section
        JPanel memorySection = createSection("Memory Allocation");
        memorySection.setLayout(new MigLayout("fill, insets 20", "[grow]", "[][][][]"));
        
        // Get system memory
        long totalMemoryBytes = Runtime.getRuntime().maxMemory();
        long totalMemoryMB = totalMemoryBytes / (1024 * 1024);
        double totalMemoryGB = totalMemoryMB / 1024.0;
        
        // Memory info labels
        totalMemoryLabel = new JLabel(String.format("Total: %.1fG", totalMemoryGB));
        totalMemoryLabel.setForeground(TEXT_SECONDARY);
        availableMemoryLabel = new JLabel(String.format("Available: %.1fG", totalMemoryGB * 0.75)); // Approximate
        availableMemoryLabel.setForeground(TEXT_SECONDARY);
        
        JPanel memoryInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        memoryInfoPanel.setOpaque(false);
        memoryInfoPanel.add(totalMemoryLabel);
        memoryInfoPanel.add(availableMemoryLabel);
        
        memorySection.add(memoryInfoPanel, "wrap, align right");
        
        // Minimum RAM slider
        JPanel minMemPanel = new JPanel(new MigLayout("fill, insets 0", "[][grow][]", "[]"));
        minMemPanel.setOpaque(false);
        
        JLabel minLabel = new JLabel("Minimum RAM");
        minLabel.setForeground(TEXT_PRIMARY);
        minLabel.setFont(minLabel.getFont().deriveFont(Font.BOLD));
        
        minMemorySlider = createMemorySlider();
        minMemoryLabel = new JLabel("3.0G");
        minMemoryLabel.setForeground(TEXT_PRIMARY);
        minMemoryLabel.setFont(minMemoryLabel.getFont().deriveFont(Font.BOLD, 16f));
        
        minMemorySlider.addChangeListener(e -> {
            int value = minMemorySlider.getValue();
            minMemoryLabel.setText(formatMemory(value));
            
            // Ensure max is at least min
            if (maxMemorySlider.getValue() < value) {
                maxMemorySlider.setValue(value);
            }
        });
        
        minMemPanel.add(minLabel, "w 120!");
        minMemPanel.add(minMemorySlider, "growx");
        minMemPanel.add(minMemoryLabel, "w 80!");
        
        memorySection.add(minMemPanel, "wrap, growx, gapbottom 10");
        
        // Maximum RAM slider
        JPanel maxMemPanel = new JPanel(new MigLayout("fill, insets 0", "[][grow][]", "[]"));
        maxMemPanel.setOpaque(false);
        
        JLabel maxLabel = new JLabel("Maximum RAM");
        maxLabel.setForeground(TEXT_PRIMARY);
        maxLabel.setFont(maxLabel.getFont().deriveFont(Font.BOLD));
        
        maxMemorySlider = createMemorySlider();
        maxMemoryLabel = new JLabel("4.0G");
        maxMemoryLabel.setForeground(TEXT_PRIMARY);
        maxMemoryLabel.setFont(maxMemoryLabel.getFont().deriveFont(Font.BOLD, 16f));
        
        maxMemorySlider.addChangeListener(e -> {
            int value = maxMemorySlider.getValue();
            maxMemoryLabel.setText(formatMemory(value));
            
            // Ensure min is at most max
            if (minMemorySlider.getValue() > value) {
                minMemorySlider.setValue(value);
            }
        });
        
        maxMemPanel.add(maxLabel, "w 120!");
        maxMemPanel.add(maxMemorySlider, "growx");
        maxMemPanel.add(maxMemoryLabel, "w 80!");
        
        memorySection.add(maxMemPanel, "wrap, growx, gapbottom 15");
        
        // Helper text
        JLabel helpText = new JLabel("<html>The recommended minimum RAM is 3 gigabytes. Setting the minimum and maximum values to the same value may reduce lag.</html>");
        helpText.setForeground(TEXT_SECONDARY);
        helpText.setFont(helpText.getFont().deriveFont(12f));
        memorySection.add(helpText, "wrap, growx");
        
        panel.add(memorySection, "wrap, growx, gapbottom 20");
        
        // Java Executable section
        JPanel javaSection = createSection("Java Executable");
        javaSection.setLayout(new MigLayout("fill, insets 20", "[grow]", "[][][]"));
        
        javaVersionLabel = new JLabel("Selected: Java 8 Update 202 (x64)");
        javaVersionLabel.setForeground(TEXT_PRIMARY);
        javaVersionLabel.setFont(javaVersionLabel.getFont().deriveFont(Font.BOLD, 14f));
        javaSection.add(javaVersionLabel, "wrap, gapbottom 10");
        
        // Java path display
        JPanel pathPanel = new JPanel(new MigLayout("fill, insets 5", "[][grow]", "[]"));
        pathPanel.setBackground(new Color(40, 40, 40));
        pathPanel.setBorder(BorderFactory.createLineBorder(BORDER_SUBTLE, 1));
        
        JLabel javaIcon = new JLabel("☕");
        javaIcon.setFont(javaIcon.getFont().deriveFont(20f));
        
        javaPathLabel = new JLabel("/path/to/java");
        javaPathLabel.setForeground(TEXT_SECONDARY);
        javaPathLabel.setFont(javaPathLabel.getFont().deriveFont(12f));
        
        pathPanel.add(javaIcon);
        pathPanel.add(javaPathLabel, "growx");
        
        javaSection.add(pathPanel, "wrap, growx, gapbottom 10");
        
        // Choose file button
        chooseJavaButton = createStyledButton("Choose File", PRIMARY_BLUE);
        chooseJavaButton.addActionListener(e -> chooseJavaExecutable());
        javaSection.add(chooseJavaButton, "align left");
        
        panel.add(javaSection, "wrap, growx, gapbottom 20");
        
        // JVM Options section
        JPanel jvmSection = createSection("Additional JVM Options");
        jvmSection.setLayout(new MigLayout("fill, insets 20", "[grow]", "[][]"));
        
        JLabel jvmLabel = new JLabel("JVM Arguments");
        jvmLabel.setForeground(TEXT_PRIMARY);
        jvmLabel.setFont(jvmLabel.getFont().deriveFont(Font.BOLD));
        jvmSection.add(jvmLabel, "wrap, gapbottom 5");
        
        jvmArgsField = new JTextField();
        jvmArgsField.setBackground(new Color(40, 40, 40));
        jvmArgsField.setForeground(TEXT_PRIMARY);
        jvmArgsField.setCaretColor(TEXT_PRIMARY);
        jvmArgsField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        jvmArgsField.setFont(jvmArgsField.getFont().deriveFont(12f));
        jvmSection.add(jvmArgsField, "growx");
        
        panel.add(jvmSection, "wrap, growx");
        
        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(PANEL_BG);
        scrollPane.getViewport().setBackground(PANEL_BG);
        
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(PANEL_BG);
        wrapper.add(scrollPane);
        
        return wrapper;
    }
    
    private JSlider createMemorySlider() {
        JSlider slider = new JSlider(512, 16384, 4096); // 0.5GB to 16GB, default 4GB
        slider.setMajorTickSpacing(2048);
        slider.setMinorTickSpacing(512);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setOpaque(false);
        slider.setForeground(TEXT_PRIMARY);
        
        // Custom UI for modern look
        slider.setUI(new BasicSliderUI(slider) {
            @Override
            public void paintTrack(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int trackHeight = 6;
                int trackTop = (trackRect.height - trackHeight) / 2;
                
                // Track background
                g2.setColor(new Color(60, 60, 60));
                g2.fillRoundRect(trackRect.x, trackRect.y + trackTop, trackRect.width, trackHeight, trackHeight, trackHeight);
                
                // Filled portion
                int fillWidth = (int) ((slider.getValue() - slider.getMinimum()) / (double) (slider.getMaximum() - slider.getMinimum()) * trackRect.width);
                g2.setColor(PRIMARY_BLUE);
                g2.fillRoundRect(trackRect.x, trackRect.y + trackTop, fillWidth, trackHeight, trackHeight, trackHeight);
                
                g2.dispose();
            }
            
            @Override
            public void paintThumb(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int thumbSize = 18;
                int x = thumbRect.x + (thumbRect.width - thumbSize) / 2;
                int y = thumbRect.y + (thumbRect.height - thumbSize) / 2;
                
                // Shadow
                g2.setColor(new Color(0, 0, 0, 50));
                g2.fillOval(x + 1, y + 2, thumbSize, thumbSize);
                
                // Thumb
                g2.setColor(TEXT_PRIMARY);
                g2.fillOval(x, y, thumbSize, thumbSize);
                
                g2.dispose();
            }
        });
        
        return slider;
    }
    
    private String formatMemory(int mb) {
        DecimalFormat df = new DecimalFormat("0.0");
        return df.format(mb / 1024.0) + "G";
    }
    
    private JPanel createSection(String title) {
        JPanel section = new JPanel();
        section.setBackground(new Color(35, 35, 35));
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        
        return section;
    }
    
    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setForeground(TEXT_PRIMARY);
        button.setBackground(bgColor);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(120, 32));
        
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                AbstractButton b = (AbstractButton) c;
                int arc = 20;
                
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
        
        return button;
    }
    
    private void chooseJavaExecutable() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || (f.getName().startsWith("java") && f.canExecute());
            }
            
            @Override
            public String getDescription() {
                return "Java runtime executables";
            }
        });
        chooser.setDialogTitle("Choose a Java executable");
        
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedRuntime = JavaRuntimeFinder.getRuntimeFromPath(chooser.getSelectedFile().getAbsolutePath());
            updateJavaInfo();
        }
    }
    
    private void updateJavaInfo() {
        if (selectedRuntime != null) {
            String version = selectedRuntime.getVersion() != null ? selectedRuntime.getVersion() : "unknown";
            String arch = selectedRuntime.is64Bit() ? "x64" : "x86";
            javaVersionLabel.setText("Selected: Java " + version + " (" + arch + ")");
            javaPathLabel.setText(selectedRuntime.getDir().getAbsolutePath());
        } else {
            javaVersionLabel.setText("Selected: System Default");
            javaPathLabel.setText("No custom Java selected");
        }
    }
    
    private JPanel createAccountPanel() {
        return createPlaceholderPanel("Account", "Account settings will be managed through the main launcher interface.");
    }
    
    private JPanel createMinecraftPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 40", "[grow]", "[][]"));
        panel.setBackground(PANEL_BG);
        
        // Title
        JLabel title = new JLabel("Minecraft Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(TEXT_PRIMARY);
        panel.add(title, "wrap, gapbottom 20");
        
        // Window size section
        JPanel windowSection = createSection("Game Window");
        windowSection.setLayout(new MigLayout("fill, insets 20", "[][grow]", "[][]"));
        
        JLabel widthLabel = new JLabel("Window Width");
        widthLabel.setForeground(TEXT_PRIMARY);
        widthLabel.setFont(widthLabel.getFont().deriveFont(Font.BOLD));
        
        widthSpinner = new JSpinner(new SpinnerNumberModel(854, 100, 3840, 1));
        styleSpinner(widthSpinner);
        
        JLabel heightLabel = new JLabel("Window Height");
        heightLabel.setForeground(TEXT_PRIMARY);
        heightLabel.setFont(heightLabel.getFont().deriveFont(Font.BOLD));
        
        heightSpinner = new JSpinner(new SpinnerNumberModel(480, 100, 2160, 1));
        styleSpinner(heightSpinner);
        
        windowSection.add(widthLabel);
        windowSection.add(widthSpinner, "growx, wrap, gapbottom 10");
        windowSection.add(heightLabel);
        windowSection.add(heightSpinner, "growx");
        
        panel.add(windowSection, "wrap, growx");
        
        return panel;
    }
    
    private void styleSpinner(JSpinner spinner) {
        spinner.setFont(spinner.getFont().deriveFont(14f));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JSpinner.DefaultEditor defaultEditor = (JSpinner.DefaultEditor) editor;
            defaultEditor.getTextField().setBackground(new Color(40, 40, 40));
            defaultEditor.getTextField().setForeground(TEXT_PRIMARY);
            defaultEditor.getTextField().setCaretColor(TEXT_PRIMARY);
        }
    }
    
    private JPanel createModsPanel() {
        return createPlaceholderPanel("Mods", "Mod management is handled per-instance.");
    }
    
    private JPanel createLauncherPanel() {
        return createPlaceholderPanel("Launcher", "Additional launcher settings will be added here.");
    }
    
    private JPanel createAboutPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 40", "[grow]", "[][][]"));
        panel.setBackground(PANEL_BG);
        
        // Title
        JLabel title = new JLabel("About");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(TEXT_PRIMARY);
        panel.add(title, "wrap, gapbottom 20");
        
        // About section
        JPanel aboutSection = createSection("Launcher Information");
        aboutSection.setLayout(new MigLayout("fill, insets 20", "[grow]", "[][]"));
        
        JLabel infoLabel = new JLabel("<html>SKCraft Launcher<br>Fancy Edition</html>");
        infoLabel.setForeground(TEXT_PRIMARY);
        infoLabel.setFont(infoLabel.getFont().deriveFont(14f));
        aboutSection.add(infoLabel, "wrap");
        
        JButton aboutDialogButton = createStyledButton("More Info", PRIMARY_BLUE);
        aboutDialogButton.addActionListener(e -> AboutDialog.showAboutDialog(this));
        aboutSection.add(aboutDialogButton, "align left");
        
        panel.add(aboutSection, "wrap, growx, gapbottom 20");
        
        // Console button
        JButton consoleButton = createStyledButton("Open Console", new Color(60, 60, 60));
        consoleButton.addActionListener(e -> ConsoleFrame.showMessages());
        panel.add(consoleButton, "align left");
        
        return panel;
    }
    
    private JPanel createUpdatesPanel() {
        return createPlaceholderPanel("Updates", "Update settings and checks will be available here.");
    }
    
    private JPanel createPlaceholderPanel(String title, String message) {
        JPanel panel = new JPanel(new MigLayout("fill, insets 40", "[grow]", "[][]"));
        panel.setBackground(PANEL_BG);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(TEXT_PRIMARY);
        panel.add(titleLabel, "wrap, gapbottom 20");
        
        JLabel messageLabel = new JLabel(message);
        messageLabel.setForeground(TEXT_SECONDARY);
        messageLabel.setFont(messageLabel.getFont().deriveFont(14f));
        panel.add(messageLabel, "wrap");
        
        return panel;
    }
    
    private void loadSettings() {
        // Load memory settings
        int minMem = config.getMinMemory();
        int maxMem = config.getMaxMemory();
        
        // Set default max memory if not set
        if (maxMem == 0) {
            maxMem = 4096; // 4GB default
        }
        
        minMemorySlider.setValue(minMem);
        maxMemorySlider.setValue(maxMem);
        minMemoryLabel.setText(formatMemory(minMem));
        maxMemoryLabel.setText(formatMemory(maxMem));
        
        // Load JVM args
        if (config.getJvmArgs() != null) {
            jvmArgsField.setText(config.getJvmArgs());
        }
        
        // Load Java runtime
        updateJavaInfo();
        
        // Load Minecraft settings
        widthSpinner.setValue(config.getWindowWidth());
        heightSpinner.setValue(config.getWindowHeight());
    }
    
    private void save() {
        // Save memory settings
        config.setMinMemory(minMemorySlider.getValue());
        config.setMaxMemory(maxMemorySlider.getValue());
        
        // Save JVM args
        config.setJvmArgs(jvmArgsField.getText());
        
        // Save Java runtime
        config.setJavaRuntime(selectedRuntime);
        
        // Save Minecraft settings
        config.setWindowWidth((Integer) widthSpinner.getValue());
        config.setWindowHeight((Integer) heightSpinner.getValue());
        
        // Persist configuration
        Persistence.commitAndForget(config);
        
        dispose();
    }
}
