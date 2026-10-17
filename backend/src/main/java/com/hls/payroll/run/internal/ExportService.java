package com.hls.payroll.run.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The CSV export of a run (spec 013b US8). It reads the stored lines and payment entries only, and every export is
 * audited with who exported which run and when.
 */
@Service
public class ExportService {

    static final String ENTITY = "PAYROLL_EXPORT";

    /** The file and the name it is saved under. */
    public record CsvFile(String fileName, byte[] bytes) {}

    private final PayRunRepository runs;
    private final PayRunLineRepository lines;
    private final PaymentService payments;
    private final ChangeRecorder changes;

    ExportService(PayRunRepository runs, PayRunLineRepository lines, PaymentService payments, ChangeRecorder changes) {
        this.runs = runs;
        this.lines = lines;
        this.payments = payments;
        this.changes = changes;
    }

    @Transactional
    public CsvFile export(UUID actor, UUID runId) {
        PayRun run = runs.findById(runId).orElseThrow(() -> new NotFoundException("Pay run not found."));
        List<PayRunLine> all = lines.findByRunId(runId).stream()
                .sorted(Comparator.comparing(PayRunLine::getPersonName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PayRunLine::getId))
                .toList();
        byte[] bytes = RunCsvWriter.write(
                run, all, payments.currentOf(all.stream().map(PayRunLine::getId).toList()));
        changes.recordLifecycle(
                actor,
                ENTITY,
                runId,
                "exported",
                RunCsvWriter.statusText(run.getStatus()) + " run of " + run.getMonth() + ", " + all.size() + " rows");
        String kind = run.getKind() == PayRun.Kind.PRIMARY ? "primary" : "supplementary-" + run.getSequence();
        String status = RunCsvWriter.statusText(run.getStatus()).toLowerCase(Locale.ROOT).replace(' ', '-');
        return new CsvFile("Payroll-" + run.getMonth() + "-" + kind + "-" + status + ".csv", bytes);
    }
}
