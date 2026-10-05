package com.hls.attendance.internal;

import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Attendance status codes (spec 008 FR-007): four seeded defaults plus codes an Admin or Director adds. */
@Service
public class StatusCodeService {

    public record StatusCodeView(
            UUID id,
            String shortCode,
            String name,
            String category,
            BigDecimal weight,
            boolean active,
            boolean system,
            boolean inUse,
            Long version) {}

    private final StatusCodeRepository codes;
    private final StatusCodeCatalog catalog;
    private final AttendanceMarkRepository marks;
    private final AttendanceAudit audit;

    public StatusCodeService(
            StatusCodeRepository codes, StatusCodeCatalog catalog, AttendanceMarkRepository marks, AttendanceAudit audit) {
        this.codes = codes;
        this.catalog = catalog;
        this.marks = marks;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<StatusCodeView> list(boolean activeOnly) {
        List<StatusCode> found = activeOnly ? codes.findByActiveTrueOrderBySortOrder() : codes.findAllByOrderBySortOrder();
        return found.stream().map(this::view).toList();
    }

    @Transactional
    public StatusCodeView create(UUID actor, String shortCode, String name, String category, BigDecimal weight) {
        String code = requireText(shortCode, "The short code is required.", 8, "The short code can be at most 8 characters.");
        String label = requireText(name, "The name is required.", 60, "The name can be at most 60 characters.");
        StatusCategory parsed = parseCategory(category);
        BigDecimal validWeight = requireWeight(weight);
        if (codes.findByShortCodeIgnoreCase(code).isPresent()) {
            throw new ConflictException("A status code with the short code " + code + " already exists.");
        }
        StatusCode saved = codes.save(new StatusCode(code, label, parsed, validWeight, codes.maxSortOrder() + 1));
        catalog.changed();
        audit.lifecycle(actor, AttendanceAudit.CODE, saved.getId(), "created", code + " " + label);
        return view(saved);
    }

    @Transactional
    public StatusCodeView update(UUID actor, UUID id, String name, BigDecimal weight, Boolean active, Long version) {
        StatusCode code = codes.findById(id).orElseThrow(() -> new NotFoundException("Status code not found."));
        StaleVersion.check(StatusCode.class, id, code.getVersion(), version);
        String label = requireText(name, "The name is required.", 60, "The name can be at most 60 characters.");
        BigDecimal validWeight = requireWeight(weight);
        boolean nextActive = active == null ? code.isActive() : active;
        if (code.isSystem() && !nextActive) {
            throw new ConflictException("A built-in status code cannot be deactivated.");
        }
        audit.changed(actor, AttendanceAudit.CODE, id, "name", code.getName(), label);
        audit.changed(actor, AttendanceAudit.CODE, id, "weight", code.getWeight().toPlainString(), validWeight.toPlainString());
        audit.changed(actor, AttendanceAudit.CODE, id, "active", code.isActive(), nextActive);
        code.setName(label);
        code.setWeight(validWeight);
        code.setActive(nextActive);
        StatusCode saved = codes.saveAndFlush(code);
        catalog.changed();
        return view(saved);
    }

    StatusCode require(UUID id) {
        return catalog.find(id).orElseThrow(() -> new NotFoundException("Status code not found."));
    }

    StatusCode requireByShortCode(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            throw new InvalidInputException("Choose a status.");
        }
        return catalog.findByShortCode(shortCode.trim())
                .orElseThrow(() -> new InvalidInputException("Unknown status " + shortCode + "."));
    }

    private StatusCodeView view(StatusCode c) {
        return new StatusCodeView(
                c.getId(),
                c.getShortCode(),
                c.getName(),
                c.getCategory().name(),
                c.getWeight(),
                c.isActive(),
                c.isSystem(),
                marks.existsByStatusCodeId(c.getId()),
                c.getVersion());
    }

    private static String requireText(String value, String missing, int max, String tooLong) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(missing);
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new InvalidInputException(tooLong);
        }
        return trimmed;
    }

    private static StatusCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new InvalidInputException("The category is required.");
        }
        try {
            return StatusCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The category must be WORKED, LEAVE, TRAINING or NON_WORKING.");
        }
    }

    private static BigDecimal requireWeight(BigDecimal weight) {
        if (weight == null) {
            throw new InvalidInputException("The weight is required.");
        }
        if (weight.signum() < 0 || weight.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidInputException("The weight must be between 0 and 1.");
        }
        return weight.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
