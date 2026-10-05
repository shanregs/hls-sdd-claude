package com.hls.schoolbilling.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.organization.api.ScopeQueries;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.internal.ContractDtos.ContractDto;
import com.hls.schoolbilling.internal.ContractDtos.ContractStatus;
import com.hls.schoolbilling.internal.ContractDtos.HlsSignatoryInput;
import com.hls.schoolbilling.internal.ContractDtos.MouRequest;
import com.hls.schoolbilling.internal.ContractDtos.NewContractRequest;
import com.hls.schoolbilling.internal.ContractDtos.PositionDto;
import com.hls.schoolbilling.internal.ContractDtos.PositionInput;
import com.hls.schoolbilling.internal.ContractDtos.SchoolContractsDto;
import com.hls.schoolbilling.internal.ContractDtos.SchoolSignatoryInput;
import com.hls.schoolbilling.internal.ContractDtos.SignatoryCandidate;
import com.hls.schoolbilling.internal.ContractDtos.SignatoryDto;
import com.hls.schoolbilling.internal.ContractDtos.UnmappedTeacherDto;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The MoU contract of a School (spec 012). A contract is signed once and never edited: a change of salary,
 * count or signatory is a new contract that ends the current one the day before it starts. The only changes
 * to an existing row are its end date, its cancellation, and recording the MoU once on a "MoU pending"
 * contract. The responsible Manager is the School's Zone Manager, read from {@code organization}.
 */
@Service
public class ContractService {

    static final int ENDS_SOON_DAYS = 30;
    private static final int MAX_TEACHERS = 500;
    private static final int MAX_TEXT = 120;
    private static final BigDecimal MAX_SALARY = new BigDecimal("10000000");
    private static final LocalDate FOREVER = LocalDate.of(9999, 12, 31);

    private final ContractRepository contracts;
    private final ContractPositionRepository positions;
    private final ContractSignatoryRepository signatories;
    private final ContractAssignmentRepository assignments;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final ScopeQueries scopeQueries;
    private final TeacherDirectory teachers;
    private final RoleAssignmentRepository roles;
    private final AppUserRepository users;
    private final ChangeRecorder changes;
    private final Clock clock;

    public ContractService(
            ContractRepository contracts,
            ContractPositionRepository positions,
            ContractSignatoryRepository signatories,
            ContractAssignmentRepository assignments,
            SchoolDirectory schools,
            ManagerQueries managers,
            ScopeQueries scopeQueries,
            TeacherDirectory teachers,
            RoleAssignmentRepository roles,
            AppUserRepository users,
            ChangeRecorder changes,
            Clock clock) {
        this.contracts = contracts;
        this.positions = positions;
        this.signatories = signatories;
        this.assignments = assignments;
        this.schools = schools;
        this.managers = managers;
        this.scopeQueries = scopeQueries;
        this.teachers = teachers;
        this.roles = roles;
        this.users = users;
        this.changes = changes;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reads

    /** The School's contracts, newest first, with positions, signatories and who fills each position today. */
    @Transactional(readOnly = true)
    public SchoolContractsDto schoolContracts(UUID userId, Set<Role> roles, UUID schoolId) {
        SchoolInfo school = requireSchoolInScope(userId, roles, schoolId);
        List<Contract> all = contracts.findBySchoolIdOrderByStartsOnDesc(schoolId);
        List<ContractDto> views = viewsOf(all);
        Optional<ManagerRef> manager = managers.managerOfSchool(schoolId);
        return new SchoolContractsDto(
                schoolId,
                school.name(),
                manager.map(ManagerRef::displayName).orElse(null),
                views,
                unmappedTeachers(schoolId));
    }

    /** HLS signatories on offer: the School's Zone Manager and the active Directors. */
    @Transactional(readOnly = true)
    public List<SignatoryCandidate> signatoryCandidates(UUID userId, Set<Role> roles, UUID schoolId) {
        requireSchoolInScope(userId, roles, schoolId);
        return candidates(schoolId).entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(designation -> new SignatoryCandidate(e.getKey(), nameOf(e.getKey()), designation)))
                .toList();
    }

    // ----------------------------------------------------------------- writes

