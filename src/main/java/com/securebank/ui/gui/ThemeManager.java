package com.securebank.ui.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: ThemeManager.java
 *
 * Professional UI/UX Design System for the SecureBank Desktop Suite.
 * Color Palette: Deep Slate Navy, Royal Blue, Emerald Green, and Clean Neutrals.
 */
public final class ThemeManager {

    // Color Palette
    public static final Color PRIMARY_NAVY = new Color(15, 23, 42);      // #0F172A
    public static final Color SECONDARY_NAVY = new Color(30, 41, 59);    // #1E293B
    public static final Color ACCENT_BLUE = new Color(37, 99, 235);      // #2563EB
    public static final Color ACCENT_HOVER = new Color(29, 78, 216);     // #1D4ED8
    public static final Color SUCCESS_GREEN = new Color(16, 185, 129);   // #10B981
    public static final Color DANGER_RED = new Color(239, 68, 68);       // #EF4444
    public static final Color WARNING_AMBER = new Color(245, 158, 11);   // #F59E0B
    public static final Color BG_LIGHT = new Color(248, 250, 252);       // #F8FAFC
    public static final Color CARD_BG = Color.WHITE;
    public static final Color TEXT_MAIN = new Color(15, 23, 42);
    public static final Color TEXT_MUTED = new Color(100, 116, 139);     // #64748B
    public static final Color BORDER_COLOR = new Color(226, 232, 240);   // #E2E8F0

    // Typography
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 13);

    private ThemeManager() {
        // Utility class
    }

    public static JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(ACCENT_BLUE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(ACCENT_HOVER);
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(ACCENT_BLUE);
            }
        });
        return btn;
    }

    public static JButton createSuccessButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(SUCCESS_GREEN);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static JButton createDangerButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(DANGER_RED);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setForeground(TEXT_MAIN);
        btn.setBackground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(9, 19, 9, 19)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static JPanel createCardPanel() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        return card;
    }

    public static void styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setRowHeight(32);
        table.setGridColor(BORDER_COLOR);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(new Color(239, 246, 255));
        table.setSelectionForeground(ACCENT_BLUE);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(TEXT_MAIN);
        header.setPreferredSize(new Dimension(0, 36));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFocus, int r, int c) {
                    Component comp = super.getTableCellRendererComponent(t, val, isSel, hasFocus, r, c);
                    if (!isSel) {
                        comp.setBackground(r % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
                    }
                    setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                    return comp;
                }
            });
        }
    }
}
