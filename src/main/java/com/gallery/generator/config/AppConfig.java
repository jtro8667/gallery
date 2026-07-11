package com.gallery.generator.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Properties;

public class AppConfig {
    private static final String PROPERTIES_FILENAME = "gallery-generator.properties";
    private final Properties properties = new Properties();

    public AppConfig(String[] args) {
        loadConfigurationPipeline();
        parseCommandLineArgs(args);
        validateImageResizeConfiguration();
    }

    private void validateImageResizeConfiguration() {
        String maxSize = properties.getProperty("target_image_max_size");
        String resolutionPct = properties.getProperty("target_image_resolution_pct");
        if (maxSize == null && resolutionPct == null) {
            throw new IllegalStateException("Configuration error: Either target_image_max_size or target_image_resolution_pct must be specified.");
        }
    }

    private void loadConfigurationPipeline() {
        File externalConfigFile = getExternalConfigurationFile();
        if (externalConfigFile != null && externalConfigFile.exists() && externalConfigFile.isFile()) {
            try (InputStream input = new FileInputStream(externalConfigFile)) {
                properties.load(input);
                System.out.println("Loaded external configuration from: " + externalConfigFile.getAbsolutePath());
                return;
            } catch (IOException e) {
                System.err.println("WARNING: Error reading external configuration file, falling back to classpath: " + e.getMessage());
            }
        }

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
            File codeLocation = new File(AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File jarDirectory = codeLocation.getParentFile();
            if (jarDirectory != null && jarDirectory.exists()) {
                return new File(jarDirectory, PROPERTIES_FILENAME);
            }
        } catch (URISyntaxException | SecurityException e) {
            System.err.println("WARNING: Could not determine JAR execution directory environment: " + e.getMessage());
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
    public Double getTargetImageResolutionPct() {
        String value = properties.getProperty("target_image_resolution_pct");
        return value != null ? Double.parseDouble(value) : null;
    }
    public Integer getTargetImageMaxSize() {
        String value = properties.getProperty("target_image_max_size");
        return value != null ? Integer.parseInt(value) : null;
    }
    public int getTargetPreviewMaxSidePx() { return Integer.parseInt(properties.getProperty("target_preview_max_side_px", "400")); }
    public String getTargetPreviewDirName() { return properties.getProperty("target_preview_dir_name"); }
    public String getFilesToProcessMask() { return properties.getProperty("files_to_process_mask"); }
    public String getDoNotResizeFilesMask() { return properties.getProperty("do_not_resize_files_mask", ""); }
    public String getMetadataFileMask() { return properties.getProperty("metadata_file_mask"); }
    public String getAdditionalFileMask() { return properties.getProperty("additional_file_mask"); }
    public String getExcludedSubdirectoriesMask() { return properties.getProperty("excluded_subdirectories_mask"); }
    public String getMetadataIdToImageMapping() { return properties.getProperty("metadata_ID_to_image_mapping"); }
    public String getNoCommentsFilesMask() { return properties.getProperty("no_comments_files_mask"); }
    public String getGalleryJsonName() { return properties.getProperty("gallery_JSON_name"); }
    public String getRootJsonName() { return properties.getProperty("root_JSON_name"); }
    public boolean isCheckOnly() { return Boolean.parseBoolean(properties.getProperty("check_only", "false")); }
    public boolean isVerbose() { return Boolean.parseBoolean(properties.getProperty("verbose", "false")); }
    public int getThreadCount() { return Integer.parseInt(properties.getProperty("thread_count", "0")); }
    public boolean isCopyExif() { return Boolean.parseBoolean(properties.getProperty("copy_exif", "true")); }
    public boolean isIncludeWatermark() { return Boolean.parseBoolean(properties.getProperty("includeWatermark", "false")); }
    public boolean isRegenerateExistingImages() { return Boolean.parseBoolean(properties.getProperty("regenerateExistingImages", "true")); }
}
