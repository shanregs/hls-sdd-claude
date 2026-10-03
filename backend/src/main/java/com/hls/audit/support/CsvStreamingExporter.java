package com.hls.audit.support;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Streams a paginated query's results to the response as CSV without ever holding the full result
 * set in memory (research.md §5), shared by all four audit export endpoints (FR-009).
 */
public final class CsvStreamingExporter {

    private static final int PAGE_SIZE = 500;

    private CsvStreamingExporter() {}

    public static <T> void stream(
            HttpServletResponse response,
            String filename,
            List<String> header,
            Function<Pageable, Page<T>> pageFetcher,
            Function<T, List<String>> rowMapper)
            throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try (PrintWriter writer = response.getWriter()) {
            writer.println(toCsvLine(header));
            int pageNumber = 0;
            Page<T> page;
            do {
                page = pageFetcher.apply(PageRequest.of(pageNumber, PAGE_SIZE));
                for (T item : page) {
                    writer.println(toCsvLine(rowMapper.apply(item)));
                }
                writer.flush();
                pageNumber++;
            } while (page.hasNext());
        }
    }

    private static String toCsvLine(List<String> fields) {
        return fields.stream().map(CsvStreamingExporter::escape).collect(Collectors.joining(","));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
