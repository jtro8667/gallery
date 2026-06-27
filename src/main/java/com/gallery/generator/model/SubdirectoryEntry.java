package com.gallery.generator.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "directory", "gallery_json_name" })
public record SubdirectoryEntry(
        String directory,
        String gallery_json_name
) {}
