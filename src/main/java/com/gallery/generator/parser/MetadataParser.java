package com.gallery.generator.parser;

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
                System.err.println("WARNING: Metadata file is empty: " + file.getAbsolutePath());
                this.galleryName = fallbackName;
                this.validHeader = false;
                return;
            }

            parseHeaderStrictly(header, fallbackName, file.getAbsolutePath());

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String trimmedLine = line.stripLeading();
                int firstSpaceIndex = -1;
                for (int i = 0; i < trimmedLine.length(); i++) {
                    if (Character.isWhitespace(trimmedLine.charAt(i))) {
                        firstSpaceIndex = i;
                        break;
                    }
                }

                if (firstSpaceIndex == -1) {
                    System.err.println("WARNING: Unexpected line format in metadata file (no description): " + line + " in " + file.getAbsolutePath());
                    continue;
                }

                String id = trimmedLine.substring(0, firstSpaceIndex).trim();
                String description = trimmedLine.substring(firstSpaceIndex).trim();

                // ROBUST CLEANING CRITERIA: Remove any hidden carriage returns, line feeds, tabs, or non-breaking spaces
                id = id.replaceAll("[\\r\\n\\t\\u00A0]", "").trim();

                if (id.isEmpty() || description.isEmpty()) {
                    System.err.println("WARNING: Invalid row elements found in line: " + line + " in " + file.getAbsolutePath());
                    continue;
                }

                // Store inside the index map using full lowercase as per architecture requirements
                imageDescriptions.put(id.toLowerCase(), description);
                hasDescriptions = true;
            }


        } catch (Exception e) {
            System.err.println("WARNING: Error reading metadata file " + file.getAbsolutePath() + ": " + e.getMessage());
            this.galleryName = fallbackName;
            this.validHeader = false;
        }
    }

    private void parseHeaderStrictly(String header, String fallbackName, String filePath) {
        try {
            String workingHeader = header.trim();

            int openBracket = workingHeader.indexOf('(');
            int closeBracket = workingHeader.lastIndexOf(')');
            if (openBracket != -1 && closeBracket != -1 && openBracket < closeBracket) {
                this.event = workingHeader.substring(openBracket + 1, closeBracket).trim();
                workingHeader = workingHeader.substring(0, openBracket).trim();
            } else if (openBracket != -1 || closeBracket != -1) {
                System.err.println("WARNING: Malformed bracket structure in header: \"" + header + "\" in " + filePath);
                this.validHeader = false;
            }

            int commaIndex = workingHeader.indexOf(',');
            if (commaIndex != -1) {
                this.galleryName = workingHeader.substring(0, commaIndex).trim();
                this.date = workingHeader.substring(commaIndex + 1).trim();
            } else {
                this.galleryName = workingHeader.trim();
            }

            if (this.galleryName.isEmpty()) {
                System.err.println("WARNING: Mandatory gallery name is empty in header: \"" + header + "\" in " + filePath);
                this.galleryName = fallbackName;
                this.validHeader = false;
            }
        } catch (Exception e) {
            System.err.println("WARNING: Header parsing failed for: \"" + header + "\". Using fallback. File: " + filePath);
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
