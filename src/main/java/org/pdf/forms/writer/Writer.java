package org.pdf.forms.writer;

import java.awt.Graphics2D;
import java.awt.Point;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.Icon;

import org.pdf.forms.fonts.FontHandler;
import org.pdf.forms.gui.IMainFrame;
import org.pdf.forms.utils.XMLUtils;
import org.pdf.forms.widgets.CheckBoxWidget;
import org.pdf.forms.widgets.IWidget;
import org.pdf.forms.widgets.RadioButtonWidget;
import org.pdf.forms.widgets.components.PdfCaption;
import org.pdf.forms.writer.PdfDocumentLayout.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.itextpdf.awt.DefaultFontMapper;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseField;
import com.itextpdf.text.pdf.GrayColor;
import com.itextpdf.text.pdf.PdfAction;
import com.itextpdf.text.pdf.PdfBorderDictionary;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfCopyFields;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.RadioCheckField;

public class Writer {

    private final Logger logger = LoggerFactory.getLogger(Writer.class);

    private final Map<Integer, PdfComponentWriter> componentWriterMap = Map.of(
            IWidget.TEXT_FIELD, new PdfTextFieldWriter(),
            IWidget.TEXT, new PdfTextWriter(),
            IWidget.IMAGE, new PdfImageWriter(),
            IWidget.RADIO_BUTTON, new PdfRadioButtonWriter(),
            IWidget.CHECK_BOX, new PdfCheckBoxWriter(),
            IWidget.COMBO_BOX, new PdfComboBoxWriter(),
            IWidget.LIST_BOX, new PdfListBoxWriter(),
            IWidget.BUTTON, new PdfButtonWriter()
    );

    private final Set<String> fontSubstitutions = new HashSet<>();
    private final IMainFrame mainFrame;

    public Writer(final IMainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    public Set<String> getFontSubstitutions() {
        return fontSubstitutions;
    }

    public void write(
            final File fileToWriteTo,
            final Map<Integer, List<IWidget>> widgetsByPageNumber,
            final org.w3c.dom.Document model) {

        final List<Element> pages = XMLUtils.getElementsFromNodeList(model.getElementsByTagName("page"));
        final Optional<String> documentJavaScript = getJavaScript(model);
        final PdfDocumentLayout pdfDocumentLayout = getPdfDocumentLayout(pages);
        final List<PdfDocumentLayout.Page> pdfPages = pdfDocumentLayout.getPdfPages();

        if (pdfPages.isEmpty()) {
            // this is just a plain, hand made document
            final Document document = new Document(getPageSize(pages, 1));
            try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                final PdfWriter writer = PdfWriter.getInstance(document, baos);
                document.open();

                final GlobalPdfWriter globalPdfWriter = new GlobalPdfWriter(writer);

                final List<IWidget> widgetsForFirstPage = widgetsByPageNumber.get(0);
                addWidgets(writer, widgetsForFirstPage, document.getPageSize(), 0, globalPdfWriter);

                if (widgetsForFirstPage.isEmpty()) {
                    writer.setPageEmpty(false);
                }

                for (int pageNumber = 1; pageNumber < pages.size(); pageNumber++) {
                    final int currentPage = pageNumber + 1;
                    document.setPageSize(getPageSize(pages, currentPage));

                    document.newPage();

                    final List<IWidget> widgetForPage = widgetsByPageNumber.get(pageNumber);
                    addWidgets(writer, widgetForPage, document.getPageSize(), currentPage, globalPdfWriter);

                    if (widgetForPage.isEmpty()) {
                        writer.setPageEmpty(false);
                    }
                }

                documentJavaScript.ifPresent(writer::addJavaScript);
            } catch (final IOException | DocumentException e) {
                logger.error("Writing failed!", e);
            } finally {
                document.close();
            }
        } else {
            // we've got pages imported from other PDF's
            try (final FileOutputStream fos = new FileOutputStream(fileToWriteTo)) {
                final PdfReader reader = createPdfReader(pages, pdfDocumentLayout);

                final PdfStamper stamper = new PdfStamper(reader, fos);
                final GlobalPdfWriter globalPdfWriter = new GlobalPdfWriter(stamper);

                for (int pageNumber = 0; pageNumber < pages.size(); pageNumber++) {
                    final Element page = pages.get(pageNumber);

                    final int currentPage = pageNumber + 1;

                    final List<IWidget> widgetList = widgetsByPageNumber.get(pageNumber);
                    if (isPdfPage(page)) {
                        // this page has been imported
                        final AcroFields acroFields = stamper.getAcroFields();
                        acroFields.removeFieldsFromPage(currentPage);

                        //int pageHeight, int cropHeight, int cropX, int cropY
                        addWidgets(stamper.getWriter(), widgetList, reader.getPageSizeWithRotation(currentPage), currentPage, globalPdfWriter);
                    } else {
                        // this is a brand new page
                        stamper.insertPage(currentPage, getPageSize(pages, currentPage));

                        addWidgets(stamper.getWriter(), widgetList, getPageSize(pages, currentPage), currentPage, globalPdfWriter);
                    }
                }

                documentJavaScript.ifPresent(stamper::addJavaScript);

                stamper.close();
            } catch (final IOException | DocumentException e) {
                logger.error("Writing failed!", e);
            }
        }
    }