    /** Records a new MoU for the School; it ends the current contract the day before it starts. */
    @Transactional
    public ContractDto create(UUID actor, Set<Role> roles, UUID schoolId, NewContractRequest request) {
        SchoolInfo school = requireSchoolInScope(actor, roles, schoolId);
        if (!school.active()) {
            throw new ConflictException("This School is inactive.");
        }
        requireZoneManager(schoolId);
        LocalDate today = LocalDate.now(clock);
        if (request.startsOn() == null) {
            throw new InvalidInputException("The start date is required.");
        }
        if (request.endsOn() != null && request.endsOn().isBefore(request.startsOn())) {
            throw new InvalidInputException("The end date cannot be before the start date.");
        }
        Validated mou = validate(schoolId, asMou(request), today);

        // the current contract covering the start date ends the day before; a later one blocks the new dates
        Optional<Contract> covering = contracts.liveOn(schoolId, request.startsOn());
        if (covering.isPresent()) {
            Contract current = covering.get();
            if (!current.getStartsOn().isBefore(request.startsOn())) {
                throw new ConflictException(
                        "These dates overlap this School's contract that starts on " + current.getStartsOn() + ".");
            }
            LocalDate before = current.getEndsOn();
            current.setEndsOn(request.startsOn().minusDays(1));
            contracts.saveAndFlush(current);
            changes.record(actor, "CONTRACT", current.getId(), "endsOn", before, current.getEndsOn());
        }
        Contract contract = Contract.signed(
                schoolId,
                mou.mode(),
                mou.count(),
                mou.rate(),
                mou.signedOn(),
                request.startsOn(),
                request.endsOn(),
                actor,
                clock.instant());
        try {
            contract = contracts.saveAndFlush(contract);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("These dates overlap another contract of this School.");
        }
        insertDetails(contract, mou);
        auditCreated(actor, contract, mou);
        return viewsOf(List.of(contract)).get(0);
    }

    /** Records the MoU on a "MoU pending" contract, once. */
    @Transactional
    public ContractDto recordMou(UUID actor, Set<Role> roles, UUID contractId, MouRequest request) {
        Contract contract = contractInScope(actor, roles, contractId);
        if (!contract.isPending()) {
            throw new ConflictException("The MoU is already recorded. Record a new contract to change it.");
        }
        requireZoneManager(contract.getSchoolId());
        Validated mou = validate(contract.getSchoolId(), request, LocalDate.now(clock));
        contract.recordMou(mou.mode(), mou.count(), mou.rate(), mou.signedOn());
        contracts.saveAndFlush(contract);
        insertDetails(contract, mou);
        auditCreated(actor, contract, mou);
        return viewsOf(List.of(contract)).get(0);
    }

    /** Sets the end date of a live contract. Teachers' own assignments are not shortened by it. */
    @Transactional
    public ContractDto end(UUID actor, Set<Role> roles, UUID contractId, LocalDate endsOn) {
        Contract contract = contractInScope(actor, roles, contractId);
        if (contract.getState() == ContractState.CANCELLED) {
            throw new ConflictException("This contract was cancelled.");
        }
        if (endsOn == null) {
            throw new InvalidInputException("The end date is required.");
        }
        if (endsOn.isBefore(contract.getStartsOn())) {
            throw new InvalidInputException("The end date cannot be before the start date.");
        }
        LocalDate before = contract.getEndsOn();
        contract.setEndsOn(endsOn);
        try {
            contracts.saveAndFlush(contract);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("That end date overlaps the School's next contract.");
        }
        changes.record(actor, "CONTRACT", contract.getId(), "endsOn", before, endsOn);
        return viewsOf(List.of(contract)).get(0);
    }

    /** Cancels a contract that no Teacher is mapped to. */
    @Transactional
    public ContractDto cancel(UUID actor, Set<Role> roles, UUID contractId) {
        Contract contract = contractInScope(actor, roles, contractId);
        if (contract.getState() == ContractState.CANCELLED) {
            throw new ConflictException("This contract is already cancelled.");
        }
        boolean inUse;
        if (contract.isPending()) {
            inUse = !assignments
                    .atSchoolOverlapping(
                            contract.getSchoolId(),
                            contract.getStartsOn(),
                            contract.getEndsOn() == null ? FOREVER : contract.getEndsOn())
                    .isEmpty();
        } else {
            List<UUID> positionIds = positions.findByContractIdOrderByNumber(contract.getId()).stream()
                    .map(ContractPosition::getId)
                    .toList();
            inUse = !positionIds.isEmpty()
                    && !assignments.onPositionsOverlapping(positionIds, LocalDate.of(1900, 1, 1), FOREVER).isEmpty();
        }
        if (inUse) {
            throw new ConflictException("Teachers are assigned under this contract, so it cannot be cancelled.");
        }
        ContractState before = contract.getState();
        contract.setState(ContractState.CANCELLED);
        contracts.saveAndFlush(contract);
        changes.record(actor, "CONTRACT", contract.getId(), "state", before.name(), ContractState.CANCELLED.name());
        return viewsOf(List.of(contract)).get(0);
    }

