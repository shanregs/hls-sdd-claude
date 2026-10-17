package com.hls.payroll.run.internal;

import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.run.internal.PaymentService.EntryView;
import com.hls.school.api.InvalidInputException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's payroll history across runs (spec 013b US7): each run they were in with its month, kind, net pay,
 * status and the current payment entry. Read only, from the stored lines, so it never recomputes anything.
 */
@Service
public class PersonHistoryService {

    /** One run the person was in. {@code net} is null for a person who could not be paid in that run. */
    public record HistoryRow(
            UUID runId,
            UUID lineId,
            String month,
            PayRun.Kind runKind,
            int sequence,
            PayRun.Status status,
            PersonResult.State state,
            String reason,
            String net,
            EntryView payment) {}

    private final PayRunLineRepository lines;
    private final PayRunRepository runs;
    private final PaymentService payments;

    PersonHistoryService(PayRunLineRepository lines, PayRunRepository runs, PaymentService payments) {
        this.lines = lines;
        this.runs = runs;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<HistoryRow> history(String kindText, UUID personId) {
        Kind kind = parse(kindText);
        List<PayRunLine> found = lines.findByPerson(kind, personId);
        Map<UUID, PayRun> byId = runs.findAllById(found.stream().map(PayRunLine::getRunId).distinct().toList()).stream()
                .collect(Collectors.toMap(PayRun::getId, Function.identity()));
        Map<UUID, EntryView> current = payments.currentOf(found.stream().map(PayRunLine::getId).toList());
        return found.stream()
                .filter(l -> byId.containsKey(l.getRunId()) && byId.get(l.getRunId()).getStatus() != PayRun.Status.CANCELLED)
                .map(l -> {
                    PayRun run = byId.get(l.getRunId());
                    return new HistoryRow(
                            run.getId(),
                            l.getId(),
                            run.getMonth().toString(),
                            run.getKind(),
                            run.getSequence(),
                            run.getStatus(),
                            l.getState(),
                            l.getReason(),
                            RunService.money(l.getNet()),
                            current.get(l.getId()));
                })
                .sorted(Comparator.comparing(HistoryRow::month)
                        .thenComparing(HistoryRow::runKind)
                        .thenComparingInt(HistoryRow::sequence)
                        .reversed())
                .toList();
    }

    private static Kind parse(String raw) {
        try {
            return Kind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new InvalidInputException("Unknown person kind \"" + raw + "\".");
        }
    }
}
