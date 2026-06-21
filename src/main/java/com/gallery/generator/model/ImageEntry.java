package com.gallery.generator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "image", "preview", "description" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageEntry(
    String image,
    String preview,
    String description
) {}

