/*
* ===========================================
* PDF Forms Designer
* ===========================================
*
* Project Info:  http://pdfformsdesigne.sourceforge.net
* (C) Copyright 2006-2008..
* Lead Developer: Simon Barnett (n6vale@googlemail.com)
*
* This file is part of the PDF Forms Designer
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
* DesignerMouseListener.java
* ---------------
*/
package org.pdf.forms.gui.designer.listeners;

import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.pdf.forms.gui.IMainFrame;
import org.pdf.forms.gui.designer.IDesigner;
import org.pdf.forms.gui.designer.captionchanger.CaptionChanger;
import org.pdf.forms.gui.designer.gui.DesignerSelectionBox;
import org.pdf.forms.widgets.IWidget;
import org.pdf.forms.widgets.utils.WidgetFactory;
import org.pdf.forms.widgets.utils.WidgetSelection;

public class DesignerMouseListener implements MouseListener {

    private final IDesigner designerPanel;

    private final DesignerSelectionBox selectionBox;

    private final CaptionChanger captionChanger;

    private final WidgetSelection widgetSelection;

    public DesignerMouseListener(final IDesigner designerPanel) {
        this.designerPanel = designerPanel;

        this.widgetSelection = designerPanel.getWidgetSelection();
        this.captionChanger = designerPanel.getCaptionChanger();
        this.selectionBox = designerPanel.getSelectionBox();
    }

