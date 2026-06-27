package com.gallery.generator.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallery.generator.config.AppConfig;
import com.gallery.generator.model.*;
import com.gallery.generator.parser.MetadataParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * High-performance tree processor orchestrating bottom-up data pipeline execution flows.
 */
public class GalleryProcessor {
    private final AppConfig config;
    private final ObjectMapper mapper;
    private final List<RootEntry> rootEntries = Collections.synchronizedList(new ArrayList<>());
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MaskMatcher excludeSubMask;
    private final MaskMatcher imageMask;
    private final MaskMatcher metaMask;
    private final MaskMatcher additionalMask;
    private final MaskMatcher noCommentMask;
    private final MaskMatcher doNotResizeMask;

    public GalleryProcessor(AppConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.excludeSubMask = new MaskMatcher(config.getExcludedSubdirectoriesMask());
        this.imageMask = new MaskMatcher(config.getFilesToProcessMask());
        this.metaMask = new MaskMatcher(config.getMetadataFileMask());
        this.additionalMask = new MaskMatcher(config.getAdditionalFileMask());
        this.noCommentMask = new MaskMatcher(config.getNoCommentsFilesMask());
        this.doNotResizeMask = new MaskMatcher(config.getDoNotResizeFilesMask());
    }

    public List<RootEntry> getRootEntries() {
        List<RootEntry> sorted = new ArrayList<>(rootEntries);
        sorted.sort(Comparator.comparing(RootEntry::directory));
        return sorted;
    }

    private void logProgress(String message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.out.println("[" + timestamp + "] " + message);
    }

