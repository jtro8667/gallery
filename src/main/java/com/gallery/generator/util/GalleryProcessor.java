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

        // Fix: Trigger processing if there are images, metadata, or active subdirectories,
        // BUT strictly forbid generating a local gallery index.json for the root baseSrcDir itself.
        boolean isRootBaseDirectory = currentSrcDir.equals(baseSrcDir);

        if (!isRootBaseDirectory && (!imageFiles.isEmpty() || !metadataFiles.isEmpty() || !subdirectoriesWithImages.isEmpty())) {
            logProgress(String.format("Processing directory %s: %d images found, %d active subdirectories.",
                    currentSrcDir.getName(), imageFiles.size(), subdirectoriesWithImages.size()));
            processGalleryContents(currentSrcDir, currentTgtDir, baseSrcDir, imageFiles, metadataFiles, additionalFile, subdirectoriesWithImages);
        }

        // Continue deep tree traversal for subdirectories (this must always run from root to discover galleries)
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
            // Fix: If local metadata file is absent, attempt to extract fallback name from deep active nested subdirectories first
            String deepFallbackName = findFirstDeepMetadataName(currentSrcDir);
            if (deepFallbackName == null) {
                deepFallbackName = currentSrcDir.getName();
            }

            if (metadataFiles.size() > 1) {
                System.err.println("WARNING: Expected exactly 1 metadata file in gallery path: " + currentSrcDir.getAbsolutePath() + " (Found: " + metadataFiles.size() + ")");
            }
            parser.parse(Optional.empty(), deepFallbackName);
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
                        String nameWithoutExt = baseName.substring(0, dotIndex);
                        String extLower = baseName.substring(dotIndex).toLowerCase();
                        targetFilename = nameWithoutExt + extLower;
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
                            ImageResizer.resizeImagePct(img, outputFile, config.getTargetImageResolutionPct(), config.isCopyExif());
                        }

                        synchronized (this) {
                            if (!previewDir.exists() && !previewDir.mkdirs()) {
                                System.err.println("WARNING: Previews directory creation failed: " + previewDir.getAbsolutePath());
                            }
                        }
                        File outputPreview = new File(previewDir, targetFilename);
                        ImageResizer.resizeToMaxSide(img, outputPreview, config.getTargetPreviewMaxSidePx());
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

        if (!config.isCheckOnly()) {
            // Fix: Guarantee structure directory is always generated even for container-only galleries containing subfolders
            if (!currentTgtDir.exists() && !currentTgtDir.mkdirs()) {
                System.err.println("WARNING: Failed creating structure directory for JSON metadata container: " + currentTgtDir.getAbsolutePath());
            } else {
                writeGalleryJson(currentTgtDir, parser, noteContent, combinedImagesList, subdirectoriesWithImages);
            }
        }

        if (currentSrcDir.getParentFile().equals(baseSrcDir)) {
            trackRootEntry(currentSrcDir.getName(), parser, combinedImagesList);
        }
    }

    /**
     * Recursively traverses subdirectories to extract the gallery name from the very first metadata file it encounters.
     * Returns null if no deeper metadata properties are available.
     */
    private String findFirstDeepMetadataName(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return null;

        // Step 1: Scan files in the current child scope for an immediate metadata match
        for (File child : children) {
            if (!child.isDirectory() && metaMask.matches(child.getName())) {
                MetadataParser tempParser = new MetadataParser();
                tempParser.parse(Optional.of(child), dir.getName());
                String derivedName = tempParser.getGalleryName();
                if (derivedName != null && !derivedName.isBlank()) {
                    return derivedName;
                }
            }
        }

        // Step 2: Traverse deeper into subdirectories recursively if no local file was discovered
        for (File child : children) {
            if (child.isDirectory() && !excludeSubMask.matches(child.getName())) {
                String deepName = findFirstDeepMetadataName(child);
                if (deepName != null) {
                    return deepName;
                }
            }
        }
        return null;
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

        if (mappingConfig == null || mappingConfig.isBlank()) {
            return null;
        }

        // Fix: Split the configuration string by comma to support multiple patterns properly
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
                    // Extract exactly the original ID token block bounds from the filename
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
                System.err.println(String.format("WARNING: Directory: %s - metadata declares description for ID '%s', but no matching image file exists.", currentSrcDirPath, declaredId));
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
        if (images != null && !images.isEmpty()) {
            for (ImageEntry entry : images) {
                if (!noCommentMask.matches(entry.image())) {
                    return topLevelDir + "/" + entry.preview();
                }
            }
            System.err.println(String.format("WARNING: All images in %s match no_comments_files_mask. Using first available image for preview.", topLevelDir));
            return topLevelDir + "/" + images.get(0).preview();
        }

        // Fix: If the top-level gallery is a container with no direct images, search recursively in subdirectories
        String deepPreviewPath = findFirstDeepPreview(new File(config.getSourceDir(), topLevelDir), noCommentMask);
        if (deepPreviewPath != null) {
            return topLevelDir + "/" + deepPreviewPath;
        }

        System.err.println("WARNING: Gallery directory and its subdirectories are empty or have no images. Root tracking preview missing for: " + topLevelDir);
        return null;
    }

    /**
     * Recursively traverses subdirectories to find the first available preview image from the first active subfolder.
     * Respects filtering masks and builds the proper relative path segment.
     */
    private String findFirstDeepPreview(File srcDir, MaskMatcher noCommentMask) {
        File[] allFiles = srcDir.listFiles();
        if (allFiles == null) return null;

        List<File> localImages = new ArrayList<>();
        List<File> subDirs = new ArrayList<>();

        for (File file : allFiles) {
            if (file.isDirectory()) {
                if (!excludeSubMask.matches(file.getName())) {
                    subDirs.add(file);
                }
            } else if (imageMask.matches(file.getName())) {
                localImages.add(file);
            }
        }

        // Sort both collections alphabetically to maintain deterministic structural scanning
        localImages.sort(Comparator.comparing(File::getName));
        subDirs.sort(Comparator.comparing(File::getName));

        // If current directory contains valid image assets, extract the preferred preview entry
        if (!localImages.isEmpty()) {
            String selectedImage = null;
            // Strategy 1: Attempt to grab the first file that does not match the comments exclusion mask
            for (File img : localImages) {
                if (!noCommentMask.matches(img.getName())) {
                    selectedImage = img.getName();
                    break;
                }
            }
            // Strategy 2: Fall back to the absolute first file if everything was filtered out by comments mask
            if (selectedImage == null) {
                selectedImage = localImages.get(0).getName();
            }

            // Enforce lowercased extension alignment rules for the final generated preview reference
            int dotIdx = selectedImage.lastIndexOf('.');
            if (dotIdx != -1) {
                selectedImage = selectedImage.substring(0, dotIdx) + selectedImage.substring(dotIdx).toLowerCase();
            }

            return config.getTargetPreviewDirName() + "/" + selectedImage;
        }

        // If current directory is empty of images, dive deeper into subdirectories sequentially
        for (File subDir : subDirs) {
            String deepPath = findFirstDeepPreview(subDir, noCommentMask);
            if (deepPath != null) {
                return subDir.getName() + "/" + deepPath;
            }
        }

        return null;
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
