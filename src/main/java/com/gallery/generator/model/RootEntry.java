package com.gallery.generator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "name", "date", "directory", "gallery_json_name", "preview_path" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RootEntry(
    String name,
    String date,
    String directory,
    String gallery_json_name,
    String preview_path
) {}

