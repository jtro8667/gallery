package com.gallery.generator.model;

import java.util.List;

/**
 * Immutable record representing the processing metadata result of a scanned directory branch.
 * Passed upwards during bottom-up tree traversal to eliminate redundant disk I/O operations.
 */
public record DirectoryResult(
        String galleryName,
        String date,
        String directoryName,
        String galleryJsonName,
        String previewPath,
        boolean hasContent,
        List<DirectoryResult> activeSubdirectories
) {}

