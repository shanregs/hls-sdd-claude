package com.hls.training.calendar.internal;

import com.hls.training.api.TrainingSessions;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sessions a Training Stay claim can be linked to (spec 024 T-015). */
@Service("trainingSessionsImpl")
@Transactional(readOnly = true)
public class TrainingSessionsImpl implements TrainingSessions {

    private final TrainingSessionRepository sessions;

    TrainingSessionsImpl(TrainingSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public List<SessionView> between(LocalDate from, LocalDate to) {
        return sessions
                .findByStatusInAndSessionDateBetweenOrderBySessionDateAscStartsAtAsc(
                        List.of(TrainingSession.PUBLISHED, TrainingSession.HELD), from, to)
                .stream()
                .map(TrainingSessionsImpl::view)
                .toList();
    }

    @Override
    public Optional<SessionView> session(UUID id) {
        return sessions.findById(id)
                .filter(s -> TrainingSession.PUBLISHED.equals(s.status) || TrainingSession.HELD.equals(s.status))
                .map(TrainingSessionsImpl::view);
    }

    private static SessionView view(TrainingSession s) {
        return new SessionView(s.id, s.topic, s.sessionDate, s.sessionType);
    }
}
