/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A circular button component for social media links and utility icons.
 */
public class SocialLinkButton extends JButton {
    
    private static final Color DEFAULT_BG = new Color(40, 40, 40, 180);
    private static final Color HOVER_BG = new Color(60, 60, 60, 200);
    private static final int BUTTON_SIZE = 40;
    
    private boolean isHovered = false;
    
    public SocialLinkButton(Icon icon) {
        this(icon, null);
    }
    
    public SocialLinkButton(Icon icon, String tooltip) {
        super(icon);
        
        if (tooltip != null) {
            setToolTipText(tooltip);
        }
        
        setPreferredSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
        setMinimumSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
        setMaximumSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
        
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }
        });
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw circular background
        g2.setColor(isHovered ? HOVER_BG : DEFAULT_BG);
        g2.fillOval(0, 0, getWidth(), getHeight());
        
        // Draw icon centered
        Icon icon = getIcon();
        if (icon != null) {
            int x = (getWidth() - icon.getIconWidth()) / 2;
            int y = (getHeight() - icon.getIconHeight()) / 2;
            icon.paintIcon(this, g2, x, y);
        }
        
        g2.dispose();
    }
}
