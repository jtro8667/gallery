package com.gallery.generator.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallery.generator.config.AppConfig;
import com.gallery.generator.model.GalleryIndex;
import com.gallery.generator.model.ImageEntry;
import com.gallery.generator.model.RootEntry;
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
 * Orchestrates the recursive gallery processing, resizing, and data collection.
 * Ensures target directories are created lazily only if they will contain processed assets.
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

    public void processDirectory(File currentSrcDir, File baseSrcDir, File baseTgtDir) {
        if (excludeSubMask.matches(currentSrcDir.getName())) {
            System.out.println("Excluding " + currentSrcDir.getAbsolutePath());
            return;
        }

        String relativePath = baseSrcDir.toURI().relativize(currentSrcDir.toURI()).getPath();
        File currentTgtDir = new File(baseTgtDir, relativePath);

        File[] allFiles = currentSrcDir.listFiles();
        if (allFiles == null) return;

        List<File> imageFiles = new ArrayList<>();
        List<File> metadataFiles = new ArrayList<>();
        File additionalFile = null;
        List<String> subdirectoriesWithImages = new ArrayList<>();

        for (File file : allFiles) {
            String name = file.getName();
            if (file.isDirectory()) {
                if (!excludeSubMask.matches(name) && hasMatchingImages(file, imageMask, excludeSubMask)) {
                    subdirectoriesWithImages.add(name);
                }
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

        // Only trigger processing logic if there are active images to scale or transform
        if (!imageFiles.isEmpty() || !metadataFiles.isEmpty()) {
            logProgress(String.format("Processing directory %s: %d images found.", currentSrcDir.getName(), imageFiles.size()));
            processGalleryContents(currentSrcDir, currentTgtDir, baseSrcDir, imageFiles, metadataFiles, additionalFile, subdirectoriesWithImages);
        }

        // Continue deep tree traversal for subdirectories
        for (File file : allFiles) {
            if (file.isDirectory()) {
                processDirectory(file, baseSrcDir, baseTgtDir);
            }
        }
    }

    private void processGalleryContents(File currentSrcDir, File currentTgtDir, File baseSrcDir,
                                        List<File> imageFiles, List<File> metadataFiles, File additionalFile,
                                        List<String> subdirectoriesWithImages) {

        MetadataParser parser = new MetadataParser();
        if (metadataFiles.size() == 1) {
            parser.parse(Optional.of(metadataFiles.get(0)), currentSrcDir.getName());
            if (!parser.isValidHeader()) {
                System.err.println("WARNING: Metadata header validation warning on file: " + metadataFiles.get(0).getAbsolutePath());
            }
        } else {
            System.err.println("WARNING: Expected exactly 1 metadata file in gallery path: " + currentSrcDir.getAbsolutePath() + " (Found: " + metadataFiles.size() + ")");
            parser.parse(Optional.empty(), currentSrcDir.getName());
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

                    if (!config.isCheckOnly()) {
                        // LAZY DIRECTORY CREATION: Create the gallery folder structure at target right before writing the first file
                        synchronized (this) {
                            if (!currentTgtDir.exists() && !currentTgtDir.mkdirs()) {
                                System.err.println("WARNING: Failed creating structure directory: " + currentTgtDir.getAbsolutePath());
                                return;
                            }
                        }

                        File outputImg = new File(currentTgtDir, img.getName());
                        if (doNotResizeMask.matches(img.getName())) {
                            ImageResizer.copyFileAndTransferExif(img, outputImg, config.isCopyExif());
                        } else {
                            ImageResizer.resizeImagePct(img, outputImg, config.getTargetImageResolutionPct(), config.isCopyExif());
                        }

                        synchronized (this) {
                            if (!previewDir.exists() && !previewDir.mkdirs()) {
                                System.err.println("WARNING: Previews directory creation failed: " + previewDir.getAbsolutePath());
                            }
                        }
                        File outputPreview = new File(previewDir, img.getName());
                        ImageResizer.resizeToMaxSide(img, outputPreview, config.getTargetPreviewMaxSidePx());
                    }

                    String matchedDescription = lookupDescription(img.getName(), parser.getImageDescriptions(), matchedIds);
                    String relativePreviewPath = config.getTargetPreviewDirName() + "/" + img.getName();

                    ImageEntry entry = new ImageEntry(img.getName(), relativePreviewPath, matchedDescription);
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

        synchronized (imagesWithoutDesc) {
            imagesWithoutDesc.sort(Comparator.comparing(ImageEntry::image));
        }
        synchronized (imagesWithDesc) {
            imagesWithDesc.sort(Comparator.comparing(ImageEntry::image));
        }

        List<ImageEntry> combinedImagesList = new ArrayList<>();
        combinedImagesList.addAll(imagesWithoutDesc);
        combinedImagesList.addAll(imagesWithDesc);
        if (!config.isCheckOnly()) {
            // Guarantee directory structure exists for pure metadata-only galleries if they didn't write any images
            if (!currentTgtDir.exists() && !currentTgtDir.mkdirs()) {
                System.err.println("WARNING: Failed creating structure directory for JSON metadata: " + currentTgtDir.getAbsolutePath());
            } else {
                writeGalleryJson(currentTgtDir, parser, noteContent, combinedImagesList, subdirectoriesWithImages);
            }
        }
        if (currentSrcDir.getParentFile().equals(baseSrcDir)) {
            trackRootEntry(currentSrcDir.getName(), parser, combinedImagesList);
        }
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
        String template = config.getMetadataIdToImageMapping().toLowerCase();
        String fileLower = filename.toLowerCase();
        int idMarkerIndex = template.indexOf("<id>");
        if (idMarkerIndex == -1) return null;
        String prefix = template.substring(0, idMarkerIndex);
        String suffix = template.substring(idMarkerIndex + 4);
        if (fileLower.startsWith(prefix) && fileLower.endsWith(suffix)) {
            int endIdx = fileLower.length() - suffix.length();
            if (endIdx >= prefix.length()) {
                return filename.substring(prefix.length(), endIdx);
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
                        System.err.println("WARNING: Missing description inside metadata record index definitions for active image target: " + img.getName() + " in directory: " + currentSrcDirPath);
                    }
                }
            }
        }
        validateNonExistentIds(imageFiles, parser, currentSrcDirPath);
    }

    private void validateNonExistentIds(List<File> imageFiles, MetadataParser parser, String currentSrcDirPath) {
        Map<String, String> declaredDescriptions = parser.getImageDescriptions();
        if (declaredDescriptions.isEmpty()) {
            return;
        }
        Set filesystemIdsLower = new HashSet<>();
        for (File img : imageFiles) {
            String fsId = lookupId(img.getName());
            if (fsId != null) {
                filesystemIdsLower.add(fsId.toLowerCase());
            }
        }
        for (String declaredId : declaredDescriptions.keySet()) {
            if (!filesystemIdsLower.contains(declaredId.toLowerCase())) {
                System.err.println(String.format("WARNING: Metadata file declares description for ID '%s', but no matching image file exists on the filesystem in directory: %s", declaredId, currentSrcDirPath));
            }
        }
    }

    private void writeGalleryJson(File currentTgtDir, MetadataParser parser, String noteContent, List combinedImagesList, List subdirectoriesWithImages) {
        GalleryIndex galleryIndex = new GalleryIndex(parser.getGalleryName(), parser.getDate(), parser.getEvent(), noteContent, combinedImagesList.isEmpty() ? null : combinedImagesList, subdirectoriesWithImages.isEmpty() ? null : subdirectoriesWithImages);
        File targetIndexJson = new File(currentTgtDir, config.getGalleryJsonName());
        try {
            mapper.writeValue(targetIndexJson, galleryIndex);
        } catch (IOException e) {
            System.err.println("WARNING: Failed writing to destination json layout: " + e.getMessage());
        }
    }

    private void trackRootEntry(String topLevelDirName, MetadataParser parser, List combinedImagesList) {
        String previewPathFallback = determineRootPreview(combinedImagesList, noCommentMask, topLevelDirName);
        RootEntry rootNode = new RootEntry(parser.getGalleryName(), parser.getDate(), topLevelDirName, config.getGalleryJsonName(), previewPathFallback);
        rootEntries.add(rootNode);
    }

    private String determineRootPreview(List<ImageEntry> images, MaskMatcher noCommentMask, String topLevelDir) {
        if (images == null || images.isEmpty()) {
            System.err.println("WARNING: Gallery directory is empty or has no images. Root tracking preview missing for: " + topLevelDir);
            return null;
        }
        for (ImageEntry entry : images) {
            if (!noCommentMask.matches(entry.image())) {
                return topLevelDir + "/" + entry.preview();
            }
        }
        System.err.println(String.format("WARNING: All images in %s match no_comments_files_mask. Using first available image for preview.", topLevelDir));
        return topLevelDir + "/" + images.get(0).preview();
    }

    private boolean hasMatchingImages(File dir, MaskMatcher imgMask, MaskMatcher excludeMask) {
        File[] content = dir.listFiles();
        if (content == null) return false;
        for (File f : content) {
            if (f.isDirectory()) {
                if (!excludeMask.matches(f.getName()) && hasMatchingImages(f, imgMask, excludeMask)) return true;
            } else if (imgMask.matches(f.getName())) {
                return true;
            }
        }
        return false;
    }
}
