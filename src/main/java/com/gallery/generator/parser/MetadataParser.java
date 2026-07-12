package com.gallery.generator.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parses gallery metadata from files encoded in Windows-1250.
 * Header parsing is performed strictly using string manipulation functions.
 */
public class MetadataParser {
    private static final Logger logger = LoggerFactory.getLogger(MetadataParser.class);
    private String galleryName;
    private String date;
    private String event;
    private final Map<String, String> imageDescriptions = new HashMap<>();
    private boolean hasDescriptions = false;
    private boolean validHeader = true;

    public void parse(Optional<File> metadataFile, String fallbackName) {
        // If the metadata file is missing completely, apply safe fallback instantly
        if (metadataFile.isEmpty()) {
            this.galleryName = fallbackName;
            this.validHeader = false;
            return;
        }

        File file = metadataFile.get();
        Charset windows1250 = Charset.forName("windows-1250");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), windows1250))) {
            String header = reader.readLine();
            if (header == null || header.isBlank()) {
                logger.warn("Metadata file is empty: {}", file.getAbsolutePath());
                this.galleryName = fallbackName;
                this.validHeader = false;
                return;
            }

            parseHeaderStrictly(header, fallbackName, file.getAbsolutePath());

            String line;
            int lineNo = 1; // Start at 2 since line 1 is the header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    lineNo++;
                    continue;
                }

                String trimmedLine = line.stripLeading();
                int firstSpaceIndex = -1;
                for (int i = 0; i < trimmedLine.length(); i++) {
                    if (Character.isWhitespace(trimmedLine.charAt(i))) {
                        firstSpaceIndex = i;
                        break;
                    }
                }

                if (firstSpaceIndex == -1) {
                    logger.warn("Unexpected line format in metadata file (no description) at line {} file {}: {}", lineNo+1, file.getAbsolutePath(), line);
                    lineNo++;
                    continue;
                }

                String id = trimmedLine.substring(0, firstSpaceIndex).trim();
                String description = trimmedLine.substring(firstSpaceIndex).trim();

                // ROBUST CLEANING CRITERIA: Remove any hidden carriage returns, line feeds, tabs, or non-breaking spaces
                id = id.replaceAll("[\\r\\n\\t\\u00A0]", "").trim();

                if (id.isEmpty() || description.isEmpty()) {
                    logger.warn("Invalid row elements found in line {} file {}: {}", lineNo+1, file.getAbsolutePath(), line);
                    lineNo++;
                    continue;
                }

                // Store inside the index map using full lowercase as per architecture requirements
                imageDescriptions.put(id.toLowerCase(), description);
                hasDescriptions = true;
                lineNo++;
            }


        } catch (Exception e) {
            logger.warn("Error reading metadata file {}: {}", file.getAbsolutePath(), e.getMessage());
            this.galleryName = fallbackName;
            this.validHeader = false;
        }
    }

    void parseHeaderStrictly(String header, String fallbackName, String filePath) {
        try {
            String workingHeader = header.trim();

            int commaIndex = workingHeader.indexOf(',');
            int openBracket = workingHeader.indexOf('(');

            int bracketCount = 0;
            int pos;
            for (pos = openBracket; pos < workingHeader.length() && openBracket != -1 && commaIndex != -1 && openBracket < commaIndex; pos++){
                if (workingHeader.charAt(pos) == '(') bracketCount++;
                if (workingHeader.charAt(pos) == ')') bracketCount--;
                if (bracketCount == 0) { //brackets closed
                    commaIndex = workingHeader.indexOf(',', pos);
                    openBracket = workingHeader.indexOf('(', pos);
                }
            }

            if (commaIndex != -1) {
                this.galleryName = workingHeader.substring(0, commaIndex).trim();
                this.date = workingHeader.substring(commaIndex + 1, openBracket == -1 ? workingHeader.length() : openBracket).trim();
            } else {
                this.galleryName = workingHeader.substring(0, openBracket == -1 ? workingHeader.length() : openBracket).trim();
            }

            int closeBracket = workingHeader.lastIndexOf(')');
            if (closeBracket < pos+1) closeBracket = -1;
            if (openBracket != -1 && closeBracket != -1 && openBracket < closeBracket) {
                this.event = workingHeader.substring(openBracket + 1, closeBracket).trim();
            } else if (openBracket != -1 || closeBracket != -1) {
                logger.warn("Malformed bracket structure in header: \"{}\" in {}", header, filePath);
                this.validHeader = false;
            }

            if (this.galleryName.isEmpty()) {
                logger.warn("Mandatory gallery name is empty in header: \"{}\" in {}", header, filePath);
                this.galleryName = fallbackName;
                this.validHeader = false;
            }
        } catch (Exception e) {
            logger.warn("Header parsing failed for: \"{}\". Using fallback. File: {}", header, filePath);
            this.galleryName = fallbackName;
            this.validHeader = false;
        }
    }

    public String getGalleryName() { return galleryName; }
    public String getDate() { return date; }
    public String getEvent() { return event; }
    public Map<String, String> getImageDescriptions() { return imageDescriptions; }
    public boolean hasDescriptions() { return hasDescriptions; }
    public boolean isValidHeader() { return validHeader; }
}
