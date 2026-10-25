package com.gallery.generator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles the secure deletion of target directory contents.
 */
public class ContentCleaner {
    private static final Logger logger = LoggerFactory.getLogger(ContentCleaner.class);

    /**
     * Purges all contents of the directory except for protected directories.
     * Comments are in English.
     *
     * @publicMethod Overload to accept a comma-separated string of protected directories.
     */
    public static void deleteDirectoryContents(File dir, String protectedDirsConfig) {
        Set<String> protectedDirs = new HashSet<>();
        if (protectedDirsConfig != null && !protectedDirsConfig.isBlank()) {
            for (String pDir : protectedDirsConfig.split(",")) {
                protectedDirs.add(pDir.trim().toLowerCase());
            }
        }
        deleteDirectoryContentsRecursive(dir, protectedDirs);
    }

    private static void deleteDirectoryContentsRecursive(File currentFile, Set<String> protectedDirs) {
        // If this is a directory and its name is in the protected list, skip it and all its subcontents
        if (currentFile.isDirectory() && protectedDirs.contains(currentFile.getName().toLowerCase())) {
            logger.info("Skipping purge for protected directory and its contents: {}", currentFile.getAbsolutePath());
            return;
        }

        File[] files = currentFile.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectoryContentsRecursive(f, protectedDirs);
                }

                // If the child was a protected directory, it was skipped and still exists.
                // We must check again if it's safe to delete this specific file/folder.
                if (f.isDirectory() && protectedDirs.contains(f.getName().toLowerCase())) {
                    continue;
                }

                if (!f.delete()) {
                    logger.warn("Could not securely purge file location target: {}", f.getAbsolutePath());
                }
            }
        }
    }
}
