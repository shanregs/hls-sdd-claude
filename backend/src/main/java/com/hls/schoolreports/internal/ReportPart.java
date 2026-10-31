package com.hls.schoolreports.internal;

import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportPage.Column;
import com.hls.reporting.api.ReportPage.Section;
import com.hls.reporting.api.ReportSource.SectionSource;
import java.util.List;
import java.util.function.Function;

/** One section of a School report, read by a function of the request. */
record ReportPart(String key, String title, List<Column> columns, Function<ReportContext, Section> reader) implements SectionSource {

    @Override
    public Section read(ReportContext ctx) {
        return reader.apply(ctx);
    }
}
