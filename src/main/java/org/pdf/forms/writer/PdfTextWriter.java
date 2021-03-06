package org.pdf.forms.writer;

import java.awt.Graphics2D;
import java.util.HashSet;
import java.util.Set;

import org.pdf.forms.fonts.FontHandler;
import org.pdf.forms.gui.IMainFrame;
import org.pdf.forms.utils.XMLUtils;
import org.pdf.forms.widgets.IWidget;
import org.pdf.forms.widgets.components.PdfCaption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import com.itextpdf.awt.DefaultFontMapper;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfWriter;

public class PdfTextWriter implements PdfComponentWriter {

    private final Logger logger = LoggerFactory.getLogger(PdfTextWriter.class);

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
            final GlobalPdfWriter globalPdfWriter) {
        writeOutCaption(widget, pageSize, currentPage, globalPdfWriter);
        return null;
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
