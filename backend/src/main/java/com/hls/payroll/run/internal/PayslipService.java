package com.hls.payroll.run.internal;

import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileContent;
import com.hls.school.api.NotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out the payslips issued at approval (spec 013b US6). It only reads: the PDFs were drawn once, from the stored
 * lines, and are opened from the shared file store, so a payslip never changes after the run is approved (FR-023).
 * A draft or awaiting run, a cannot-pay line and a line of another run are all "not found".
 */
@Service
public class PayslipService {

    /** One issued payslip with its bytes. */
    public record Slip(String fileName, byte[] bytes) {}

    /** The payslips of a run, in line order, and the name the zip is saved under. */
    public record RunSlips(String zipName, List<UUID> fileIds) {}

    private final PayRunRepository runs;
    private final PayslipRepository payslips;
    private final FileStore files;

    PayslipService(PayRunRepository runs, PayslipRepository payslips, FileStore files) {
        this.runs = runs;
        this.payslips = payslips;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public Slip one(UUID runId, UUID lineId) {
        approved(runId);
        Payslip payslip = payslips.findByLineId(lineId)
                .filter(p -> p.getRunId().equals(runId))
                .orElseThrow(() -> new NotFoundException("No payslip for this line."));
        FileContent content = open(payslip);
        return new Slip(content.file().name(), content.bytes());
    }

    /** Checks the run and lists its payslips; the zip itself is written by {@link #writeZip}, outside any transaction. */
    @Transactional(readOnly = true)
    public RunSlips all(UUID runId) {
        PayRun run = approved(runId);
        List<UUID> fileIds = payslips.findByRunId(runId).stream()
                .sorted(java.util.Comparator.comparing(Payslip::getIssuedAt).thenComparing(Payslip::getId))
                .map(Payslip::getFileId)
                .toList();
        if (fileIds.isEmpty()) {
            throw new NotFoundException("This run has no payslips.");
        }
        String runName = run.getKind() == PayRun.Kind.PRIMARY ? "primary" : "supplementary-" + run.getSequence();
        return new RunSlips("Payslips-" + run.getMonth() + "-" + runName + ".zip", fileIds);
    }

    /** Streams the payslips into a zip, one entry each; entries with the same name get a number so none is lost. */
    public void writeZip(RunSlips slips, OutputStream out) {
        Set<String> used = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (UUID fileId : slips.fileIds()) {
                FileContent content = files.open(fileId)
                        .orElseThrow(() -> new IllegalStateException("A payslip file is missing: " + fileId));
                String name = unique(content.file().name(), used);
                zip.putNextEntry(new ZipEntry(name));
                zip.write(content.bytes());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The payslips could not be zipped", e);
        }
    }

    private static String unique(String name, Set<String> used) {
        String candidate = name;
        int n = 2;
        while (!used.add(candidate)) {
            int dot = name.lastIndexOf('.');
            candidate = dot < 0 ? name + "-" + n : name.substring(0, dot) + "-" + n + name.substring(dot);
            n++;
        }
        return candidate;
    }

    private PayRun approved(UUID runId) {
        PayRun run = runs.findById(runId).orElseThrow(() -> new NotFoundException("Pay run not found."));
        if (run.getStatus() != PayRun.Status.APPROVED) {
            throw new NotFoundException("Payslips exist only for an approved run.");
        }
        return run;
    }

    private FileContent open(Payslip payslip) {
        return files.open(payslip.getFileId())
                .orElseThrow(() -> new IllegalStateException("The payslip file is missing: " + payslip.getFileId()));
    }
}