    /**
     * Recursively scans and processes the file tree using a high-efficiency bottom-up pipeline model.
     *
     * @return DirectoryResult metadata bubble-up token payload mapping the scanned state.
     */
    public DirectoryResult processDirectory(File currentSrcDir, File baseSrcDir, File baseTgtDir) {
        if (excludeSubMask.matches(currentSrcDir.getName())) {
            System.out.println("Excluding " + currentSrcDir.getAbsolutePath());
            return null;
        }

        File[] allFiles = currentSrcDir.listFiles();
        if (allFiles == null) return null;

        List<File> imageFiles = new ArrayList<>();
        List<File> metadataFiles = new ArrayList<>();
        File additionalFile = null;
        List<File> rawSubDirectories = new ArrayList<>();

        // Perform a single atomic directory contents scan step
        for (File file : allFiles) {
            String name = file.getName();
            if (file.isDirectory()) {
                rawSubDirectories.add(file);
            } else {
                if (imageMask.matches(name)) {
                    imageFiles.add(file);
                } else if (metaMask.matches(name)) {
                    metadataFiles.add(file);
                } else if (additionalMask.matches(name)) {
                    additionalFile = file;
                } else if (name.toLowerCase().endsWith(".txt")) {
                    System.err.println("WARNING: Unexpected text file found: " + file.getAbsolutePath());
                }
            }
        }

        // Bottom-up recursion step: resolve all child branches first to inherit their results
        List<DirectoryResult> activeChildResults = new ArrayList<>();
        for (File subDir : rawSubDirectories) {
            DirectoryResult childRes = processDirectory(subDir, baseSrcDir, baseTgtDir);
            if (childRes != null && childRes.hasContent()) {
                activeChildResults.add(childRes);
            }
        }

        boolean isRootBaseDirectory = currentSrcDir.equals(baseSrcDir);
        boolean hasLocalContent = !imageFiles.isEmpty() || !metadataFiles.isEmpty();
        boolean hasActiveBranchTree = hasLocalContent || !activeChildResults.isEmpty();

        // Enforce boundary constraint: skip local structural mapping execution routines for root directory path
        if (isRootBaseDirectory || !hasActiveBranchTree) {
            return new DirectoryResult(null, null, currentSrcDir.getName(), config.getGalleryJsonName(), null, hasActiveBranchTree, activeChildResults);
        }

        String relativePath = baseSrcDir.toURI().relativize(currentSrcDir.toURI()).getPath();
        File currentTgtDir = new File(baseTgtDir, relativePath);

        logProgress(String.format("Processing directory %s: %d images found, %d active child branches.",
                currentSrcDir.getName(), imageFiles.size(), activeChildResults.size()));

        // Resolve Fallback Names from child metadata tokens without running disk I/O routines again
        String implicitFallbackName = null;
        for (DirectoryResult child : activeChildResults) {
            if (child.galleryName() != null && !child.galleryName().isBlank()) {
                implicitFallbackName = child.galleryName();
                break;
            }
        }
        if (implicitFallbackName == null) {
            implicitFallbackName = currentSrcDir.getName();
        }

        MetadataParser parser = new MetadataParser();
        if (metadataFiles.size() == 1) {
            parser.parse(Optional.of(metadataFiles.get(0)), currentSrcDir.getName());
            if (!parser.isValidHeader()) {
                System.err.println("WARNING: Metadata header validation warning on file: " + metadataFiles.get(0).getAbsolutePath());
            }
        } else {
            if (metadataFiles.size() > 1) {
                System.err.println("WARNING: Expected exactly 1 metadata file in gallery path: " + currentSrcDir.getAbsolutePath() + " (Found: " + metadataFiles.size() + ")");
            }
            parser.parse(Optional.empty(), implicitFallbackName);
        }

        String noteContent = null;
        if (additionalFile != null) {
            try {
                noteContent = Files.readString(additionalFile.toPath(), Charset.forName("windows-1250"));
            } catch (IOException e) {
                System.err.println("WARNING: Could not parse additional file content: " + e.getMessage());
            }
        }

        List<ImageEntry> imagesWithoutDesc = Collections.synchronizedList(new ArrayList<>());
        List<ImageEntry> imagesWithDesc = Collections.synchronizedList(new ArrayList<>());
        Set<String> matchedIds = Collections.synchronizedSet(new HashSet<>());
        File previewDir = new File(currentTgtDir, config.getTargetPreviewDirName());

        int computedThreads = config.getThreadCount();
        if (computedThreads <= 0) {
            computedThreads = Runtime.getRuntime().availableProcessors();
        }

        ExecutorService imageExecutor = Executors.newFixedThreadPool(computedThreads);

        for (File img : imageFiles) {
            imageExecutor.submit(() -> {
                try {
                    if (config.isVerbose()) {
                        logProgress(" -> Processing file: " + img.getName());
                    }

                    String baseName = img.getName();
                    int dotIndex = baseName.lastIndexOf('.');
                    String targetFilename = baseName;
                    if (dotIndex != -1) {
                        targetFilename = baseName.substring(0, dotIndex) + baseName.substring(dotIndex).toLowerCase();
                    }

                    if (!config.isCheckOnly()) {
                        synchronized (this) {
                            if (!currentTgtDir.exists() && !currentTgtDir.mkdirs()) {
                                System.err.println("WARNING: Failed creating structure directory: " + currentTgtDir.getAbsolutePath());
                                return;
                            }
                        }

                        File outputFile = new File(currentTgtDir, targetFilename);
                        if (doNotResizeMask.matches(img.getName())) {
                            ImageResizer.copyFileAndTransferExif(img, outputFile, config.isCopyExif());
                        } else {
                            ImageResizer.resizeImagePct(img, outputFile, config.getTargetImageResolutionPct(), config.isCopyExif(), config.isIncludeWatermark());
                        }

                        synchronized (this) {
                            if (!previewDir.exists() && !previewDir.mkdirs()) {
                                System.err.println("WARNING: Previews directory creation failed: " + previewDir.getAbsolutePath());
                            }
                        }
                        File outputFilePreview = new File(previewDir, targetFilename);
                        ImageResizer.resizeToMaxSide(img, outputFilePreview, config.getTargetPreviewMaxSidePx());
                    }

                    String matchedDescription = lookupDescription(img.getName(), parser.getImageDescriptions(), matchedIds);
                    String relativePreviewPath = config.getTargetPreviewDirName() + "/" + targetFilename;

                    ImageEntry entry = new ImageEntry(targetFilename, relativePreviewPath, matchedDescription);
                    if (matchedDescription == null) {
                        imagesWithoutDesc.add(entry);
                    } else {
                        imagesWithDesc.add(entry);
                    }
                } catch (Exception e) {
                    System.err.println("WARNING: Unexpected processing failure handling image " + img.getAbsolutePath() + ": " + e.getMessage());
                }
            });
        }
        imageExecutor.shutdown();
        try {
            if (!imageExecutor.awaitTermination(1, TimeUnit.HOURS)) {
                System.err.println("ERROR: Image scaling pool execution timed out before completion.");
            }
        } catch (InterruptedException e) {
            System.err.println("ERROR: Multi-threaded operation interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        validateMissingDescriptions(imageFiles, parser, currentSrcDir.getAbsolutePath());
        imagesWithoutDesc.sort(Comparator.comparing(ImageEntry::image));
        imagesWithDesc.sort(Comparator.comparing(ImageEntry::image));
        List<ImageEntry> combinedImagesList = new ArrayList<>();
        combinedImagesList.addAll(imagesWithoutDesc);
        combinedImagesList.addAll(imagesWithDesc);
        // Map subdirectories structured items matching formatting constraints rule updatesList
        List<SubdirectoryEntry> structuredChildren = new ArrayList<>();
        for (DirectoryResult child : activeChildResults) {
            structuredChildren.add(new com.gallery.generator.model.SubdirectoryEntry(child.directoryName(), child.galleryJsonName()));
        }
        if (!config.isCheckOnly()) {
            if (!currentTgtDir.exists() && !currentTgtDir.mkdirs()) {
                System.err.println("WARNING: Failed creating structure directory for JSON metadata container: " + currentTgtDir.getAbsolutePath());
            } else {
                GalleryIndex galleryIndex = new GalleryIndex(parser.getGalleryName(), parser.getDate(), parser.getEvent(), noteContent, combinedImagesList.isEmpty() ? null : combinedImagesList, structuredChildren.isEmpty() ? null : structuredChildren);
                try {
                    mapper.writeValue(new File(currentTgtDir, config.getGalleryJsonName()), galleryIndex);
                } catch (IOException e) {
                    System.err.println("WARNING: Failed writing destination index json layout: " + e.getMessage());
                }
            }
        }
        // Determine local preview fallback target reference paths tokensString
        String resolvedLocalPreview = null;
        if (!combinedImagesList.isEmpty()) {
            for (ImageEntry entry : combinedImagesList) {
                if (!noCommentMask.matches(entry.image())) {
                    resolvedLocalPreview = entry.preview();
                    break;
                }
            }
            if (resolvedLocalPreview == null) {
                System.err.println(String.format("WARNING: All images in %s match no_comments_files_mask. Using first available image for preview.", currentSrcDir.getName()));
                resolvedLocalPreview = combinedImagesList.get(0).preview();
            }
        } else {
            // Bubble-up preview mapping inheritance strategy from the first active nested child context layout segment
            for (DirectoryResult child : activeChildResults) {
                if (child.previewPath() != null) {
                    resolvedLocalPreview = child.directoryName() + "/" + child.previewPath();
                    break;
                }
            }
        }
        DirectoryResult localResultPayload = new DirectoryResult(parser.getGalleryName(), parser.getDate(), currentSrcDir.getName(), config.getGalleryJsonName(), resolvedLocalPreview, true, activeChildResults);
        // Perform root catalog generation registry check if operating at direct branch levels
        if (currentSrcDir.getParentFile().equals(baseSrcDir)) {
            String trackingPreview = localResultPayload.previewPath() != null ? currentSrcDir.getName() + "/" + localResultPayload.previewPath() : null;
            rootEntries.add(new RootEntry(localResultPayload.galleryName(), localResultPayload.date(), currentSrcDir.getName(), config.getGalleryJsonName(), trackingPreview));
        }
        return localResultPayload;
    }

    private String lookupDescription(String filename, Map<String, String> descriptions, Set matchedIds) {
        String id = lookupId(filename);
        if (id != null && descriptions.containsKey(id.toLowerCase())) {
            String idLower = id.toLowerCase();
            matchedIds.add(idLower);
            return descriptions.get(idLower);
        }
        return null;
    }

    private String lookupId(String filename) {
        String fileLower = filename.toLowerCase();
        String mappingConfig = config.getMetadataIdToImageMapping();
        if (mappingConfig == null || mappingConfig.isBlank()) return null;
        String[] patterns = mappingConfig.split(",");
        for (String pattern : patterns) {
            String template = pattern.trim().toLowerCase();
            int idMarkerIndex = template.indexOf("<id>");
            if (idMarkerIndex == -1) continue;
            String prefix = template.substring(0, idMarkerIndex);
            String suffix = template.substring(idMarkerIndex + 4);
            if (fileLower.startsWith(prefix) && fileLower.endsWith(suffix)) {
                int endIdx = fileLower.length() - suffix.length();
                if (endIdx >= prefix.length()) {
                    return filename.substring(prefix.length(), endIdx);
                }
            }
        }
        return null;
    }

    private void validateMissingDescriptions(List<File> imageFiles, MetadataParser parser, String currentSrcDirPath) {
        if (parser.hasDescriptions()) {
            for (File img : imageFiles) {
                if (!noCommentMask.matches(img.getName())) {
                    String checkId = lookupId(img.getName());
                    if (checkId == null || !parser.getImageDescriptions().containsKey(checkId.toLowerCase())) {
                        System.err.println("WARNING: Directory: " + currentSrcDirPath + " - missing metadata description for image: " + img.getName());
                    }
                }
            }
        }
        validateNonExistentIds(imageFiles, parser, currentSrcDirPath);
    }

    private void validateNonExistentIds(List<File> imageFiles, MetadataParser parser, String currentSrcDirPath) {
        Map<String, String> declaredDescriptions = parser.getImageDescriptions();
        if (declaredDescriptions.isEmpty()) return;
        Set filesystemIdsLower = new HashSet<>();
        for (File img : imageFiles) {
            String fsId = lookupId(img.getName());
            if (fsId != null) {
                filesystemIdsLower.add(fsId.toLowerCase());
            }
        }
        for (String declaredId : declaredDescriptions.keySet()) {
            if (!filesystemIdsLower.contains(declaredId.toLowerCase()))
                System.err.println(String.format("WARNING: Directory: %s - metadata declares description for ID '%s', but no matching image file exists.", currentSrcDirPath, declaredId));
        }
    }
}