    private PdfReader createPdfReader(
            final List<Element> pages,
            final PdfDocumentLayout pdfDocumentLayout) throws DocumentException, IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // TODO: use PdfCopy instead
        final PdfCopyFields pdfCopyFields = new PdfCopyFields(baos);

        for (int i = 0; i < pages.size(); i++) {
            final Page page = pdfDocumentLayout.getPage(i + 1);

            if (page.isPdfPage()) {
                final PdfReader pdfReader = new PdfReader(page.getPdfPath());
                final List<Integer> pageNumbers = List.of(page.getPdfPageNumber());

                pdfCopyFields.addDocument(pdfReader, pageNumbers);
            }
        }

        pdfCopyFields.close();

        return new PdfReader(baos.toByteArray());
    }

    private Optional<String> getJavaScript(final org.w3c.dom.Document model) {
        final Element rootElement = model.getDocumentElement();
        final List<Element> elementsFromNodeList = XMLUtils.getElementsFromNodeList(rootElement.getElementsByTagName("javascript"));
        if (elementsFromNodeList.isEmpty()) {
            return Optional.empty();
        }

        final Element javaScriptElement = elementsFromNodeList.get(0);
        final Map<PdfName, String> eventsAndScripts = getEventAndScriptMap(javaScriptElement);
        final Collection<String> values = eventsAndScripts.values();
        if (values.isEmpty()) {
            return Optional.empty();
        }

        final String script = values.iterator().next();
        if (script == null || script.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(script);
    }

    private boolean isPdfPage(final Element page) {
        final Element fileLocationElement = XMLUtils.getPropertyElement(page, "pdffilelocation");
        return fileLocationElement != null;
    }

    private Rectangle getPageSize(
            final List<Element> pages,
            final int currentPage) {
        final Element page = pages.get(currentPage - 1);
        final Element pageDataElement = (Element) page.getElementsByTagName("pagedata").item(0);

        final int width = Integer.parseInt(XMLUtils.getAttributeFromChildElement(pageDataElement, "width"));
        final int height = Integer.parseInt(XMLUtils.getAttributeFromChildElement(pageDataElement, "height"));

        return new Rectangle(width, height);
    }

    private void handleButtonGroups(
            final PdfWriter writer,
            final List<IWidget> widgets,
            final Rectangle pageSize,
            final int currentPage,
            final GlobalPdfWriter globalPdfWriter) throws IOException, DocumentException {

        final Map<String, List<IWidget>> radioButtonGroups = new HashMap<>();
        final Map<String, List<IWidget>> checkBoxGroups = new HashMap<>();

        for (final IWidget widget : widgets) {
            if (widget.getType() == IWidget.RADIO_BUTTON || widget.getType() == IWidget.CHECK_BOX) {
                final int widgetType = widget.getType();

                final Map<String, List<IWidget>> buttonGroup;
                final String groupName;
                if (widgetType == IWidget.RADIO_BUTTON) {
                    final RadioButtonWidget rbw = (RadioButtonWidget) widget;
                    groupName = rbw.getRadioButtonGroupName();
                    buttonGroup = radioButtonGroups;
                } else {
                    final CheckBoxWidget cbw = (CheckBoxWidget) widget;
                    groupName = cbw.getCheckBoxGroupName();
                    buttonGroup = checkBoxGroups;
                }

                final List<IWidget> buttonsInGroup = buttonGroup.computeIfAbsent(groupName, k -> new ArrayList<>());
                buttonsInGroup.add(widget);
            }

        }

        radioButtonGroups.putAll(checkBoxGroups);

        for (final String groupName : radioButtonGroups.keySet()) {
            final List<IWidget> widgetsInGroup = radioButtonGroups.get(groupName);
            final IWidget testWidget = widgetsInGroup.get(0);

            final int type = testWidget.getType();

            final PdfFormField top;

            if (type == IWidget.RADIO_BUTTON) {
                top = PdfFormField.createRadioButton(writer, true);
            } else {
                top = PdfFormField.createCheckBox(writer);
            }

            top.setFieldName(groupName);
            top.setValueAsName(groupName);

            for (final IWidget widget : widgetsInGroup) {
                final Element rootElement = widget.getProperties().getDocumentElement();

                final List<Element> elementsFromNodeList = XMLUtils.getElementsFromNodeList(rootElement.getElementsByTagName("javascript"));

                if (!elementsFromNodeList.isEmpty()) {
                    writeOutCaption(widget, pageSize, currentPage, globalPdfWriter);

                    final AbstractButton value = (AbstractButton) widget.getValueComponent();

                    final java.awt.Rectangle valueBounds;
                    if (type == IWidget.RADIO_BUTTON) {
                        valueBounds = new java.awt.Rectangle(13, 13);
                    } else {
                        java.awt.Rectangle iconBounds = new java.awt.Rectangle(13, 13);
                        final Icon icon = value.getIcon();
                        if (icon != null) {
                            final int iconWidth = icon.getIconWidth();
                            final int iconHeight = icon.getIconHeight();

                            iconBounds = new java.awt.Rectangle(iconWidth, iconHeight);
                        }
                        valueBounds = iconBounds;
                    }

                    final java.awt.Rectangle actualBounds = value.getBounds();
                    final Point actualLocation = widget.getAbsoluteLocationsOfValue();

                    actualLocation.y += (actualBounds.height / 2d) - (valueBounds.height / 2d);

                    valueBounds.setLocation(actualLocation);
                    final Rectangle pdfValueBounds = convertJavaCoordsToPdfCoords(valueBounds, pageSize);

                    final RadioCheckField check = new RadioCheckField(writer, pdfValueBounds, getName(widget), "Yes");
                    check.setChecked(value.isSelected());

                    addBorder(widget, check);

                    final PdfFormField field;
                    if (type == IWidget.RADIO_BUTTON) {
                        check.setCheckType(RadioCheckField.TYPE_CIRCLE);
                        field = check.getRadioField();
                    } else {
                        check.setCheckType(RadioCheckField.TYPE_CROSS);
                        field = check.getCheckField();
                    }

                    top.addKid(field);

                    final Element javaScriptElement = elementsFromNodeList.get(0);
                    final Map<PdfName, String> eventsAndScripts = getEventAndScriptMap(javaScriptElement);
                    addJavaScriptToFormField(eventsAndScripts, field, writer);
                }
            }

            globalPdfWriter.addAnnotation(top, currentPage);
        }
    }

    private void addWidgets(
            final PdfWriter writer,
            final List<IWidget> widgets,
            final Rectangle pageSize,
            final int currentPage,
            final GlobalPdfWriter globalPdfWriter) throws IOException, DocumentException {

        handleButtonGroups(writer, widgets, pageSize, currentPage, globalPdfWriter);

        for (final IWidget widget : widgets) {
            final int type = widget.getWidgetType();

            final Element rootElement = widget.getProperties().getDocumentElement();

            //Font valueFontToUse = getFont(rootElement, "value");

            if (type == IWidget.GROUP) {
                addWidgets(writer, widget.getWidgetsInGroup(), pageSize, currentPage, globalPdfWriter);
            } else {
                final PdfFormField field = componentWriterMap.get(type).write(widget, pageSize, currentPage, writer, rootElement, globalPdfWriter);

                final List<Element> elementsFromNodeList = XMLUtils.getElementsFromNodeList(rootElement.getElementsByTagName("javascript"));
                if (!elementsFromNodeList.isEmpty()) {
                    final Element javaScriptElement = elementsFromNodeList.get(0);
                    final Map<PdfName, String> eventsAndScripts = getEventAndScriptMap(javaScriptElement);
                    addJavaScriptToFormField(eventsAndScripts, field, writer);
                }

                globalPdfWriter.addAnnotation(field, currentPage);
            }
        }
    }

    private String getName(final IWidget widget) {
        String widgetName = widget.getWidgetName();
        if (mainFrame.getWidgetArrays().isWidgetArrayInList(widgetName)) {
            final int arrayNumber = widget.getArrayNumber();
            widgetName += "[" + arrayNumber + "]";
        }

        return widgetName;
    }

    private void addBorder(
            final IWidget widget,
            final BaseField tf) {
        final org.w3c.dom.Document document = widget.getProperties();
        final Element borderProperties = (Element) document.getElementsByTagName("border").item(0);

        final Element border = (Element) borderProperties.getElementsByTagName("borders").item(0);

        final String style = XMLUtils.getAttributeFromChildElement(border, "Border Style");
        final String width = XMLUtils.getAttributeFromChildElement(border, "Border Width");
        final String color = XMLUtils.getAttributeFromChildElement(border, "Border Color");

        switch (style) {
            case "Solid":
                tf.setBorderStyle(PdfBorderDictionary.STYLE_SOLID);
                break;
            case "Dashed":
                tf.setBorderStyle(PdfBorderDictionary.STYLE_DASHED);
                break;
            case "Beveled":
                tf.setBorderStyle(PdfBorderDictionary.STYLE_BEVELED);
                break;
            case "None":
                return;
            default:
                return;
        }

        tf.setBorderColor(new GrayColor(Integer.parseInt(color)));
        tf.setBorderWidth(Integer.parseInt(width));
    }

    private void addJavaScriptToFormField(
            final Map<PdfName, String> eventsAndScripts,
            final PdfFormField formField,
            final PdfWriter writer) {
        for (final PdfName pdfName : eventsAndScripts.keySet()) {
            final String script = eventsAndScripts.getOrDefault(pdfName, "");
            if (!script.isEmpty()) {
                formField.setAdditionalActions(pdfName, PdfAction.javaScript(script, writer));
            }
        }
    }

    private PdfDocumentLayout getPdfDocumentLayout(final List<Element> pages) {
        final PdfDocumentLayout pdfDocumentLayout = new PdfDocumentLayout();

        for (final Element page : pages) {
            final Element fileLocationElement = XMLUtils.getPropertyElement(page, "pdffilelocation");
            if (fileLocationElement == null) { // is a hand made page
                pdfDocumentLayout.addPage(false);
            } else { // its an imported page
                final String fileLocation = fileLocationElement.getAttributeNode("value").getValue();

                final Element pdfPageNumberElement = XMLUtils.getPropertyElement(page, "pdfpagenumber");
                final String pdfPageNumber = pdfPageNumberElement.getAttributeNode("value").getValue();

                pdfDocumentLayout.addPage(true, fileLocation, Integer.parseInt(pdfPageNumber));
            }
        }

        return pdfDocumentLayout;
    }

    private Map<PdfName, String> getEventAndScriptMap(final Element javaScriptElement) {

        final Map<PdfName, String> actionAndScriptMap = new HashMap<>();

        final List<Element> javaScriptProperties = XMLUtils.getElementsFromNodeList(javaScriptElement.getChildNodes());

        for (final Element property : javaScriptProperties) {
            final String event = property.getNodeName();

            PdfName eventToUse = null;
            switch (event) {
                case "mouseEnter":
                    eventToUse = PdfName.E;
                    break;
                case "mouseExit":
                    eventToUse = PdfName.X;
                    break;
                case "change":
                    eventToUse = PdfName.F;
                    break;
                case "mouseUp":
                    eventToUse = PdfName.U;
                    break;
                case "mouseDown":
                    eventToUse = PdfName.D;
                    break;
                case "keystroke":
                    eventToUse = PdfName.K;
                    break;
                default:
                    break;
            }

            final NodeList childNodes = property.getChildNodes();
            if (childNodes.getLength() != 0) {
                final Text textNode = (Text) childNodes.item(0);
                final String nodeValue = textNode.getNodeValue();
                actionAndScriptMap.put(eventToUse, nodeValue);
            }
        }

        return actionAndScriptMap;
    }

    private void writeOutCaption(
            final IWidget widget,
            final Rectangle pageSize,
            final int currentPage,
            final GlobalPdfWriter globalPdfWriter) {

        final PdfCaption caption = widget.getCaptionComponent();
        if (caption == null) {
            return;
        }

        if (widget.isComponentSplit()) {
            final Element captionElement = XMLUtils.getElementsFromNodeList(
                    widget.getProperties().getElementsByTagName("layout")).get(0);

            final String location = XMLUtils.getPropertyElement(captionElement, "Position").getAttributeNode("value").getValue();
            if (location.equals("None")) {
                return;
            }
        }

        final java.awt.Rectangle captionBounds = caption.getBounds();
        captionBounds.setLocation(widget.getAbsoluteLocationsOfCaption());
        final Rectangle pdfCaptionBounds = convertJavaCoordsToPdfCoords(captionBounds, pageSize);

        // write out caption
        final PdfContentByte cb = globalPdfWriter.getContentByte(currentPage);
        cb.saveState();
        cb.concatCTM(1, 0, 0, 1, pdfCaptionBounds.getLeft(), pdfCaptionBounds.getTop() - captionBounds.height);

        final java.awt.Font font = caption.getFont();
        final String fontDirectory = FontHandler.getInstance().getFontDirectory(font);

        DefaultFontMapper mapper = new DefaultFontMapper();

        mapper.insertDirectory(fontDirectory);

        /*
         * we need to make this erroneous call to awtToPdf to see if an exception is thrown, if it is, it is
         * probably because the font cannot be embedded due to licensing restrictions, so substitute with Helvetica
         */
        try {
            mapper.awtToPdf(font);
        } catch (final Exception e) {
            mapper = new DefaultFontMapper();
            fontSubstitutions.add(font.getFontName());
        }

        final Graphics2D g2 = cb.createGraphics(captionBounds.width, captionBounds.height, mapper, true, .95f);

        //Graphics2D g2 = cb.createGraphicsShapes(captionBounds.width, captionBounds.height, true, 0.95f);

        caption.paint(g2);

        g2.dispose();
        cb.restoreState();
    }

    private Rectangle convertJavaCoordsToPdfCoords(
            final java.awt.Rectangle bounds,
            final Rectangle pageSize) {
        final float javaX1 = bounds.x - IMainFrame.INSET;
        final float javaY1 = bounds.y - IMainFrame.INSET;

        final float javaX2 = javaX1 + bounds.width;

        final float pdfY1 = pageSize.getHeight() - javaY1 - bounds.height;

        final float pdfY2 = pdfY1 + bounds.height;

        return new Rectangle(javaX1, pdfY1, javaX2, pdfY2);
    }
}
