package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.schoolbilling.api.Occupancy;
import com.hls.schoolbilling.api.SchoolContracts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 031 SC-006 and FR-013: what 012, 013 and 022 read (positions, who fills them, occupancy, the list row) is identical
 * whatever the approval state of the MoU. The ArchUnit rule in MouModuleRulesTest shows the mapping code never reads the state.
 */
class MouRegressionTest extends MouTestBase {

    @Autowired
    private SchoolContracts contracts;

    private record Snapshot(List<String> positions, Occupancy occupancy, Object position, int filled, int vacant, int unmapped) {}

    @SuppressWarnings("unchecked")
    private Snapshot snapshot(Mou m, UUID teacher) {
        Map<String, Object> contract = contractsOf(m.admin(), m.f().schoolId()).get(0);
        List<String> positions = new ArrayList<>();
        for (Map<String, Object> p : positionsOf(contract)) {
            positions.add(p.get("number") + ":" + p.get("salary") + ":" + p.get("teacherName"));
        }
        Map<String, Object> row = content(get(BASE + "?size=100", m.admin())).stream()
                .filter(r -> m.f().schoolId().toString().equals(r.get("schoolId")))
                .findFirst()
                .orElseThrow();
        return new Snapshot(
                positions,
                contracts.occupancyOf(m.f().schoolId(), today),
                contracts.positionOf(teacher, today).map(p -> p.number() + ":" + p.salary()).orElse(null),
                ((Number) row.get("filled")).intValue(),
                ((Number) row.get("vacant")).intValue(),
                ((Number) row.get("unmapped")).intValue());
    }

    @Test
    void mappingOccupancyAndSalariesAreTheSameInEveryApprovalState() {
        Mou m = mou();
        UUID teacher = teacher(m.admin());
        UUID position = UUID.fromString((String) positionsOf(contractsOf(m.admin(), m.f().schoolId()).get(0)).get(0).get("id"));
        assertThat(assign(m.admin(), teacher, m.f().schoolId(), position, today).status()).isEqualTo(200);
        String dir = tokenOf(m.director());

        Snapshot draft = snapshot(m, teacher);
        assertThat(draft.filled()).isEqualTo(1);
        assertThat(draft.occupancy().filled()).isEqualTo(1);
        assertThat(draft.position()).isNotNull();

        submit(m.admin(), m.contractId());
        assertThat(snapshot(m, teacher)).as("PENDING").isEqualTo(draft);
        reject(dir, m.contractId(), "wrong");
        assertThat(snapshot(m, teacher)).as("REJECTED").isEqualTo(draft);
        submit(m.admin(), m.contractId());
        approve(dir, m.contractId());
        assertThat(snapshot(m, teacher)).as("APPROVED").isEqualTo(draft);
        upload(m.admin(), m.contractId(), "v2.pdf", PDF);
        assertThat(stateOf(m.admin(), m.contractId())).isEqualTo("PENDING");
        assertThat(snapshot(m, teacher)).as("RESET").isEqualTo(draft);
        makeLegacy(m.contractId());
        assertThat(snapshot(m, teacher)).as("LEGACY").isEqualTo(draft);
    }
}
