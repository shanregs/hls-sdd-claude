package com.hls.content.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.Value;
import com.hls.reporting.api.TileSource;
import java.util.List;
import org.springframework.stereotype.Component;

/** The four Content tiles of the management Dashboard (spec 029 FR-014): submitted, awaiting review, approved and overdue. */
@Component
class ContentTiles implements TileSource {

    private static final String MODULE = "CONTENT";

    private final ContentQueries queries;

    ContentTiles(ContentQueries queries) {
        this.queries = queries;
    }

    private static TileDefinition def(String suffix, int order, String label, Link link) {
        return new TileDefinition(
                "content." + suffix, Area.CONTENT, order, label, PeriodKind.TODAY, MODULE, PermissionAction.VIEW,
                ScopeKind.ALL, false, link);
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(
                def("submitted", 1, "Content submitted", Link.to("/operations/content", "status", "SUBMITTED")),
                def("awaiting", 2, "Content awaiting review", Link.to("/operations/content", "status", "SUBMITTED")),
                def("approved", 3, "Content approved", Link.to("/operations/content", "status", "APPROVED")),
                def("overdue", 4, "Content overdue", Link.to("/operations/content")));
    }

    @Override
    public TileResult read(TileDefinition tile, TileContext ctx) {
        ContentQueries.Summary s = ctx.memo("content.summary", () -> queries.summary(new ContentService.Caller(ctx.userId(), ctx.roles())));
        long n =
                switch (tile.key()) {
                    case "content.submitted" -> s.submitted() + s.approved() + s.returned();
                    case "content.awaiting" -> s.submitted();
                    case "content.approved" -> s.approved();
                    case "content.overdue" -> s.overdue();
                    default -> throw new IllegalArgumentException("Unknown tile " + tile.key());
                };
        return TileResult.ok(tile.key(), Value.count(tile.label(), n, tile.link()));
    }
}
