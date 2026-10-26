package com.hls.training.calendar.internal;

import com.hls.training.calendar.internal.SessionQueries.SessionView;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Upcoming Training" (spec 024 US7): the sessions the caller is Required at and, for a Teacher, the open weekend sessions
 * with a free seat that have not started. A Manager sees only Required.
 */
@Service("trainingUpcomingService")
public class UpcomingService {

    private static final int MONTHS_AHEAD = 3;

    /** {@code label} is REQUIRED, REGISTERED (a Teacher's own registration, so it can be cancelled) or OPEN. */
    public record Upcoming(String label, SessionView session) {}

    private final SessionQueries queries;
    private final TrainingClock clock;

    UpcomingService(SessionQueries queries, TrainingClock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Upcoming> mine(Caller caller) {
        LocalDate today = clock.today();
        return queries.list(caller, today, today.plusMonths(MONTHS_AHEAD), null, "PUBLISHED", null, null).stream()
                .map(s -> {
                    if (s.my() != null && "REQUIRED".equals(s.my().status())) {
                        return new Upcoming("REQUIRED", s);
                    }
                    if (s.my() != null && "REGISTERED".equals(s.my().status())) {
                        return new Upcoming("REGISTERED", s);
                    }
                    if (caller.isTeacher() && s.my() != null && s.my().open() && s.my().status() == null) {
                        return new Upcoming("OPEN", s);
                    }
                    return null;
                })
                .filter(u -> u != null)
                .toList();
    }
}
