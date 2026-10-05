package com.hls.recruitment.marketing.internal;

import com.hls.identity.user.Role;
import com.hls.recruitment.api.ProspectWon;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ContactDetails;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.SchoolContacts;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolRegistry;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * After the Final Stage review approved a prospect, Admin or Director creates its School (or links an existing one) and
 * is handed to the MoU form of spec 012. The School is created only now, never earlier, and the contract itself is
 * recorded only in spec 012. A Zone Manager can approve a prospect but cannot create the School or record the MoU.
 */
@Service
public class WinService {

    public record WinRequest(
            UUID placeId, String billingContact, UUID linkSchoolId, ContactDetails principal, ContactDetails accountant) {}

    public record Handoff(UUID schoolId, String path, ProposalService.ProposalDto proposal, boolean available) {}

    public record WinResult(ProspectService.ProspectDto prospect, Handoff handoff) {}

    private final ProspectRepository prospects;
    private final ProspectService prospectService;
    private final ProposalService proposals;
    private final SchoolRegistry registry;
    private final SchoolDirectory schools;
    private final ObjectProvider<SchoolContacts> contacts;
    private final ObjectProvider<com.hls.schoolbilling.api.SchoolContracts> contracts;
    private final ApplicationEventPublisher events;
    private final ChangeRecorder changes;

    public WinService(
            ProspectRepository prospects,
            ProspectService prospectService,
            ProposalService proposals,
            SchoolRegistry registry,
            SchoolDirectory schools,
            ObjectProvider<SchoolContacts> contacts,
            ObjectProvider<com.hls.schoolbilling.api.SchoolContracts> contracts,
            ApplicationEventPublisher events,
            ChangeRecorder changes) {
        this.prospects = prospects;
        this.prospectService = prospectService;
        this.proposals = proposals;
        this.registry = registry;
        this.schools = schools;
        this.contacts = contacts;
        this.contracts = contracts;
        this.events = events;
        this.changes = changes;
    }

    @Transactional
    public WinResult win(UUID actor, Set<Role> roles, UUID id, WinRequest request) {
        if (!CallerContext.isOrgWide(roles)) {
            throw new ForbiddenFieldException("Only Admin or Director can create the School of a won prospect.");
        }
        Prospect prospect = prospectService.visible(actor, roles, id);
        if (!prospect.isWon()) {
            throw new ConflictException("The School can be created only after the Final Stage review approved the prospect.");
        }
        if (prospect.getSchoolId() != null) {
            throw new ConflictException("The School of this prospect has already been created.");
        }
        if (request.placeId() == null) {
            throw new InvalidInputException("Choose the Place of the School.");
        }
        SchoolDirectory.PlaceInfo place = schools.place(request.placeId()).orElseThrow(() -> new InvalidInputException("Place not found."));
        if (!place.zoneId().equals(prospect.getZoneId())) {
            throw new InvalidInputException("The Place must be in the prospect's Zone.");
        }
        UUID schoolId;
        if (request.linkSchoolId() != null) {
            SchoolDirectory.SchoolInfo existing = schools.school(request.linkSchoolId()).orElseThrow(() -> new InvalidInputException("School not found."));
            if (!existing.zoneId().equals(prospect.getZoneId())) {
                throw new InvalidInputException("The School must be in the prospect's Zone.");
            }
            if (prospects.findFirstBySchoolId(existing.id()).isPresent()) {
                throw new ConflictException("Another prospect is already linked to this School.");
            }
            schoolId = existing.id();
        } else {
            String billing = Texts.clean(request.billingContact(), 200, "Billing contact");
            if (billing == null) {
                throw new InvalidInputException("Confirm the billing contact of the School.");
            }
            if (prospect.getAddress() == null) {
                throw new InvalidInputException("Add the School's address to the prospect first.");
            }
            var existing = registry.findByNameInPlace(request.placeId(), prospect.getName());
            if (existing.isPresent()) {
                throw new ConflictException("A School with this name already exists in this Place; link it instead (School " + existing.get() + ").");
            }
            schoolId = registry.create(
                    actor,
                    request.placeId(),
                    new SchoolRegistry.NewSchool(prospect.getName(), prospect.getAddress(), prospect.getContactPerson(), prospect.getPhone(), billing));
        }
        SchoolContacts contactWriter = contacts.getIfAvailable();
        if (contactWriter != null && (request.principal() != null || request.accountant() != null)) {
            contactWriter.replace(actor, schoolId, request.principal(), request.accountant());
        }
        prospect.linkSchool(request.placeId(), schoolId);
        prospects.saveAndFlush(prospect);
        changes.record(actor, "PROSPECT_SCHOOL", id, "school", null, schoolId);
        events.publishEvent(new ProspectWon(id, schoolId));
        boolean handoffAvailable = contracts.getIfAvailable() != null;
        return new WinResult(
                prospectService.dtoOf(prospect),
                new Handoff(
                        schoolId,
                        handoffAvailable ? "/operations/school-contracts/schools/" + schoolId : null,
                        proposals.latest(id).orElse(null),
                        handoffAvailable));
    }
}
