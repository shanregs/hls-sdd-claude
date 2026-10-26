package com.hls.schoolbilling.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.schoolbilling.api.SchoolReceivables;
import com.hls.schoolbilling.internal.ReceivablesStore.DueRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The public receivables read (spec 022 US7). */
@Service
class SchoolReceivablesImpl implements SchoolReceivables {

    private static final BigDecimal ZERO = LedgerQueryService.ZERO;

    private final ReceivablesStore store;
    private final BusinessDate businessDate;

    SchoolReceivablesImpl(ReceivablesStore store, BusinessDate businessDate) {
        this.store = store;
        this.businessDate = businessDate;
    }

    private record Key(UUID teacher, YearMonth month) {}

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<TeacherBilled>> billedPerTeacher(Collection<UUID> teacherIds, YearMonth from, YearMonth to) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        Map<Key, BigDecimal> amounts =
                new TreeMap<>(Comparator.comparing(Key::month).thenComparing(k -> k.teacher().toString()));
        Map<Key, List<UUID>> where = new HashMap<>();
        store.named().query(
                """
                select l.teacher_id, d.month, d.school_id, sum(l.amount) as amount
                from due_line l join monthly_due d on d.id = l.due_id
                where l.teacher_id in (:ids) and d.cancelled_at is null and d.month between :from and :to
                group by l.teacher_id, d.month, d.school_id
                """,
                new MapSqlParameterSource("ids", teacherIds).addValue("from", java.sql.Date.valueOf(from.atDay(1)))
                        .addValue("to", java.sql.Date.valueOf(to.atDay(1))),
                rs -> {
                    Key key = new Key(rs.getObject("teacher_id", UUID.class), YearMonth.from(rs.getDate("month").toLocalDate()));
                    amounts.merge(key, rs.getBigDecimal("amount"), BigDecimal::add);
                    where.computeIfAbsent(key, k -> new ArrayList<>()).add(rs.getObject("school_id", UUID.class));
                });
        Map<UUID, List<TeacherBilled>> out = new LinkedHashMap<>();
        amounts.forEach((k, v) -> out.computeIfAbsent(k.teacher(), t -> new ArrayList<>())
                .add(new TeacherBilled(k.teacher(), k.month(), v, where.get(k))));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<SchoolMonth>> schoolMonths(Collection<UUID> schoolIds, YearMonth from, YearMonth to) {
        LocalDate today = businessDate.today();
        Map<UUID, List<SchoolMonth>> out = new LinkedHashMap<>();
        List<DueRecord> dues = new ArrayList<>(store.duesOfSchools(Set.copyOf(schoolIds)));
        dues.sort(Comparator.comparing(DueRecord::month));
        for (DueRecord d : dues) {
            if (d.cancelled() || d.month().isBefore(from) || d.month().isAfter(to)) {
                continue;
            }
            out.computeIfAbsent(d.schoolId(), k -> new ArrayList<>()).add(new SchoolMonth(
                    d.schoolId(), d.month(), d.total().subtract(d.adjustments()), d.collected(), d.balance(),
                    LedgerQueryService.overdue(d, today), d.dueOn()));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SchoolBalance> balances(Collection<UUID> schoolIds, LocalDate asOf) {
        Map<UUID, SchoolBalance> out = new HashMap<>();
        Map<UUID, BigDecimal[]> sums = new HashMap<>();
        for (DueRecord d : store.duesOfSchools(Set.copyOf(schoolIds))) {
            if (d.cancelled()) {
                continue;
            }
            BigDecimal[] s = sums.computeIfAbsent(d.schoolId(), k -> new BigDecimal[] {ZERO, ZERO, ZERO, ZERO});
            s[0] = s[0].add(d.total().subtract(d.adjustments()));
            s[1] = s[1].add(d.collected());
            s[2] = s[2].add(d.balance());
            if (d.balance().signum() > 0 && d.dueOn().isBefore(asOf)) {
                s[3] = s[3].add(d.balance());
            }
        }
        Map<UUID, BigDecimal> credits = new HashMap<>();
        if (!schoolIds.isEmpty()) {
            store.named().query(
                    """
                    select c.school_id,
                           sum(c.amount - coalesce((select sum(a.amount) from collection_allocation a where a.collection_id = c.id), 0)) as credit
                    from school_collection c
                    where c.school_id in (:ids) and c.reverses_id is null
                      and not exists (select 1 from school_collection r where r.reverses_id = c.id)
                    group by c.school_id
                    """,
                    new MapSqlParameterSource("ids", schoolIds),
                    rs -> {
                        credits.put(rs.getObject("school_id", UUID.class), rs.getBigDecimal("credit"));
                    });
        }
        for (UUID id : schoolIds) {
            BigDecimal[] s = sums.getOrDefault(id, new BigDecimal[] {ZERO, ZERO, ZERO, ZERO});
            out.put(id, new SchoolBalance(id, s[0], s[1], s[2], s[3], credits.getOrDefault(id, ZERO)));
        }
        return out;
    }
}
