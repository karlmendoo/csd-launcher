/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Utility class to generate simple programmatic icons.
 */
public class IconFactory {
    
    private static final int ICON_SIZE = 24;
    private static final Color ICON_COLOR = new Color(255, 255, 255, 200);
    
    /**
     * Creates a settings gear icon.
     */
    public static Icon createSettingsIcon() {
        return createIcon(g2 -> {
            // Draw gear shape
            int cx = ICON_SIZE / 2;
            int cy = ICON_SIZE / 2;
            int outerRadius = ICON_SIZE / 2 - 2;
            int innerRadius = ICON_SIZE / 4;
            
            // Create gear path with 8 teeth
            Path2D gear = new Path2D.Double();
            int teeth = 8;
            for (int i = 0; i < teeth * 2; i++) {
                double angle = Math.PI * 2 * i / (teeth * 2);
                double radius = (i % 2 == 0) ? outerRadius : outerRadius * 0.8;
                double x = cx + Math.cos(angle) * radius;
                double y = cy + Math.sin(angle) * radius;
                if (i == 0) {
                    gear.moveTo(x, y);
                } else {
                    gear.lineTo(x, y);
                }
            }
            gear.closePath();
            
            g2.fill(gear);
            
            // Draw center hole
            g2.setColor(new Color(0, 0, 0, 255));
            g2.fillOval(cx - innerRadius, cy - innerRadius, innerRadius * 2, innerRadius * 2);
        });
    }
    
    /**
     * Creates a Discord icon (simple version).
     */
    public static Icon createDiscordIcon() {
        return createIcon(g2 -> {
            // Simplified Discord logo - rounded rectangle with two circles for eyes
            int padding = 3;
            g2.fillRoundRect(padding, padding + 2, ICON_SIZE - padding * 2, ICON_SIZE - padding * 2 - 4, 8, 8);
            
            // Eyes
            g2.setColor(new Color(0, 0, 0, 255));
            g2.fillOval(7, 10, 4, 4);
            g2.fillOval(13, 10, 4, 4);
        });
    }
    
    /**
     * Creates a Twitter/X icon (simple version).
     */
    public static Icon createTwitterIcon() {
        return createIcon(g2 -> {
            // Simple X shape for Twitter/X
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(6, 6, 18, 18);
            g2.drawLine(18, 6, 6, 18);
        });
    }
    
    /**
     * Creates an Instagram icon (simple version).
     */
    public static Icon createInstagramIcon() {
        return createIcon(g2 -> {
            // Rounded square
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(4, 4, 16, 16, 6, 6);
            
            // Camera circle
            g2.drawOval(8, 8, 8, 8);
            
            // Camera dot
            g2.fillOval(16, 7, 2, 2);
        });
    }
    
    /**
     * Creates a YouTube icon (simple version).
     */
    public static Icon createYouTubeIcon() {
        return createIcon(g2 -> {
            // Rounded rectangle (TV shape)
            g2.fillRoundRect(3, 7, 18, 10, 4, 4);
            
            // Play triangle
            g2.setColor(new Color(0, 0, 0, 255));
            int[] xPoints = {10, 10, 16};
            int[] yPoints = {10, 14, 12};
            g2.fillPolygon(xPoints, yPoints, 3);
        });
    }
    
    /**
     * Creates a globe/web icon.
     */
    public static Icon createWebIcon() {
        return createIcon(g2 -> {
            g2.setStroke(new BasicStroke(2f));
            
            // Circle
            g2.drawOval(4, 4, 16, 16);
            
            // Vertical line
            g2.drawLine(12, 4, 12, 20);
            
            // Horizontal lines
            g2.drawLine(4, 12, 20, 12);
            g2.drawArc(7, 7, 10, 10, 0, 180);
            g2.drawArc(7, 7, 10, 10, 180, 180);
        });
    }
    
    /**
     * Creates a minimize icon (horizontal line).
     */
    public static Icon createMinimizeIcon() {
        return createIcon(g2 -> {
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(6, 12, 18, 12);
        });
    }
    
    /**
     * Creates a maximize icon (square).
     */
    public static Icon createMaximizeIcon() {
        return createIcon(g2 -> {
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(6, 6, 12, 12);
        });
    }
    
    /**
     * Creates a close icon (X).
     */
    public static Icon createCloseIcon() {
        return createIcon(g2 -> {
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(6, 6, 18, 18);
            g2.drawLine(18, 6, 6, 18);
        });
    }
    
    /**
     * Creates a news/document icon.
     */
    public static Icon createNewsIcon() {
        return createIcon(g2 -> {
            // Document outline
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(6, 4, 12, 16);
            
            // Lines
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(8, 8, 16, 8);
            g2.drawLine(8, 11, 16, 11);
            g2.drawLine(8, 14, 13, 14);
        });
    }
    
    /**
     * Creates a server icon (database/stack).
     */
    public static Icon createServerIcon() {
        return createIcon(g2 -> {
            // Stack of disks
            g2.fillRoundRect(6, 5, 12, 4, 3, 3);
            g2.fillRoundRect(6, 10, 12, 4, 3, 3);
            g2.fillRoundRect(6, 15, 12, 4, 3, 3);
        });
    }
    
    private static Icon createIcon(IconPainter painter) {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        g2.setColor(ICON_COLOR);
        painter.paint(g2);
        
        g2.dispose();
        return new ImageIcon(image);
    }
    
    @FunctionalInterface
    private interface IconPainter {
        void paint(Graphics2D g2);
    }
}
