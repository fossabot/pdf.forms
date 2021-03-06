package org.pdf.forms.gui.commands;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.pdf.forms.gui.IMainFrame;
import org.pdf.forms.gui.designer.IDesigner;
import org.pdf.forms.widgets.IWidget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class UnGroupElementsCommand implements Command {

    private final Logger logger = LoggerFactory.getLogger(UnGroupElementsCommand.class);
    private final IMainFrame mainFrame;

    UnGroupElementsCommand(final IMainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    @Override
    public void execute() {
        ungroup();
    }

    private void ungroup() {
        final IDesigner designerPanel = mainFrame.getDesigner();

        final Set<IWidget> selectedWidgets = designerPanel.getSelectedWidgets();

        final IWidget gw = selectedWidgets.iterator().next();

        designerPanel.removeSelectedWidgets();

        final List<IWidget> widgetsInGroup = gw.getWidgetsInGroup();
        for (final IWidget widget : widgetsInGroup) {
            designerPanel.addWidget(widget);
        }

        final Set<IWidget> widgets = new HashSet<>(widgetsInGroup);

        designerPanel.setSelectedWidgets(widgets);

        designerPanel.getMainFrame().setPropertiesCompound(widgets);
        designerPanel.getMainFrame().setPropertiesToolBar(widgets);

        designerPanel.repaint();
    }

}
