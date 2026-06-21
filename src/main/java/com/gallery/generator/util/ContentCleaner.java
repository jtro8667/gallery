package com.gallery.generator.util;

import java.io.File;

/**
 * Handles the secure deletion of target directory contents.
 */
public class ContentCleaner {

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
                System.err.println("WARNING: Could not securely purge file location target: " + f.getAbsolutePath());
            }
        }
    }
}
