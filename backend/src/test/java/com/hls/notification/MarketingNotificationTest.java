package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.recruitment.api.WonProspectOverdue;
import com.hls.support.MasterDataTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/** Spec 023: the overdue-MoU event reaches the owner, the Zone Manager of the Zone, and Admin and Director, once each. */
class MarketingNotificationTest extends MasterDataTestBase {

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate tx;

    private int countFor(UUID user) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and type = 'MOU_NOT_RECORDED'", Integer.class, user);
    }

    @Test
    void theOwnerTheZoneManagerAndAdminAndDirectorAreToldOnceEach() {
        Signed admin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        Signed owner = signInAs(Role.MANAGER);
        UUID zone = zone(admin.token());
        ManagerCtx zoneManager = newManager(admin.token(), zone);
        UUID prospect = UUID.randomUUID();

        tx.executeWithoutResult(s -> publisher.publishEvent(new WonProspectOverdue(prospect, "Green Valley School", owner.userId(), zone, 14)));

        assertThat(countFor(owner.userId())).isEqualTo(1);
        assertThat(countFor(zoneManager.signed().userId())).isEqualTo(1);
        assertThat(countFor(admin.userId())).isEqualTo(1);
        assertThat(countFor(director.userId())).isEqualTo(1);
        String link = jdbc.queryForObject(
                "select link from notification where recipient_user_id = ? and type = 'MOU_NOT_RECORDED'", String.class, owner.userId());
        assertThat(link).isEqualTo("/marketing/prospects/" + prospect);
        String message = jdbc.queryForObject(
                "select message from notification where recipient_user_id = ? and type = 'MOU_NOT_RECORDED'", String.class, owner.userId());
        assertThat(message).contains("Green Valley School", "14 days");
    }

    @Test
    void anOwnerWhoIsAlsoTheZoneManagerIsToldOnlyOnce() {
        Signed admin = signInAs(Role.ADMIN);
        UUID zone = zone(admin.token());
        ManagerCtx zoneManager = newManager(admin.token(), zone);

        tx.executeWithoutResult(s -> publisher.publishEvent(
                new WonProspectOverdue(UUID.randomUUID(), "Sunrise School", zoneManager.signed().userId(), zone, 20)));

        assertThat(countFor(zoneManager.signed().userId())).isEqualTo(1);
    }
}
