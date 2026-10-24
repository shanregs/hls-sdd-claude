package com.hls.expenses.internal;

import com.hls.designation.api.BusinessDate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claims waiting for a decision, grouped by age since submission (spec 015 FR-023): 0-2, 3-7, 8-14 and over 14 days. */
@Service
@Transactional(readOnly = true)
public class PendingQueries {

    static final String[] LABELS = {"0-2 days", "3-7 days", "8-14 days", "Over 14 days"};

    public record Bucket(String label, long count, String total) {}

    public record Summary(List<Bucket> buckets, long count, String total, Instant oldestSubmittedAt) {}

    private final JdbcTemplate jdbc;
    private final BusinessDate businessDate;

    PendingQueries(JdbcTemplate jdbc, BusinessDate businessDate) {
        this.jdbc = jdbc;
        this.businessDate = businessDate;
    }

    static int bucketOf(long ageDays) {
        return ageDays <= 2 ? 0 : ageDays <= 7 ? 1 : ageDays <= 14 ? 2 : 3;
    }

    /** Buckets oldest first, each with its count and total; empty buckets are shown with zero. */
    public Summary summary() {
        long[] counts = new long[4];
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query(
                "select (?::date - (submitted_at at time zone 'Asia/Kolkata')::date) as age, count(*) as n, sum(amount) as total"
                        + " from expense_claim where status = 'SUBMITTED' group by age",
                rs -> {
                    int b = bucketOf(Math.max(0, rs.getLong("age")));
                    counts[b] += rs.getLong("n");
                    totals[b] = totals[b].add(rs.getBigDecimal("total"));
                },
                businessDate.today());
        java.sql.Timestamp oldest = jdbc.queryForObject(
                "select min(submitted_at) from expense_claim where status = 'SUBMITTED'", java.sql.Timestamp.class);
        List<Bucket> buckets = new ArrayList<>();
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 3; i >= 0; i--) {
            buckets.add(new Bucket(LABELS[i], counts[i], ClaimViews.money(totals[i])));
            count += counts[i];
            total = total.add(totals[i]);
        }
        return new Summary(buckets, count, ClaimViews.money(total), oldest == null ? null : oldest.toInstant());
    }
}
