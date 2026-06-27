package com.gallery.generator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonPropertyOrder({ "name", "date", "event", "note", "images", "subdirectories" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GalleryIndex(
        String name,
        String date,
        String event,
        String note,
        List<ImageEntry> images,
        List<SubdirectoryEntry> subdirectories
) {}


