package com.hls.recruitment.marketing.internal;

import com.hls.identity.user.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The prospect screen: the prospect with its latest proposal, its contract status and its history. */
@Service
public class ProspectDetailService {

    public record ContractStatusDto(
            boolean available,
            String status,
            ContractStatusReader.ContractSide contract,
            ProposalService.ProposalDto proposal,
            List<String> differences) {}

    public record ProspectDetail(
            ProspectService.ProspectRow row,
            String address,
            String designation,
            String phone,
            String email,
            String lostReason,
            Instant wonAt,
            UUID placeId,
            List<ProspectService.HistoryRow> stageHistory,
            List<ProspectService.OwnerChange> ownerHistory,
            ProposalService.ProposalDto proposal,
            ContractStatusDto contractStatus) {}

    private final ProspectService prospects;
    private final ContractStatusReader reader;

    public ProspectDetailService(ProspectService prospects, ContractStatusReader reader) {
        this.prospects = prospects;
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public ProspectDetail get(UUID userId, Set<Role> roles, UUID id) {
        Prospect prospect = prospects.visible(userId, roles, id);
        ProspectService.ProspectDto dto = prospects.dtoOf(prospect);
        ContractStatusReader.Status status = reader.of(prospect);
        return new ProspectDetail(
                dto.row(),
                dto.address(),
                dto.designation(),
                dto.phone(),
                dto.email(),
                dto.lostReason(),
                dto.wonAt(),
                dto.placeId(),
                dto.stageHistory(),
                dto.ownerHistory(),
                status.proposal(),
                new ContractStatusDto(status.available(), status.status(), status.contract(), status.proposal(), status.differences()));
    }
}
