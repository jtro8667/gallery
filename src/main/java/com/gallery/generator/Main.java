package com.gallery.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gallery.generator.config.AppConfig;
import com.gallery.generator.model.RootEntry;
import com.gallery.generator.util.ContentCleaner;
import com.gallery.generator.util.GalleryProcessor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Entry point of the Gallery Generator application.
 * Performs directory security validations and initiates processing.
 */
public class Main {

    public static void main(String[] args) {
        AppConfig config = new AppConfig(args);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        File sourceDir = new File(config.getSourceDir());
        File targetDir = new File(config.getTargetDir());

        if (config.isCheckOnly()) {
            System.out.println("RUNNING IN CHECK-ONLY MODE. No files or directories will be altered.");
        }

        if (!validateDirectories(sourceDir, targetDir)) {
            return;
        }

        if (!config.isCheckOnly()) {
            if (targetDir.exists()) {
                ContentCleaner.deleteDirectoryContents(targetDir);
            } else if (!targetDir.mkdirs()) {
                System.err.println("ERROR: Failed to create target_dir structure.");
                return;
            }
        }

        GalleryProcessor processor = new GalleryProcessor(config, mapper);
        // Execute the optimized high-performance tree pipeline model scan layout
        processor.processDirectory(sourceDir, sourceDir, targetDir);

        List<RootEntry> rootEntries = processor.getRootEntries();
        if (!config.isCheckOnly() && !rootEntries.isEmpty()) {
            File rootJsonFile = new File(targetDir, config.getRootJsonName());
            try {
                mapper.writeValue(rootJsonFile, rootEntries);
            } catch (IOException e) {
                System.err.println("WARNING: Failed to write root JSON file: " + e.getMessage());
            }
        }
        
        if (!config.isCheckOnly()) {
            copyNetlifyToml(targetDir);
        }
        
        System.out.println("Processing completed successfully.");
    }

    private static boolean validateDirectories(File sourceDir, File targetDir) {
        try {
            String sourceCanonical = sourceDir.getCanonicalPath();
            String targetCanonical = targetDir.getCanonicalPath();

            if (sourceCanonical.equals(targetCanonical)) {
                System.err.println("ERROR: source_dir and target_dir cannot be the same location.");
                return false;
            }
            if (targetCanonical.startsWith(sourceCanonical + File.separator)) {
                System.err.println("ERROR: target_dir is strictly forbidden from being a subdirectory of source_dir.");
                return false;
            }
        } catch (IOException e) {
            System.err.println("ERROR: Failed verification of directory security constraints: " + e.getMessage());
            return false;
        }

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("ERROR: source_dir does not exist or is not a directory.");
            return false;
        }

        return true;
    }

    private static void copyNetlifyToml(File targetDir) {
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("netlify.toml")) {
            if (input == null) {
                System.err.println("WARNING: netlify.toml not found in resources, skipping copy");
                return;
            }
            File netlifyTomlFile = new File(targetDir, "netlify.toml");
            Files.copy(input, netlifyTomlFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Copied netlify.toml to target directory");
        } catch (IOException e) {
            System.err.println("WARNING: Failed to copy netlify.toml: " + e.getMessage());
        }
    }
}