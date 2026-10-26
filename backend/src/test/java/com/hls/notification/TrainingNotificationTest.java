package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.notification.internal.MessageFactory;
import com.hls.support.MasterDataTestBase;
import com.hls.training.api.TrainingAssigned;
import com.hls.training.api.TrainingAssignmentRemoved;
import com.hls.training.api.TrainingCannotAttend;
import com.hls.training.api.TrainingExceptionRaised;
import com.hls.training.api.TrainingRegistered;
import com.hls.training.api.TrainingReminderDue;
import com.hls.training.api.TrainingSessionChanged;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Spec 024 T012: the texts of the seven training notifications and who receives each. */
class TrainingNotificationTest extends MasterDataTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 14);

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactions;

    private void publish(Object event) {
        new TransactionTemplate(transactions).executeWithoutResult(s -> publisher.publishEvent(event));
    }

    private int count(UUID user, String type) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and type = ?", Integer.class, user, type);
    }

    @Test
    void textsNameTheTopicAndTheDateAndLinkToMyTraining() {
        var assigned = MessageFactory.trainingAssigned("Phonics", DAY);
        assertThat(assigned.message()).isEqualTo("You are required at the training \"Phonics\" on 14/11/2026.");
        assertThat(assigned.link()).isEqualTo("/training/mine");
        assertThat(MessageFactory.trainingMoved("Phonics", DAY, DAY.plusDays(7)).message())
                .isEqualTo("The training \"Phonics\" moved from 14/11/2026 to 21/11/2026.");
        assertThat(MessageFactory.trainingCancelled("Phonics", DAY).title()).isEqualTo("A training session was cancelled");
        assertThat(MessageFactory.trainingReminder("Phonics", DAY, 1).message()).isEqualTo("The training \"Phonics\" is tomorrow.");
        assertThat(MessageFactory.trainingReminder("Phonics", DAY, 2).message()).contains("in 2 days (14/11/2026)");
        assertThat(MessageFactory.trainingRegistered("Phonics", DAY).title()).isEqualTo("Your training registration is confirmed");
        UUID session = UUID.randomUUID();
        assertThat(MessageFactory.trainingException("Asha", DAY, session).link())
                .isEqualTo("/operations/training/exceptions?session=" + session);
        assertThat(MessageFactory.trainingCannotAttend("Asha", "Family event", session).message())
                .isEqualTo("Asha says they cannot attend: Family event.");
    }

    @Test
    void assignedRemovedMovedCancelledReminderAndRegisteredGoToThePeopleNamed() {
        UUID a = signInAs(Role.MANAGER).userId();
        UUID b = signInAs(Role.TEACHER).userId();
        UUID session = UUID.randomUUID();

        publish(new TrainingAssigned(session, Set.of(a, b), DAY, "Phonics"));
        publish(new TrainingAssignmentRemoved(session, Set.of(b), DAY, "Phonics"));
        publish(new TrainingSessionChanged(session, "MOVED", Set.of(a, b), DAY, DAY.plusDays(7), "Phonics"));
        publish(new TrainingSessionChanged(session, "CANCELLED", Set.of(a), DAY, null, "Phonics"));
        publish(new TrainingReminderDue(session, b, 2, DAY, "Phonics"));
        publish(new TrainingRegistered(session, b, DAY, "Phonics"));

        assertThat(count(a, "TRAINING_ASSIGNED")).isEqualTo(1);
        assertThat(count(b, "TRAINING_ASSIGNED")).isEqualTo(1);
        assertThat(count(a, "TRAINING_ASSIGNMENT_REMOVED")).isZero();
        assertThat(count(b, "TRAINING_ASSIGNMENT_REMOVED")).isEqualTo(1);
        assertThat(count(a, "TRAINING_CHANGED")).isEqualTo(2);
        assertThat(count(b, "TRAINING_CHANGED")).isEqualTo(1);
        assertThat(count(a, "TRAINING_REMINDER")).isZero();
        assertThat(count(b, "TRAINING_REMINDER")).isEqualTo(1);
        assertThat(count(b, "TRAINING_REGISTERED")).isEqualTo(1);
    }

    @Test
    void exceptionsAndCannotAttendNotesGoToAdminAndDirectorOnly() {
        UUID admin = signInAs(Role.ADMIN).userId();
        UUID director = signInAs(Role.DIRECTOR).userId();
        UUID manager = signInAs(Role.MANAGER).userId();
        UUID session = UUID.randomUUID();

        publish(new TrainingExceptionRaised(session, "Asha", DAY));
        publish(new TrainingCannotAttend(session, "Asha", "Family event"));

        for (UUID user : new UUID[] {admin, director}) {
            assertThat(count(user, "TRAINING_EXCEPTION")).isEqualTo(1);
            assertThat(count(user, "TRAINING_CANNOT_ATTEND")).isEqualTo(1);
        }
        assertThat(count(manager, "TRAINING_EXCEPTION")).isZero();
        assertThat(count(manager, "TRAINING_CANNOT_ATTEND")).isZero();
    }
}
