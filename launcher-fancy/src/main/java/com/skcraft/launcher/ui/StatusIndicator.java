/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.ui;

import javax.swing.*;
import java.awt.*;

/**
 * A status indicator component showing a colored dot and text.
 */
public class StatusIndicator extends JPanel {
    
    public enum Status {
        ONLINE(new Color(40, 167, 69)),    // Green
        OFFLINE(new Color(220, 53, 69)),   // Red
        WARNING(new Color(255, 193, 7)),   // Yellow
        UNKNOWN(new Color(108, 117, 125)); // Gray
        
        private final Color color;
        
        Status(Color color) {
            this.color = color;
        }
        
        public Color getColor() {
            return color;
        }
    }
    
    private final JLabel dotLabel;
    private final JLabel textLabel;
    private Status status;
    
    public StatusIndicator(String text, Status status) {
        this.status = status;
        
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);
        
        dotLabel = new JLabel("\u25CF "); // Circle bullet
        dotLabel.setFont(dotLabel.getFont().deriveFont(16f));
        dotLabel.setForeground(status.getColor());
        
        textLabel = new JLabel(text);
        textLabel.setFont(textLabel.getFont().deriveFont(Font.PLAIN, 12f));
        textLabel.setForeground(Color.WHITE);
        
        add(dotLabel);
        add(textLabel);
    }
    
    public void setStatus(Status status) {
        this.status = status;
        dotLabel.setForeground(status.getColor());
        repaint();
    }
    
    public void setText(String text) {
        textLabel.setText(text);
    }
    
    public Status getStatus() {
        return status;
    }
}
