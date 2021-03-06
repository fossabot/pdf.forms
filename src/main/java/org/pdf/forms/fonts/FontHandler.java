/*
 * ===========================================
 * PDF Forms Designer
 * ===========================================
 * <p>
 * Project Info:  http://pdfformsdesigne.sourceforge.net
 * (C) Copyright 2006-2008..
 * Lead Developer: Simon Barnett (n6vale@googlemail.com)
 * <p>
 * This file is part of the PDF Forms Designer
 * <p>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * <p>
 * <p>
 * <p>
 * ---------------
 * FontHandler.java
 * ---------------
 */
package org.pdf.forms.fonts;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.pdf.forms.utils.DesignerPropertiesFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public final class FontHandler {

    private static FontHandler instance;

    private static final Map<Font, String> FONT_FILE_MAP = new TreeMap<>((o1, o2) -> {
        final String font1 = o1.getFontName();
        final String font2 = o2.getFontName();

        return font1.compareToIgnoreCase(font2);
    });

    private final Logger logger = LoggerFactory.getLogger(FontHandler.class);

    private FontHandler() {
        final String javaFontDir = System.getProperty("java.home") + "/lib/fonts";
        final String[] fontDirectoriesWindows = {
                "c:/windows/fonts",
                "c:/winnt/fonts",
                "d:/windows/fonts",
                "d:/winnt/fonts"
        };
        final String[] fontDirectoriesUnix = {
                "/usr/X/lib/X11/fonts/TrueType",
                "/usr/openwin/lib/X11/fonts/TrueType",
                "/usr/share/fonts/default/TrueType",
                "/usr/X11R6/lib/X11/fonts/ttf",
                "/Library/Fonts",
                "/System/Library/Fonts"
        };
        final List<String> fontDirectories = ImmutableList.<String>builder()
                .addAll(Arrays.asList(fontDirectoriesWindows))
                .addAll(Arrays.asList(fontDirectoriesUnix))
                .add(javaFontDir)
                .build();

        fontDirectories.forEach(this::registerDirectory);

        //TODO need to check if file has moved, and if so offer user chance to browse
        final File configDir = new File(System.getProperty("user.dir"));
        DesignerPropertiesFile.getInstance(configDir).getCustomFonts().forEach((key, value) -> registerFont(new File(value)));
    }

    private void registerDirectory(final String fontDirectory) {
        try {
            final File folder = new File(fontDirectory);
            if (!folder.exists() || !folder.isDirectory()) {
                return;
            }

            final File[] fontFiles = folder.listFiles((directory, fileName) -> fileName.toLowerCase().endsWith(".ttf"));
            if (fontFiles == null) {
                return;
            }

            Arrays.stream(fontFiles).forEach(this::registerFont);
        } catch (final Exception e) {
            logger.info("Error registering directory", e);
        }
    }

    /**
     * Any method that relies on it what happened.
     */
    String registerFont(final File file) {
        // TODO adapt this method to handle a duff file, behave nicely, and tell
        try {
            final String fontLocation = file.getPath();
            final FileInputStream fontStream = new FileInputStream(fontLocation);
            final Font font = Font.createFont(java.awt.Font.TRUETYPE_FONT, fontStream);

            FONT_FILE_MAP.put(font, fontLocation);

            return font.getFontName();
        } catch (final FontFormatException | IOException e) {
            logger.error("Error reading font in FontHandler: " + file.getAbsolutePath(), e);
        }

        return null;
    }

    public static FontHandler getInstance() {
        // it's ok, we can call this constructor
        if (instance == null) {
            instance = new FontHandler();
        }

        return instance;
    }

    public Font getDefaultFont() {
        return (Font) ((TreeMap) FONT_FILE_MAP).firstKey();
    }

    public Map<Font, String> getFontFileMap() {
        return FONT_FILE_MAP;
    }

    public String getFontDirectory(final Font font) {
        final String fontPath = getAbsoluteFontPath(font);
        final String fileName = new File(fontPath).getName();
        return fontPath.substring(0, fontPath.length() - fileName.length());
    }

    public String getAbsoluteFontPath(final Font font) {
        final String fontName = font.getName();

        return FONT_FILE_MAP.entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(fontName))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElseGet(() -> FONT_FILE_MAP.get(getDefaultFont()));
    }

    public Font getFontFromName(final String fontName) {
        return FONT_FILE_MAP.entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(fontName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseGet(this::getDefaultFont);
    }
}
