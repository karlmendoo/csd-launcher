/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * A component that displays an image in a circular shape with optional border.
 */
public class CircularImageComponent extends JComponent {
    
    private Image image;
    private Color borderColor;
    private int borderWidth;
    
    public CircularImageComponent(Image image, int size) {
        this(image, size, null, 0);
    }
    
    public CircularImageComponent(Image image, int size, Color borderColor, int borderWidth) {
        this.image = image;
        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }
    
    public void setImage(Image image) {
        this.image = image;
        repaint();
    }
    
    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        repaint();
    }
    
    public void setBorderWidth(int borderWidth) {
        this.borderWidth = borderWidth;
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (image == null) {
            return;
        }
        
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        int size = Math.min(getWidth(), getHeight());
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        
        // Create circular image
        BufferedImage circularImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = circularImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        // Clip to circle
        g2d.setClip(new Ellipse2D.Float(0, 0, size, size));
        g2d.drawImage(image, 0, 0, size, size, null);
        g2d.dispose();
        
        // Draw the circular image
        g2.drawImage(circularImage, x, y, null);
        
        // Draw border if specified
        if (borderColor != null && borderWidth > 0) {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderWidth));
            g2.drawOval(x + borderWidth / 2, y + borderWidth / 2, 
                       size - borderWidth, size - borderWidth);
        }
        
        g2.dispose();
    }
}
