package com.hls.reporting.internal.report;

import com.hls.reporting.api.ReportRequest;
import com.hls.school.api.ChangeRecorder;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes one audit row for every report export: who, which report, the filter and the time (spec 014a FR-010). */
@Component
public class ReportExportAudit {

    static final String ENTITY_PREFIX = "REPORT_EXPORT_";

    private final ChangeRecorder changes;

    public ReportExportAudit(ChangeRecorder changes) {
        this.changes = changes;
    }

    /** Call inside the export's transaction so the row is written only if the file is made. */
    public void exported(UUID actor, String reportKey, ReportRequest request, String filterText, int rows) {
        changes.recordLifecycle(
                actor,
                ENTITY_PREFIX + reportKey.toUpperCase(java.util.Locale.ROOT),
                reportKey,
                "exported",
                "Month " + request.month() + ", filter: " + (filterText == null || filterText.isBlank() ? "none" : filterText) + ", "
                        + rows + " rows");
    }
}
