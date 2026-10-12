package com.hls.audit.support;

import com.hls.audit.api.RecentAudit;
import com.hls.audit.changehistory.ChangeHistoryEntryRepository;
import com.hls.audit.loginhistory.LoginHistoryEntry;
import com.hls.audit.loginhistory.LoginHistoryEntryRepository;
import com.hls.identity.user.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Merges the newest Change History and Login History rows for a dashboard (spec 011). */
@Component
class RecentAuditImpl implements RecentAudit {

    /** Change rows read before filtering by what the caller may see, so hidden rows do not empty the list. */
    private static final int OVERFETCH = 5;

    private final ChangeHistoryEntryRepository changes;
    private final LoginHistoryEntryRepository logins;
    private final AuditVisibility visibility;

    RecentAuditImpl(ChangeHistoryEntryRepository changes, LoginHistoryEntryRepository logins, AuditVisibility visibility) {
        this.changes = changes;
        this.logins = logins;
        this.visibility = visibility;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> recent(Set<Role> callerRoles, int limit) {
        Set<String> hidden = visibility.hiddenEntityTypes(callerRoles);
        Sort newest = Sort.by(Sort.Direction.DESC, "occurredAt");
        List<Event> events = new ArrayList<>();
        changes.findAll(PageRequest.of(0, limit * OVERFETCH, newest)).stream()
                .filter(c -> !hidden.contains(c.getEntityType()))
                .limit(limit)
                .forEach(c -> events.add(new Event(
                        "CHANGE",
                        c.getOccurredAt(),
                        c.getActorUserId(),
                        c.getEntityType() + " " + c.getEntityId() + " " + c.getField() + ": "
                                + shown(c.getBeforeValue()) + " -> " + shown(c.getAfterValue()))));
        logins.findAll(PageRequest.of(0, limit, newest))
                .forEach(l -> events.add(new Event("LOGIN", l.getOccurredAt(), l.getUserId(), describe(l))));
        events.sort(Comparator.comparing(Event::at).reversed());
        return events.size() > limit ? List.copyOf(events.subList(0, limit)) : List.copyOf(events);
    }

    @Override
    @Transactional(readOnly = true)
    public long failedSignInsSince(Instant since) {
        return logins.countByEventTypeAndOccurredAtAfter("SIGN_IN_FAILURE", since);
    }

    private static String describe(LoginHistoryEntry l) {
        return l.getEventType().replace('_', ' ').toLowerCase() + " (" + l.getMethod().toLowerCase() + ")";
    }

    private static String shown(String value) {
        return value == null ? "(none)" : value;
    }
}
