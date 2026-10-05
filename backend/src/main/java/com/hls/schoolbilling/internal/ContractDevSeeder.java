package com.hls.schoolbilling.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.internal.ContractDtos.HlsSignatoryInput;
import com.hls.schoolbilling.internal.ContractDtos.MouRequest;
import com.hls.schoolbilling.internal.ContractDtos.SchoolSignatoryInput;
import com.hls.schoolbilling.internal.ContractDtos.UnmappedTeacherDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): the MoU of the first demo
 * School (4 Teachers at one salary, signed by its Zone Manager and a Director) recorded on the "MoU pending"
 * contract the placements created, with the Teachers already placed there mapped to its first positions.
 * The second demo School has no Zone Manager, so it stays "MoU pending" to show that state.
 */
@Component
@Order(45)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class ContractDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContractDevSeeder.class);

    private final AppUserRepository users;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final ContractRepository contracts;
    private final ContractPositionRepository positions;
    private final ContractService contractService;
    private final RemapService remapService;
    private final Clock clock;

    public ContractDevSeeder(
            AppUserRepository users,
            SchoolDirectory schools,
            ManagerQueries managers,
            ContractRepository contracts,
            ContractPositionRepository positions,
            ContractService contractService,
            RemapService remapService,
            Clock clock) {
        this.users = users;
        this.schools = schools;
        this.managers = managers;
        this.contracts = contracts;
        this.positions = positions;
        this.contractService = contractService;
        this.remapService = remapService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("[DEV SEED] The demo School contract could not be seeded: {}", e.getMessage());
        }
    }

    private void seed() {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        AppUser director = users.findByPhone("9800000002").orElse(null);
        var zone = schools.zoneByName("Demo Zone");
        if (admin == null || director == null || zone.isEmpty()) {
            return;
        }
        List<SchoolInfo> demoSchools = schools.schoolsInZone(zone.get().id());
        if (demoSchools.isEmpty()) {
            return;
        }
        SchoolInfo first = demoSchools.get(0);
        Optional<Contract> pending = contracts.findBySchoolIdOrderByStartsOnDesc(first.id()).stream()
                .filter(Contract::isPending)
                .findFirst();
        Optional<ManagerRef> manager = managers.managerOfSchool(first.id());
        if (pending.isEmpty() || manager.isEmpty() || manager.get().userId() == null) {
            return;
        }
        LocalDate signed = LocalDate.now(clock).minusDays(90);
        MouRequest request = new MouRequest(
                4,
                SalaryMode.SAME_FOR_ALL,
                new BigDecimal("15000.00"),
                null,
                signed,
                List.of(new SchoolSignatoryInput("R. Kumar", "Principal")),
                List.of(
                        new HlsSignatoryInput(manager.get().userId(), "Zone Manager"),
                        new HlsSignatoryInput(director.getId(), "Director")));
        contractService.recordMou(admin.getId(), Set.of(Role.ADMIN), pending.get().getId(), request);

        List<UnmappedTeacherDto> placed = contractService.unmappedTeachers(first.id());
        List<ContractPosition> slots = positions.findByContractIdOrderByNumber(pending.get().getId());
        List<RemapService.Entry> entries = new ArrayList<>();
        for (int i = 0; i < Math.min(placed.size(), slots.size()); i++) {
            entries.add(new RemapService.Entry(placed.get(i).teacherId(), slots.get(i).getId()));
        }
        if (!entries.isEmpty()) {
            remapService.remap(admin.getId(), Set.of(Role.ADMIN), pending.get().getId(), entries);
        }
        log.info(
                "[DEV SEED] School contract demo data ready: {} has a 4-Teacher MoU with {} Teachers mapped.",
                first.name(),
                entries.size());
    }
}
