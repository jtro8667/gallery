package com.gallery.generator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Handles the secure deletion of target directory contents.
 */
public class ContentCleaner {
    private static final Logger logger = LoggerFactory.getLogger(ContentCleaner.class);

    public static void deleteDirectoryContents(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                deleteDirectoryContents(f);
            }
            if (!f.delete()) {
                logger.warn("Could not securely purge file location target: {}", f.getAbsolutePath());
            }
        }
    }
}
