package com.hls.identity.accessmodel;

import java.util.List;
import java.util.Map;

/** Response shapes for {@code contracts/access-model-api.md}'s {@code GET /api/v1/me/access-model}. */
public final class AccessModelDtos {

    private AccessModelDtos() {}

    public record AccessModelResponse(List<String> roles, List<NavSection> navigation, Map<String, String> dataScope) {}

    public record NavSection(String section, List<NavItemView> items) {}

    public record NavItemView(String label, String route, List<String> actions) {}
}
