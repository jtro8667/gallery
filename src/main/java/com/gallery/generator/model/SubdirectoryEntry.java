package com.gallery.generator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "directory", "gallery_json_name", "preview_path" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubdirectoryEntry(
        String directory,
        String gallery_json_name,
        String preview_path
) {}
