package com.hls.attendance.workingdays.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hls.attendance.workingdays.internal.WorkingDaysConflicts.Duplicate;
import com.hls.attendance.workingdays.internal.WorkingDaysConflicts.RowDeleted;
import com.hls.attendance.workingdays.internal.WorkingDaysConflicts.Stale;
import com.hls.attendance.workingdays.internal.WorkingDaysViewFactory;
import com.hls.attendance.workingdays.internal.WorkingDaysViewFactory.RowView;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The three refusals that carry a code and a row (contracts/school-working-days-api.md). */
@RestControllerAdvice(basePackageClasses = SchoolWorkingDaysController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkingDaysExceptionAdvice {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WithRow(String code, String reason, RowView existing, RowView current) {}

    public record CodeOnly(String code, String reason) {}

    private final WorkingDaysViewFactory views;

    public WorkingDaysExceptionAdvice(WorkingDaysViewFactory views) {
        this.views = views;
    }

    @ExceptionHandler(Duplicate.class)
    ResponseEntity<WithRow> duplicate(Duplicate e) {
        return ResponseEntity.status(409)
                .body(new WithRow("DUPLICATE", e.getMessage(), views.view(e.existing()), null));
    }

    @ExceptionHandler(Stale.class)
    ResponseEntity<WithRow> stale(Stale e) {
        return ResponseEntity.status(409)
                .body(new WithRow("STALE", e.getMessage(), null, views.view(e.current())));
    }

    @ExceptionHandler(RowDeleted.class)
    ResponseEntity<CodeOnly> rowDeleted(RowDeleted e) {
        return ResponseEntity.status(404).body(new CodeOnly("ROW_DELETED", e.getMessage()));
    }
}
