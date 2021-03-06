/*
* ===========================================
* PDF Forms Designer
* ===========================================
*
* Project Info:  http://pdfformsdesigne.sourceforge.net
* (C) Copyright 2006-2008..
* Lead Developer: Simon Barnett (n6vale@googlemail.com)
*
*  This file is part of the PDF Forms Designer
*
    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


*
* ---------------
* ColorComboBoxEditor.java
* ---------------
*/
package org.pdf.forms.gui.properties.customcomponents.colorcombobox;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.ComboBoxEditor;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.event.EventListenerList;

public class ColorComboBoxEditor implements ComboBoxEditor {

    private final JTextField editor;

    private final transient EventListenerList listenerList = new EventListenerList();

    public ColorComboBoxEditor(
            final Color initialColor,
            final JComboBox<Object> colorBox) {
        editor = new JTextField("");
        editor.setBackground(initialColor);
        editor.setEditable(false);

        editor.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(final MouseEvent e) {
            }

            @Override
            public void mouseEntered(final MouseEvent e) {
            }

            @Override
            public void mouseExited(final MouseEvent e) {
            }

            @Override
            public void mousePressed(final MouseEvent e) {

                if (!colorBox.isPopupVisible()) {
                    colorBox.setPopupVisible(true);
                }

            }

            @Override
            public void mouseReleased(final MouseEvent e) {
            }
        });

    }

    @Override
    public void addActionListener(final ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    @Override
    public Component getEditorComponent() {
        return editor;
    }

    @Override
    public Object getItem() {
        return editor.getBackground();
    }

    @Override
    public void removeActionListener(final ActionListener l) {
        listenerList.remove(ActionListener.class, l);
    }

    @Override
    public void selectAll() {
        // ignore
    }

    @Override
    public void setItem(final Object newValue) {
        if (newValue instanceof Color) {
            final Color color = (Color) newValue;
            editor.setBackground(color);
            editor.setText("");
        } else if (newValue == null) {
            editor.setBackground(null);
            editor.setText("mixed");
        }
    }
}
