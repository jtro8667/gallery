package com.gallery.generator.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallery.generator.config.AppConfig;
import com.gallery.generator.model.*;
import com.gallery.generator.parser.MetadataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.Collator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * High-performance tree processor orchestrating bottom-up data pipeline execution flows.
 */
public class GalleryProcessor {
    private static final Logger logger = LoggerFactory.getLogger(GalleryProcessor.class);
    private final AppConfig config;
    private final ObjectMapper jsonMapper;
    private final List<RootEntry> rootEntries = Collections.synchronizedList(new ArrayList<>());
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MaskMatcher excludeSubMask;
    private final MaskMatcher imageMask;
    private final MaskMatcher metaMask;
    private final MaskMatcher additionalMask;
    private final MaskMatcher noCommentMask;
    private final MaskMatcher doNotResizeMask;
    private final Collator collator;

    public GalleryProcessor(AppConfig config, ObjectMapper jsonMapper) {
        this.config = config;
        this.jsonMapper = jsonMapper;
        this.excludeSubMask = new MaskMatcher(config.getExcludedSubdirectoriesMask());
        this.imageMask = new MaskMatcher(config.getFilesToProcessMask());
        this.metaMask = new MaskMatcher(config.getMetadataFileMask());
        this.additionalMask = new MaskMatcher(config.getAdditionalFileMask());
        this.noCommentMask = new MaskMatcher(config.getNoCommentsFilesMask());
        this.doNotResizeMask = new MaskMatcher(config.getDoNotResizeFilesMask());
        this.collator = Collator.getInstance(Locale.forLanguageTag(config.getLocale()));
    }

    public List<RootEntry> getRootEntries() {
        List<RootEntry> sorted = new ArrayList<>(rootEntries);
        sorted.sort(Comparator.comparing(RootEntry::directory, collator));
        return sorted;
    }

    private void logProgress(String message) {
        logger.info(message);
    }