    // ------------------------------------------------------------- validation

    private record Signer(UUID userId, String name, String designation) {}

    private record Validated(
            SalaryMode mode,
            int count,
            BigDecimal rate,
            LocalDate signedOn,
            List<ContractPosition> positionRows,
            List<SchoolSignatoryInput> schoolSigners,
            List<Signer> hlsSigners) {}

    private static MouRequest asMou(NewContractRequest r) {
        return new MouRequest(
                r.teacherCount(),
                r.salaryMode(),
                r.rate(),
                r.positions(),
                r.signedOn(),
                r.schoolSignatories(),
                r.hlsSignatories());
    }

    private Validated validate(UUID schoolId, MouRequest r, LocalDate today) {
        if (r.teacherCount() == null || r.teacherCount() < 1 || r.teacherCount() > MAX_TEACHERS) {
            throw new InvalidInputException("The number of Teachers must be between 1 and " + MAX_TEACHERS + ".");
        }
        int count = r.teacherCount();
        if (r.salaryMode() == null) {
            throw new InvalidInputException("Choose whether the salary is the same for all Teachers or different for each.");
        }
        List<ContractPosition> rows = new ArrayList<>();
        BigDecimal rate = null;
        if (r.salaryMode() == SalaryMode.SAME_FOR_ALL) {
            rate = money(r.rate(), "The salary");
            if (r.positions() != null && !r.positions().isEmpty()) {
                throw new InvalidInputException("Positions are only entered when the salary differs for each Teacher.");
            }
            for (int n = 1; n <= count; n++) {
                rows.add(new ContractPosition(null, n, null, rate));
            }
        } else {
            if (r.rate() != null) {
                throw new InvalidInputException("Enter a salary for each position, not one salary for all.");
            }
            List<PositionInput> inputs = r.positions() == null ? List.of() : r.positions();
            if (inputs.size() != count) {
                throw new InvalidInputException(
                        "Enter a salary for each of the " + count + " positions (" + inputs.size() + " given).");
            }
            for (int n = 1; n <= count; n++) {
                PositionInput in = inputs.get(n - 1);
                String title = in.title() == null ? null : in.title().trim();
                if (title != null && title.length() > 80) {
                    throw new InvalidInputException("A position title can have at most 80 characters.");
                }
                rows.add(new ContractPosition(
                        null, n, title == null || title.isEmpty() ? null : title, money(in.salary(), "The salary of position " + n)));
            }
        }
        if (r.signedOn() == null) {
            throw new InvalidInputException("The date the MoU was signed is required.");
        }
        if (r.signedOn().isAfter(today)) {
            throw new InvalidInputException("The date signed cannot be in the future.");
        }
        List<SchoolSignatoryInput> schoolSigners = new ArrayList<>();
        for (SchoolSignatoryInput s : r.schoolSignatories() == null ? List.<SchoolSignatoryInput>of() : r.schoolSignatories()) {
            schoolSigners.add(new SchoolSignatoryInput(
                    text(s.name(), "A School signatory's name"), text(s.designation(), "A School signatory's designation")));
        }
        if (schoolSigners.isEmpty()) {
            throw new InvalidInputException("At least one School signatory (name and designation) is required.");
        }
        List<HlsSignatoryInput> hlsInputs = r.hlsSignatories() == null ? List.of() : r.hlsSignatories();
        if (hlsInputs.isEmpty()) {
            throw new InvalidInputException("At least one HLS signatory (the Zone Manager and/or a Director) is required.");
        }
        Map<UUID, List<String>> allowed = candidates(schoolId);
        List<Signer> hlsSigners = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (HlsSignatoryInput in : hlsInputs) {
            List<String> designations = in.userId() == null ? null : allowed.get(in.userId());
            if (designations == null) {
                throw new InvalidInputException(
                        "An HLS signatory must be the School's Zone Manager or an active Director.");
            }
            if (!seen.add(in.userId())) {
                throw new InvalidInputException("The same person is listed twice as an HLS signatory.");
            }
            String designation = in.designation() != null && designations.contains(in.designation().trim())
                    ? in.designation().trim()
                    : designations.get(0);
            hlsSigners.add(new Signer(in.userId(), nameOf(in.userId()), designation));
        }
        return new Validated(r.salaryMode(), count, rate, r.signedOn(), rows, schoolSigners, hlsSigners);
    }

