package com.gallery.generator.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * Manages application configuration parameters.
 * It searches for the properties file first in the directory where the JAR is located.
 * If not found, it falls back to the internal classpath resource.
 * Command-line arguments override any of these loaded options.
 */
public class AppConfig {
    private static final String PROPERTIES_FILENAME = "gallery-generator.properties";
    private final Properties properties = new Properties();

    public AppConfig(String[] args) {
        loadConfigurationPipeline();
        parseCommandLineArgs(args);
    }

    private void loadConfigurationPipeline() {
        // Step 1: Try to look for an external properties file next to the JAR file
        File externalConfigFile = getExternalConfigurationFile();

        if (externalConfigFile != null && externalConfigFile.exists() && externalConfigFile.isFile()) {
            try (InputStream input = new FileInputStream(externalConfigFile)) {
                properties.load(input);
                System.out.println("Loaded external configuration from: " + externalConfigFile.getAbsolutePath());
                return; // Successfully loaded externally, skip fallback
            } catch (IOException e) {
                System.err.println("WARNING: Error reading external configuration file, falling back to classpath: " + e.getMessage());
            }
        }

        // Step 2: Fallback to internal classpath resource inside the JAR
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILENAME)) {
            if (input == null) {
                System.err.println("WARNING: Unable to find " + PROPERTIES_FILENAME + " inside JAR resources. Using code defaults.");
                return;
            }
            properties.load(input);
            System.out.println("Loaded default internal configuration from JAR resources.");
        } catch (IOException e) {
            System.err.println("WARNING: Error loading internal resource config: " + e.getMessage());
        }
    }

    private File getExternalConfigurationFile() {
        try {
            // Resolves the absolute path to the running code location (JAR or class directory)
            File codeLocation = new File(AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File jarDirectory = codeLocation.getParentFile();

            if (jarDirectory != null && jarDirectory.exists()) {
                return new File(jarDirectory, PROPERTIES_FILENAME);
            }
        } catch (URISyntaxException | SecurityException e) {
            System.err.println("WARNING: Could not determine JAR execution directory framework environment: " + e.getMessage());
        }
        return null;
    }

    private void parseCommandLineArgs(String[] args) {
        for (String arg : args) {
            int equalsIndex = arg.indexOf('=');
            if (equalsIndex > 0) {
                String key = arg.substring(0, equalsIndex).trim();
                String value = arg.substring(equalsIndex + 1).trim();
                properties.setProperty(key, value);
            } else {
                System.err.println("WARNING: Invalid command line argument format: " + arg);
            }
        }
    }

    public String getSourceDir() { return properties.getProperty("source_dir"); }
    public String getTargetDir() { return properties.getProperty("target_dir"); }
    public double getTargetImageResolutionPct() { return Double.parseDouble(properties.getProperty("target_image_resolution_pct")); }
    public double getTargetPreviewResolutionPct() { return Double.parseDouble(properties.getProperty("target_preview_resolution_pct")); }
    public String getTargetPreviewDirName() { return properties.getProperty("target_preview_dir_name"); }
    public String getFilesToProcessMask() { return properties.getProperty("files_to_process_mask"); }
    public String getMetadataFileMask() { return properties.getProperty("metadata_file_mask"); }
    public String getAdditionalFileMask() { return properties.getProperty("additional_file_mask"); }
    public String getExcludedSubdirectoriesMask() { return properties.getProperty("excluded_subdirectories_mask"); }
    public String getMetadataIdToImageMapping() { return properties.getProperty("metadata_ID_to_image_mapping"); }
    public String getNoCommentsFilesMask() { return properties.getProperty("no_comments_files_mask"); }
    public String getGalleryJsonName() { return properties.getProperty("gallery_JSON_name"); }
    public String getRootJsonName() { return properties.getProperty("root_JSON_name"); }
    public boolean isCheckOnly() { return Boolean.parseBoolean(properties.getProperty("check_only", "false")); }
}