    /**
     * Method called when the mouse is clicked.  The primary goal of this method is to pick up the location of the
     * click, and if the mouse has been clicked inside a caption box, then display the caption changer
     *
     * @param e
     *         a MouseEvent object passed in by Java
     */
    @Override
    public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {

            final Set<IWidget> selectedWidgets = designerPanel.getSelectedWidgets();

            /* get a flatterned set of widgets, this means if widgets are in a group, then get them out */
            final Set<IWidget> flatternedWidgets = widgetSelection.getFlatternedWidgets(selectedWidgets);

            for (final IWidget widget : flatternedWidgets) {
                if (!widget.allowEditOfCaptionOnClick()) {
                    continue;
                }

                final Rectangle captionBounds = widget.getCaptionComponent().getBounds();
                captionBounds.setLocation(widget.getAbsoluteLocationsOfCaption());

                /* see if the click is inside the caption bounds */
                if (captionBounds.contains(e.getX(), e.getY())) {
                    captionChanger.displayCaptionChanger(widget, designerPanel);
                }
            }
        }
    }

    @Override
    public void mousePressed(final MouseEvent e) {
        designerPanel.grabFocus();

        captionChanger.closeCaptionChanger();

        final int x = e.getX();
        final int y = e.getY();

        /*
         * if the widget is being resized the mouse is not over the
         * the widget so we need to keep the present selectedWidget,
         * otherwise, we can get the current selectedWidget
         */
        if (designerPanel.getResizeType() == DesignerMouseMotionListener.DEFAULT_CURSOR) { // widget not being resized
            final IWidget selectedWidget = designerPanel.getWidgetAt(x, y);

            if (selectedWidget == null) {
                // user has clicked off all widgets, so empty the selected widgets Set
                designerPanel.setSelectedWidgets(new HashSet<>());
            } else {

                /*
                 * if the new selected widget is already selected the we leave it selected
                 * and dont change anything
                 */
                if (!designerPanel.getSelectedWidgets().contains(selectedWidget)) {

                    /*
                     * the user has clicked on a widget that isnt already selected
                     * so we need to create a new set with just the new selected widget
                     */
                    final Set<IWidget> selectedWidgets = new HashSet<>();
                    selectedWidgets.add(selectedWidget);

                    designerPanel.setSelectedWidgets(selectedWidgets);

                    designerPanel.getMainFrame().setPropertiesCompound(selectedWidgets);
                    designerPanel.getMainFrame().setPropertiesToolBar(selectedWidgets);
                }
            }
        }

        final Set<IWidget> selectedWidgets = designerPanel.getSelectedWidgets();

        /*
         * if no widget is selected then set up the selection box, otherwise
         * a widget is either about to be moved or resized, so set up for that
         */
        if (selectedWidgets.isEmpty()) { // set up the selection box
            selectionBox.setCurrentRect(new Rectangle(x, y, 0, 0));
            selectionBox.updateDrawableRect(designerPanel.getWidth(), designerPanel.getHeight());
        } else { // set up the widget to be moved or resized
            final Iterator iter = widgetSelection.getFlatternedWidgets(selectedWidgets).iterator();

            final Rectangle selectionBoxBounds = widgetSelection.getSelectionBoxBounds();

            while (iter.hasNext()) {
                final IWidget widget = (IWidget) iter.next();

                widget.setResizeHeightRatio(widget.getBoxSize().getHeight() / (selectionBoxBounds.getHeight()));
                widget.setResizeWidthRatio(widget.getBoxSize().getWidth() / (selectionBoxBounds.getWidth()));

                widget.setResizeFromTopRatio(
                        (widget.getY() - (selectionBoxBounds.getY() + WidgetSelection.BOX_MARGIN))
                                / selectionBoxBounds.getHeight());

                widget.setResizeFromLeftRatio(
                        (widget.getX() - (selectionBoxBounds.getX() + WidgetSelection.BOX_MARGIN))
                                / selectionBoxBounds.getWidth());

                //                widget.setLastX((int) (widget.getX() * scale - x)); @scale
                //                widget.setLastY((int) (widget.getY() * scale - y)); @scale

                widget.setLastX(widget.getX() - x);
                widget.setLastY(widget.getY() - y);
            }

            widgetSelection.setLastX(x);
            widgetSelection.setLastY(y);
        }

        designerPanel.setProperties(selectedWidgets);
    }

    @Override
    public void mouseReleased(final MouseEvent e) {
        if (designerPanel.getWidgetToAdd() == IWidget.NONE) {
            selectionBox.setSelectedWedgets();
            selectionBox.setCurrentRect(null);

            designerPanel.setResizeType(DesignerMouseMotionListener.DEFAULT_CURSOR);
            designerPanel.setIsResizing(false);
            designerPanel.setIsResizingSplitComponent(false);
            designerPanel.setCurrentlyDraging(false);
        } else {
            final int widgetToAdd = designerPanel.getWidgetToAdd();

            final Rectangle bounds = selectionBox.getCurrentRect();

            final IWidget widget;
            if (widgetToAdd == IWidget.RADIO_BUTTON) {
                final IMainFrame mainFrame = designerPanel.getMainFrame();
                widget = WidgetFactory.createRadioButtonWidget(
                        mainFrame.getFormsDocument().getPage(mainFrame.getCurrentPage()), bounds);
            } else if (widgetToAdd == IWidget.CHECK_BOX) {
                final IMainFrame mainFrame = designerPanel.getMainFrame();
                widget = WidgetFactory.createCheckBoxWidget(
                        mainFrame.getFormsDocument().getPage(mainFrame.getCurrentPage()), bounds);
            } else {
                widget = WidgetFactory.createWidget(widgetToAdd, bounds);
            }
            widget.setX(bounds.x);
            widget.setY(bounds.y);

            final Set<IWidget> set = new HashSet<>();
            set.add(widget);

            designerPanel.setSelectedWidgets(set);
            designerPanel.addWidget(widget);

            designerPanel.setWidgetToAdd(IWidget.NONE);
            designerPanel.resetPaletteButtons();

            selectionBox.setCurrentRect(null);
        }

        designerPanel.repaint();
    }

    @Override
    public void mouseEntered(final MouseEvent e) {
        final int widgetToAdd = designerPanel.getWidgetToAdd();

        if (widgetToAdd != IWidget.NONE) {
            designerPanel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        }
    }

    @Override
    public void mouseExited(final MouseEvent arg0) {

    }
}