    private static BigDecimal money(BigDecimal value, String what) {
        if (value == null || value.signum() <= 0) {
            throw new InvalidInputException(what + " must be an amount above zero.");
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(value) != 0) {
            throw new InvalidInputException(what + " can have at most two decimal places.");
        }
        if (scaled.compareTo(MAX_SALARY) > 0) {
            throw new InvalidInputException(what + " is too large.");
        }
        return scaled;
    }

    private static String text(String value, String what) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new InvalidInputException(what + " is required.");
        }
        if (v.length() > MAX_TEXT) {
            throw new InvalidInputException(what + " can have at most " + MAX_TEXT + " characters.");
        }
        return v;
    }

    // ------------------------------------------------------------------- data

    private void insertDetails(Contract contract, Validated mou) {
        for (ContractPosition p : mou.positionRows()) {
            positions.save(new ContractPosition(contract.getId(), p.getNumber(), p.getTitle(), p.getSalary()));
        }
        for (SchoolSignatoryInput s : mou.schoolSigners()) {
            signatories.save(new ContractSignatory(
                    contract.getId(), SignatoryParty.SCHOOL, s.name(), s.designation(), null));
        }
        for (Signer s : mou.hlsSigners()) {
            signatories.save(new ContractSignatory(
                    contract.getId(), SignatoryParty.HLS, s.name(), s.designation(), s.userId()));
        }
        positions.flush();
        signatories.flush();
    }

    private void auditCreated(UUID actor, Contract contract, Validated mou) {
        UUID id = contract.getId();
        changes.record(actor, "CONTRACT", id, "state", null, ContractState.ACTIVE.name());
        changes.record(actor, "CONTRACT", id, "teacherCount", null, mou.count());
        changes.record(actor, "CONTRACT", id, "salaryMode", null, mou.mode().name());
        changes.record(actor, "CONTRACT", id, "rate", null, mou.rate());
        changes.record(actor, "CONTRACT", id, "signedOn", null, mou.signedOn());
        changes.record(actor, "CONTRACT", id, "startsOn", null, contract.getStartsOn());
        changes.record(actor, "CONTRACT", id, "endsOn", null, contract.getEndsOn());
        changes.record(
                actor,
                "CONTRACT",
                id,
                "signatories",
                null,
                mou.schoolSigners().size() + " School, " + mou.hlsSigners().size() + " HLS");
    }

    /** The HLS signatories allowed for the School: the Zone Manager and the active Directors, with designations. */
    private Map<UUID, List<String>> candidates(UUID schoolId) {
        Map<UUID, List<String>> result = new LinkedHashMap<>();
        managers.managerOfSchool(schoolId)
                .filter(ManagerRef::active)
                .map(ManagerRef::userId)
                .filter(java.util.Objects::nonNull)
                .ifPresent(id -> result.computeIfAbsent(id, k -> new ArrayList<>()).add("Zone Manager"));
        for (UUID id : roles.userIdsWithRole(Role.DIRECTOR)) {
            if (users.findById(id).filter(AppUser::isActive).isPresent()) {
                result.computeIfAbsent(id, k -> new ArrayList<>()).add("Director");
            }
        }
        return result;
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(AppUser::getDisplayName).orElse("Unknown");
    }

    private void requireZoneManager(UUID schoolId) {
        if (managers.managerOfSchool(schoolId).filter(ManagerRef::active).isEmpty()) {
            throw new ConflictException(
                    "This School has no Zone Manager yet. Assign one first, then record the contract.");
        }
    }

    /** The contract, or not found when its School is outside the caller's scope (Constitution Principle III). */
    private Contract contractInScope(UUID userId, Set<Role> roles, UUID contractId) {
        Contract contract = contracts.findById(contractId)
                .orElseThrow(() -> new NotFoundException("Contract not found."));
        if (!CallerContext.isOrgWide(roles) && !scopeQueries.scopeOf(userId, roles).allowsSchool(contract.getSchoolId())) {
            throw new NotFoundException("Contract not found.");
        }
        return contract;
    }

    private SchoolInfo requireSchool(UUID schoolId) {
        return schools.school(schoolId).orElseThrow(() -> new NotFoundException("School not found."));
    }

    private SchoolInfo requireSchoolInScope(UUID userId, Set<Role> roles, UUID schoolId) {
        SchoolInfo school = requireSchool(schoolId);
        if (!CallerContext.isOrgWide(roles) && !scopeQueries.scopeOf(userId, roles).allowsSchool(schoolId)) {
            throw new NotFoundException("School not found.");
        }
        return school;
    }

    // ------------------------------------------------------------------ views

    ContractStatus statusOf(Contract c, LocalDate today) {
        if (c.getState() == ContractState.CANCELLED) {
            return ContractStatus.CANCELLED;
        }
        if (c.getEndsOn() != null && c.getEndsOn().isBefore(today)) {
            return ContractStatus.ENDED;
        }
        if (c.isPending()) {
            return ContractStatus.MOU_PENDING;
        }
        if (c.getEndsOn() != null && !c.getEndsOn().isAfter(today.plusDays(ENDS_SOON_DAYS))) {
            return ContractStatus.ENDS_SOON;
        }
        return ContractStatus.ACTIVE;
    }

    /** Contract views with positions (and who fills each today) and signatories. */
    List<ContractDto> viewsOf(List<Contract> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        List<UUID> ids = list.stream().map(Contract::getId).toList();
        Map<UUID, List<ContractPosition>> positionsByContract = positions
                .findByContractIdInOrderByContractIdAscNumberAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(ContractPosition::getContractId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<ContractSignatory>> signatoriesByContract =
                signatories.findByContractIdIn(ids).stream().collect(Collectors.groupingBy(ContractSignatory::getContractId));
        List<UUID> positionIds = positionsByContract.values().stream()
                .flatMap(List::stream)
                .map(ContractPosition::getId)
                .toList();
        Map<UUID, UUID> occupant = new HashMap<>();
        if (!positionIds.isEmpty()) {
            for (ContractAssignment a : assignments.onPositionsOverlapping(positionIds, today, today)) {
                occupant.put(a.getPositionId(), a.getTeacherId());
            }
        }
        Map<UUID, TeacherInfo> teacherInfo = occupant.isEmpty() ? Map.of() : teachers.teacherInfo(occupant.values());
        List<ContractDto> result = new ArrayList<>();
        for (Contract c : list) {
            List<PositionDto> pos = positionsByContract.getOrDefault(c.getId(), List.of()).stream()
                    .map(p -> {
                        UUID teacherId = occupant.get(p.getId());
                        TeacherInfo t = teacherId == null ? null : teacherInfo.get(teacherId);
                        return new PositionDto(
                                p.getId(), p.getNumber(), p.getTitle(), p.getSalary(), teacherId, t == null ? null : t.name());
                    })
                    .toList();
            List<SignatoryDto> sig = signatoriesByContract.getOrDefault(c.getId(), List.of()).stream()
                    .map(s -> new SignatoryDto(s.getId(), s.getParty().name(), s.getName(), s.getDesignation(), s.getUserId()))
                    .sorted(java.util.Comparator.comparing(SignatoryDto::party).thenComparing(SignatoryDto::name))
                    .toList();
            result.add(new ContractDto(
                    c.getId(),
                    c.getSchoolId(),
                    c.getState().name(),
                    statusOf(c, today),
                    c.getSalaryMode() == null ? null : c.getSalaryMode().name(),
                    c.getTeacherCount(),
                    c.getRate(),
                    c.getSignedOn(),
                    c.getStartsOn(),
                    c.getEndsOn(),
                    c.getVersion(),
                    pos,
                    sig));
        }
        return result;
    }

    /**
     * Teachers at the School today who are not mapped to a position of the contract in effect (carried-over
     * assignments, or assignments left on a contract that has ended or been replaced; spec 012 FR-007a).
     */
    List<UnmappedTeacherDto> unmappedTeachers(UUID schoolId) {
        LocalDate today = LocalDate.now(clock);
        Optional<Contract> inEffect = contracts.liveOn(schoolId, today);
        Set<UUID> inEffectPositions = inEffect.isEmpty() || inEffect.get().isPending()
                ? Set.of()
                : positions.findByContractIdOrderByNumber(inEffect.get().getId()).stream()
                        .map(ContractPosition::getId)
                        .collect(Collectors.toSet());
        List<ContractAssignment> here = assignments.atSchoolOverlapping(schoolId, today, today);
        List<ContractAssignment> unmapped = here.stream()
                .filter(a -> a.getPositionId() == null || !inEffectPositions.contains(a.getPositionId()))
                .toList();
        if (unmapped.isEmpty()) {
            return List.of();
        }
        Map<UUID, TeacherInfo> info = teachers.teacherInfo(
                unmapped.stream().map(ContractAssignment::getTeacherId).toList());
        return unmapped.stream()
                .map(a -> new UnmappedTeacherDto(
                        a.getTeacherId(),
                        info.containsKey(a.getTeacherId()) ? info.get(a.getTeacherId()).name() : "?"))
                .sorted(java.util.Comparator.comparing(UnmappedTeacherDto::teacherName))
                .toList();
    }
}
