package com.hls.reporting.api;

import java.util.Map;

/** Where a figure leads: a front-end path and the query that applies the matching filter. */
public record Link(String path, Map<String, String> query) {

    public Link {
        query = query == null ? Map.of() : Map.copyOf(query);
    }

    public static Link to(String path) {
        return new Link(path, Map.of());
    }

    public static Link to(String path, String key, String value) {
        return new Link(path, Map.of(key, value));
    }
}
