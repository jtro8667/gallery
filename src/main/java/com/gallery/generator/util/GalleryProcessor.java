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
import java.util.*;

/**
 * Orchestrates the recursive gallery processing, resizing, and data collection.
 */
public class GalleryProcessor {
    private final AppConfig config;
    private final ObjectMapper mapper;
    private final List<RootEntry> rootEntries = new ArrayList<>();

    private final MaskMatcher excludeSubMask;
    private final MaskMatcher imageMask;
    private final MaskMatcher metaMask;
    private final MaskMatcher additionalFileMask;
    private final MaskMatcher noCommentMask;

    public GalleryProcessor(AppConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;

        this.excludeSubMask = new MaskMatcher(config.getExcludedSubdirectoriesMask());
        this.imageMask = new MaskMatcher(config.getFilesToProcessMask());
        this.metaMask = new MaskMatcher(config.getMetadataFileMask());
        this.additionalFileMask = new MaskMatcher(config.getAdditionalFileMask());
        this.noCommentMask = new MaskMatcher(config.getNoCommentsFilesMask());
    }

    public List<RootEntry> getRootEntries() {
        return rootEntries;
    }

    public void processDirectory(File currentSrcDir, File baseSrcDir, File baseTgtDir) {
        if (excludeSubMask.matches(currentSrcDir.getName())) {
            System.out.println("Excluding " + currentSrcDir.getAbsolutePath());
            return;
        }

        String relativePath = baseSrcDir.toURI().relativize(currentSrcDir.toURI()).getPath();
        File currentTgtDir = new File(baseTgtDir, relativePath);

        if (!config.isCheckOnly() && !currentTgtDir.exists() && !currentTgtDir.mkdirs()) {
            System.err.println("WARNING: Failed creating matching structure directory: " + currentTgtDir.getAbsolutePath());
            return;
        }

        File[] allFiles = currentSrcDir.listFiles();
        if (allFiles == null) return;

        List<File> imageFiles = new ArrayList<>();
        List<File> metadataFiles = new ArrayList<>();
        File additionalFile = null;
        List<String> subdirectoriesWithImages = new ArrayList<>();

        // Categorize file entities
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
                } else if (additionalFileMask.matches(name)) {
                    additionalFile = file;
                } else if (name.toLowerCase().endsWith(".txt")) {
                    System.err.println("WARNING: Unexpected text file found: " + file.getAbsolutePath());
                }
            }
        }

        if (!imageFiles.isEmpty() || !metadataFiles.isEmpty()) {
            processGalleryContents(currentSrcDir, currentTgtDir, baseSrcDir, imageFiles, metadataFiles, additionalFile, subdirectoriesWithImages);
        }

        // Continue deep iteration tree traversal
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

        List<ImageEntry> imagesWithoutDesc = new ArrayList<>();
        List<ImageEntry> imagesWithDesc = new ArrayList<>();
        Set<String> matchedIds = new HashSet<>();
        File previewDir = new File(currentTgtDir, config.getTargetPreviewDirName());

        for (File img : imageFiles) {
            try {
                if (!config.isCheckOnly()) {
                    File outputImg = new File(currentTgtDir, img.getName());
                    ImageResizer.resizeImage(img, outputImg, config.getTargetImageResolutionPct());

                    if (!previewDir.exists() && !previewDir.mkdirs()) {
                        System.err.println("WARNING: Previews directory creation failed: " + previewDir.getAbsolutePath());
                    }
                    File outputPreview = new File(previewDir, img.getName());
                    ImageResizer.resizeImage(img, outputPreview, config.getTargetPreviewResolutionPct());
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
        }

        validateMissingDescriptions(imageFiles, parser, currentSrcDir.getAbsolutePath());

        imagesWithoutDesc.sort(Comparator.comparing(ImageEntry::image));
        imagesWithDesc.sort(Comparator.comparing(ImageEntry::image));

        List<ImageEntry> combinedImagesList = new ArrayList<>();
        combinedImagesList.addAll(imagesWithoutDesc);
        combinedImagesList.addAll(imagesWithDesc);

        if (!config.isCheckOnly()) {
            writeGalleryJson(currentTgtDir, parser, noteContent, combinedImagesList, subdirectoriesWithImages);
        }

        if (currentSrcDir.getParentFile().equals(baseSrcDir)) {
            trackRootEntry(currentSrcDir.getName(), parser, combinedImagesList);
        }
    }

    private String lookupDescription(String filename, Map<String, String> descriptions, Set<String> matchedIds) {
        String id = lookupId(filename);
        if (id != null && descriptions.containsKey(id)) {
            matchedIds.add(id);
            return descriptions.get(id);
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
                    if (checkId == null || !parser.getImageDescriptions().containsKey(checkId)) {
                        System.err.println("WARNING: Missing description inside metadata record index definitions for active image target: " + img.getName() + " in directory: " + currentSrcDirPath);
                    }
                }
            }
        }
    }

    private void writeGalleryJson(File currentTgtDir, MetadataParser parser, String noteContent,
                                  List<ImageEntry> combinedImagesList, List<String> subdirectoriesWithImages) {
        GalleryIndex galleryIndex = new GalleryIndex(
                parser.getGalleryName(),
                parser.getDate(),
                parser.getEvent(),
                noteContent,
                combinedImagesList.isEmpty() ? null : combinedImagesList,
                subdirectoriesWithImages.isEmpty() ? null : subdirectoriesWithImages
        );

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

    private static String determineRootPreview(List<ImageEntry> images, MaskMatcher noCommentMask, String topLevelDir) {
        if (images == null || images.isEmpty()) {
            System.err.println("WARNING: Gallery directory is empty or has no images. Root tracking preview missing for: " + topLevelDir);
            return null;
        }

        for (ImageEntry entry : images) {
            if (!noCommentMask.matches(entry.image())) {
                return topLevelDir + "/" + entry.preview();
            }
        }

        System.err.println(String.format("WARNING: All images in %s match no_comments_files_mask. Using first available image  for preview.", topLevelDir));
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
