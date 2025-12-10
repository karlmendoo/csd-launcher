/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.common.base.Supplier;
import com.skcraft.launcher.swing.SwingHelper;
import lombok.extern.java.Log;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.logging.Level;

@Log
public class FancyLauncher {

    public static void main(final String[] args) {
        // Enable anti-aliasing globally before any Swing classes load
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.awt.noerasebackground", "true");

        Launcher.setupLogger();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                /*
                 * Native Splash Screen (Preferred)
                 */
                SplashScreen splash = SplashScreen.getSplashScreen();
                
                // Fallback Manual Splash (If native fails or config is wrong)
                JWindow fallbackSplash = null;
                if (splash == null) {
                    try {
                        // Load image from the path you specified: com/skcraft/launcher/splash.png
                        java.net.URL imgUrl = FancyLauncher.class.getResource("/com/skcraft/launcher/splash.png");
                        if (imgUrl != null) {
                            ImageIcon img = new ImageIcon(imgUrl);
                            fallbackSplash = new JWindow();
                            fallbackSplash.getContentPane().add(new JLabel(img));
                            fallbackSplash.setSize(img.getIconWidth(), img.getIconHeight());
                            fallbackSplash.setLocationRelativeTo(null);
                            fallbackSplash.setBackground(new Color(0, 0, 0, 0)); // Transparent
                            fallbackSplash.setVisible(true);
                        } else {
                            System.out.println("Splash image not found at /com/skcraft/launcher/splash.png");
                        }
                    } catch (Exception ignored) { }
                }

                try {
                    Thread.currentThread().setContextClassLoader(FancyLauncher.class.getClassLoader());
                    UIManager.getLookAndFeelDefaults().put("ClassLoader", FancyLauncher.class.getClassLoader());
                    UIManager.getDefaults().put("SplitPane.border", BorderFactory.createEmptyBorder());
                    JFrame.setDefaultLookAndFeelDecorated(true);
                    JDialog.setDefaultLookAndFeelDecorated(true);

                    // Configure FlatLaf properties for modern styling
                    UIManager.put("Button.arc", 10);
                    UIManager.put("Component.arc", 10);
                    UIManager.put("CheckBox.arc", 5);
                    UIManager.put("ProgressBar.arc", 10);
                    UIManager.put("TextComponent.arc", 8);
                    UIManager.put("ScrollBar.thumbArc", 10);
                    UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
                    
                    // Set FlatDarkLaf as the Look and Feel
                    FlatDarkLaf.setup();
                    
                    setModernFont();

                    Launcher launcher = Launcher.createFromArguments(args);
                    launcher.setMainWindowSupplier(new CustomWindowSupplier(launcher));
                    launcher.showLauncherWindow();

                    // Close Native Splash
                    if (splash != null && splash.isVisible()) {
                        splash.close();
                    }
                    // Close Fallback Splash
                    if (fallbackSplash != null) {
                        fallbackSplash.dispose();
                    }
                } catch (Throwable t) {
                    if (splash != null && splash.isVisible()) splash.close();
                    if (fallbackSplash != null) fallbackSplash.dispose();
                    
                    log.log(Level.WARNING, "Load failure", t);
                    SwingHelper.showErrorDialog(null, "Uh oh! The updater couldn't be opened because a " +
                            "problem was encountered.", "Launcher error", t);
                }
            }
        });
    }
    private static void setModernFont() {
        // Try to set a modern font (Segoe UI on Windows, Inter or San Francisco on other platforms)
        String[] preferredFonts = {"Inter", "Segoe UI", "San Francisco", Font.SANS_SERIF};
        String selectedFont = Font.SANS_SERIF;
        
        // Check which fonts are available
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();
        
        for (String font : preferredFonts) {
            for (String available : availableFonts) {
                if (available.equalsIgnoreCase(font)) {
                    selectedFont = font;
                    break;
                }
            }
            if (!selectedFont.equals(Font.SANS_SERIF)) break;
        }
        
        final FontUIResource controlFont = new FontUIResource(selectedFont, Font.PLAIN, 13);
        final FontUIResource titleFont = new FontUIResource(selectedFont, Font.BOLD, 13);

        // Apply to standard UIManager for all font keys
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource) {
                if (key.toString().toLowerCase().contains("title")) {
                    UIManager.put(key, titleFont);
                } else {
                    UIManager.put(key, controlFont);
                }
            }
        }
    }

    private static class CustomWindowSupplier implements Supplier<Window> {

        private final Launcher launcher;

        private CustomWindowSupplier(Launcher launcher) {
            this.launcher = launcher;
        }

        @Override
        public Window get() {
            return new FancyLauncherFrame(launcher);
        }
    }

}