    /**
     * Recursively scans and processes the file tree using a high-efficiency bottom-up pipeline model.
     *
     * @return DirectoryResult metadata bubble-up token payload mapping the scanned state.
     */
    public DirectoryResult processDirectory(File currentSrcDir, File rootDirSrc, File baseTgtDir) {
        if (excludeSubMask.matches(currentSrcDir.getName())) {
            logger.info("Excluding {}", currentSrcDir.getAbsolutePath());
            return null;
        }

        File[] allFiles = currentSrcDir.listFiles();
        if (allFiles == null) return null;

        DirectoryContents contents = scanDirectoryContents(allFiles);

        // Bottom-up recursion step: resolve all child branches first to inherit their results
        List<DirectoryResult> activeSubDirs = new ArrayList<>();
        for (File subDir : contents.subDirectories) {
            DirectoryResult childRes = processDirectory(subDir, rootDirSrc, baseTgtDir);
            if (childRes != null && childRes.hasContent()) {
                activeSubDirs.add(childRes);
            }
        }

        boolean isRootBaseDirectory = currentSrcDir.equals(rootDirSrc);
        boolean hasLocalContent = !contents.imageFiles().isEmpty() || !contents.metadataFiles().isEmpty();
        boolean hasActiveBranchTree = hasLocalContent || !activeSubDirs.isEmpty();

        // Enforce boundary constraint: skip local structural mapping execution routines for root directory path
        if (isRootBaseDirectory || !hasActiveBranchTree) {
            return new DirectoryResult(null, null, currentSrcDir.getName(), config.getGalleryJsonName(), null, hasActiveBranchTree, activeSubDirs);
        }

        String relativePath = rootDirSrc.toURI().relativize(currentSrcDir.toURI()).getPath();
        File currentTgtDir = new File(baseTgtDir, relativePath);

        logProgress(String.format("Processing directory %s: %d images found, %d active child branches.",
                currentSrcDir.getName(), contents.imageFiles().size(), activeSubDirs.size()));

        // Resolve Fallback Names from child metadata tokens without running disk I/O routines again
        String implicitFallbackName = null;
        for (DirectoryResult child : activeSubDirs) {
            if (child.galleryName() != null && !child.galleryName().isBlank()) {
                implicitFallbackName = child.galleryName();
                break;
            }
        }
        if (implicitFallbackName == null) {
            implicitFallbackName = currentSrcDir.getName();
        }

        MetadataParser parser = parseMetadata(contents.metadataFiles(), contents.additionalFile(), currentSrcDir, implicitFallbackName);
        String noteContent = readAdditionalFile(contents.additionalFile());

        ImageProcessingResult imageResult = processImagesInParallel(contents.imageFiles(), currentTgtDir, parser.getImageDescriptions());

        validateMissingDescriptions(contents.imageFiles(), parser, currentSrcDir.getAbsolutePath());

        List<ImageEntry> combinedImagesList = imageResult.combinedImages();
        List<SubdirectoryEntry> structuredChildren = new ArrayList<>();
        for (DirectoryResult child : activeSubDirs) {
            structuredChildren.add(new SubdirectoryEntry(child.directoryName(), child.galleryJsonName(), child.previewPath()));
        }
        writeGalleryIndex(currentTgtDir, parser, noteContent, combinedImagesList, structuredChildren);

        String resolvedLocalPreview = resolvePreviewPath(combinedImagesList, activeSubDirs, currentSrcDir);

        DirectoryResult currentDirInfo = new DirectoryResult(parser.getGalleryName(), parser.getDate(), currentSrcDir.getName(), config.getGalleryJsonName(), resolvedLocalPreview, true, activeSubDirs);

        if (currentSrcDir.getParentFile().equals(rootDirSrc)) {
            String trackingPreview = currentDirInfo.previewPath() != null ? currentSrcDir.getName() + "/" + currentDirInfo.previewPath() : null;
            String dateForRoot = currentDirInfo.date();
            if (config.isTreatSubDirsAsDates() && (dateForRoot == null || dateForRoot.isBlank()))
                dateForRoot = buildArtificalDateFromSubDirs(activeSubDirs);

            rootEntries.add(new RootEntry(currentDirInfo.galleryName(), dateForRoot, currentSrcDir.getName(), config.getGalleryJsonName(), trackingPreview));
        }
        return currentDirInfo;
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
                        logger.warn("Directory: {} - missing metadata for: {}", currentSrcDirPath, img.getName());
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
                logger.warn("Directory: {} - no file found for metadata ID '{}'", currentSrcDirPath, declaredId);
        }
    }

    private DirectoryContents scanDirectoryContents(File[] allFiles) {
        List<File> imageFiles = new ArrayList<>();
        List<File> metadataFiles = new ArrayList<>();
        File additionalFile = null;
        List<File> rawSubDirectories = new ArrayList<>();

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
                    logger.warn("Unexpected text file found: {}", file.getAbsolutePath());
                }
            }
        }
        rawSubDirectories.sort(Comparator.comparing(File::getName, collator));
        return new DirectoryContents(imageFiles, metadataFiles, additionalFile, rawSubDirectories);
    }

    private MetadataParser parseMetadata(List<File> metadataFiles, File additionalFile, File currentSrcDir, String implicitFallbackName) {
        MetadataParser parser = new MetadataParser();
        if (metadataFiles.size() == 1) {
            parser.parse(Optional.of(metadataFiles.get(0)), currentSrcDir.getName());
            if (!parser.isValidHeader()) {
                logger.warn("Metadata header validation warning on file: {}", metadataFiles.get(0).getAbsolutePath());
            }
        } else {
            if (metadataFiles.size() > 1) {
                logger.warn("Expected exactly 1 metadata file in gallery path: {} (Found: {})", currentSrcDir.getAbsolutePath(), metadataFiles.size());
            }
            parser.parse(Optional.empty(), implicitFallbackName);
        }
        return parser;
    }

    private String readAdditionalFile(File additionalFile) {
        if (additionalFile == null) return null;
        try {
            return Files.readString(additionalFile.toPath(), Charset.forName("windows-1250"));
        } catch (IOException e) {
            logger.warn("Could not parse additional file content: {}", e.getMessage());
            return null;
        }
    }

    private ImageProcessingResult processImagesInParallel(List<File> imageFiles, File currentTgtDir, Map<String, String> descriptions) {
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
                                logger.warn("Failed creating structure directory: {}", currentTgtDir.getAbsolutePath());
                                return;
                            }
                        }

                        File outputFile = new File(currentTgtDir, targetFilename);
                        if (config.isRegenerateExistingImages() || !outputFile.exists()) {
                            if (doNotResizeMask.matches(img.getName())) {
                                ImageResizer.copyFileAndTransferExif(img, outputFile, config.isCopyExif());
                            } else {
                                Integer maxSize = config.getTargetImageMaxSize();
                                if (maxSize != null) {
                                    ImageResizer.resizeToMaxSide(img, outputFile, maxSize, config.isCopyExif(), config.isIncludeWatermark());
                                } else {
                                    ImageResizer.resizeImagePct(img, outputFile, config.getTargetImageResolutionPct(), config.isCopyExif(), config.isIncludeWatermark());
                                }
                            }
                        }

                        synchronized (this) {
                            if (!previewDir.exists() && !previewDir.mkdirs()) {
                                logger.warn("Previews directory creation failed: {}", previewDir.getAbsolutePath());
                            }
                        }
                        File outputFilePreview = new File(previewDir, targetFilename);
                        if (config.isRegenerateExistingImages() || !outputFilePreview.exists()) {
                            ImageResizer.resizeToMaxSide(img, outputFilePreview, config.getTargetPreviewMaxSidePx(), false, false);
                        }
                    }

                    String matchedDescription = lookupDescription(img.getName(), descriptions, matchedIds);
                    String relativePreviewPath = config.getTargetPreviewDirName() + "/" + targetFilename;

                    ImageEntry entry = new ImageEntry(targetFilename, relativePreviewPath, matchedDescription);
                    if (matchedDescription == null) {
                        imagesWithoutDesc.add(entry);
                    } else {
                        imagesWithDesc.add(entry);
                    }
                } catch (Exception e) {
                    logger.warn("Unexpected processing failure handling image {}: {}", img.getAbsolutePath(), e.getMessage());
                }
            });
        }
        imageExecutor.shutdown();
        try {
            if (!imageExecutor.awaitTermination(1, TimeUnit.HOURS)) {
                logger.error("Image scaling pool execution timed out before completion.");
            }
        } catch (InterruptedException e) {
            logger.error("Multi-threaded operation interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        imagesWithoutDesc.sort(Comparator.comparing(ImageEntry::image, collator));
        imagesWithDesc.sort(Comparator.comparing(ImageEntry::image, collator));
        List<ImageEntry> combinedImagesList = new ArrayList<>();
        combinedImagesList.addAll(imagesWithoutDesc);
        combinedImagesList.addAll(imagesWithDesc);

        return new ImageProcessingResult(combinedImagesList);
    }

    private void writeGalleryIndex(File currentTgtDir, MetadataParser parser, String noteContent, List<ImageEntry> combinedImagesList, List<SubdirectoryEntry> subDirectories) {
        if (!config.isCheckOnly()) {
            if (!currentTgtDir.exists() && !currentTgtDir.mkdirs()) {
                logger.warn("Failed creating structure directory for JSON metadata container: {}", currentTgtDir.getAbsolutePath());
            } else {
                GalleryIndex galleryIndex = new GalleryIndex(parser.getGalleryName(), parser.getDate(), parser.getEvent(), noteContent, combinedImagesList, subDirectories.isEmpty() ? null : subDirectories);
                try {
                    jsonMapper.writeValue(new File(currentTgtDir, config.getGalleryJsonName()), galleryIndex);
                } catch (IOException e) {
                    logger.warn("Failed writing destination index json layout: {}", e.getMessage());
                }
            }
        }
    }

    private String resolvePreviewPath(List<ImageEntry> combinedImagesList, List<DirectoryResult> activeSubDirs, File currentSrcDir) {
        String resolvedLocalPreview = null;
        if (!combinedImagesList.isEmpty()) {
            for (ImageEntry entry : combinedImagesList) {
                if (!noCommentMask.matches(entry.image())) {
                    resolvedLocalPreview = entry.preview();
                    break;
                }
            }
            if (resolvedLocalPreview == null) {
                logger.warn("All images in {} match no_comments_files_mask. Using first available image for preview.", currentSrcDir.getName());
                resolvedLocalPreview = combinedImagesList.get(0).preview();
            }
        } else {
            for (DirectoryResult child : activeSubDirs) {
                if (child.previewPath() != null) {
                    resolvedLocalPreview = child.directoryName() + "/" + child.previewPath();
                    break;
                }
            }
        }
        return resolvedLocalPreview;
    }

    private String buildArtificalDateFromSubDirs(List<DirectoryResult> activeSubDirs) {
        if (activeSubDirs == null || activeSubDirs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < activeSubDirs.size(); i++) {
            if (i > 0) sb.append(" / ");
            sb.append(activeSubDirs.get(i).directoryName());
        }
        return sb.toString();
    }

    private record DirectoryContents(List<File> imageFiles, List<File> metadataFiles, File additionalFile, List<File> subDirectories) {}
    private record ImageProcessingResult(List<ImageEntry> combinedImages) {}
}
