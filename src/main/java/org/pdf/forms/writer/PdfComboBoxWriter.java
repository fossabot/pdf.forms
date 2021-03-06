package org.pdf.forms.writer;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.pdf.forms.fonts.FontHandler;
import org.pdf.forms.gui.IMainFrame;
import org.pdf.forms.utils.XMLUtils;
import org.pdf.forms.widgets.IWidget;
import org.pdf.forms.widgets.components.PdfCaption;
import org.pdf.forms.widgets.components.PdfComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import com.itextpdf.awt.DefaultFontMapper;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseField;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.GrayColor;
import com.itextpdf.text.pdf.PdfBorderDictionary;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.TextField;

public class PdfComboBoxWriter implements PdfComponentWriter {

    private Logger logger = LoggerFactory.getLogger(PdfComboBoxWriter.class);

    private final Set<String> fontSubstitutions = new HashSet<>();

    @Override
    public Set<String> getFontSubstitutions() {
        return Set.copyOf(fontSubstitutions);
    }

    @Override
    public PdfFormField write(
            final IWidget widget,
            final Rectangle pageSize,
            final int currentPage,
            final PdfWriter writer,
            final Element rootElement,
            final GlobalPdfWriter globalPdfWriter) throws IOException, DocumentException {
        //PdfCaption comboBoxCaption = widget.getCaptionComponent();
        writeOutCaption(widget, pageSize, currentPage, globalPdfWriter);

        final PdfComboBox value = (PdfComboBox) widget.getValueComponent();
        final java.awt.Rectangle valueBounds = value.getBounds();
        valueBounds.setLocation(widget.getAbsoluteLocationsOfValue());

        final Rectangle pdfValueBounds = convertJavaCoordsToPdfCoords(valueBounds, pageSize);

        final Element itemsElement = (Element) rootElement.getElementsByTagName("items").item(0);

        final List list = XMLUtils.getElementsFromNodeList(itemsElement.getChildNodes());

        final String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            final Element item = (Element) list.get(i);
            items[i] = XMLUtils.getAttributeFromElement(item, "item");
        }

        final Font font = value.getFont();

        final BaseFont baseFont = getBaseFont(font);

        final TextField combo = new TextField(writer, pdfValueBounds, widget.getWidgetName());
        combo.setFont(baseFont);
        combo.setFontSize(value.getFont().getSize());
        combo.setTextColor(getBaseColor(widget.getValueComponent().getForeground()));
        combo.setChoices(items);

        final Element editableElement = XMLUtils.getPropertyElement(rootElement, "Allow Custom Text Entry");
        final boolean editable = Boolean.valueOf(editableElement.getAttributes().getNamedItem("value").getNodeValue());
        if (editable) {
            combo.setOptions(BaseField.EDIT);
        }

        final Element defaultElement = XMLUtils.getPropertyElement(rootElement, "Default");
        String defaultText = defaultElement.getAttributes().getNamedItem("value").getNodeValue();
        if (defaultText.equals("< None >")) {
            defaultText = "";
        }

        addBorder(widget, combo);

        final PdfFormField field = combo.getComboField();
        field.setValueAsString(defaultText);
        return field;
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
            logger.error("Failed converting font from AWT to PDF for " + font.getName() + "!", e);
            mapper = new DefaultFontMapper();
            fontSubstitutions.add(font.getFontName());
        }

        final Graphics2D g2 = cb.createGraphics(captionBounds.width, captionBounds.height, mapper, true, .95f);

        //Graphics2D g2 = cb.createGraphicsShapes(captionBounds.width, captionBounds.height, true, 0.95f);

        caption.paint(g2);

        g2.dispose();
        cb.restoreState();
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

    private BaseFont getBaseFont(final Font font) throws IOException, DocumentException {
        final String fontPath = FontHandler.getInstance().getAbsoluteFontPath(font);
        try {
            return BaseFont.createFont(fontPath, BaseFont.CP1250, BaseFont.EMBEDDED);
        } catch (final DocumentException e) {
            logger.error("Failed creating font from path " + fontPath + "!", e);

            /*
             * A document exception has been thrown meaning that the font cannot be embedded
             * due to licensing restrictions so substitute with Helvetica
             */
            fontSubstitutions.add(font.getFontName());
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.EMBEDDED);
        }
    }

    private BaseColor getBaseColor(final Color color) {
        return new GrayColor(color.getRGB());
    }
}
