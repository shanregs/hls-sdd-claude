package com.hls.activitycalendar.internal;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Whether a source exists and was read; {@code note} explains an unavailable or failed source. */
public record SourceReport(String group, boolean available, boolean read, @JsonInclude(JsonInclude.Include.NON_NULL) String note) {}
