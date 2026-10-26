package com.hls.schoolbilling.internal;

import com.hls.schoolbilling.internal.ReceivablesDtos.AdjustmentView;
import com.hls.schoolbilling.internal.ReceivablesDtos.AllocationView;
import com.hls.schoolbilling.internal.ReceivablesDtos.CollectionView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** SQL access to the receivables ledger (data-model.md). Rows are only ever inserted; see the V120 triggers. */
@Component
public class ReceivablesStore {

    /** A due as stored plus its derived money columns. */
    public record DueRecord(
            UUID id,
            String reference,
            UUID schoolId,
            YearMonth month,
            LocalDate raisedOn,
            LocalDate dueOn,
            BigDecimal total,
            BigDecimal adjustments,
            BigDecimal collected,
            BigDecimal balance,
            Instant cancelledAt,
            String cancelReason) {

        public boolean cancelled() {
            return cancelledAt != null;
        }
    }

    static final String DUE_SELECT = """
            select d.id, d.reference, d.school_id, d.month, d.raised_on, d.due_on, d.total, b.adjustments, b.collected,
                   b.balance, d.cancelled_at, d.cancel_reason
            from monthly_due d join due_balance b on b.due_id = d.id
            """;

    static final RowMapper<DueRecord> DUE = (rs, n) -> new DueRecord(
            rs.getObject("id", UUID.class),
            rs.getString("reference"),
            rs.getObject("school_id", UUID.class),
            YearMonth.from(rs.getDate("month").toLocalDate()),
            rs.getDate("raised_on").toLocalDate(),
            rs.getDate("due_on").toLocalDate(),
            rs.getBigDecimal("total"),
            rs.getBigDecimal("adjustments"),
            rs.getBigDecimal("collected"),
            rs.getBigDecimal("balance"),
            instant(rs, "cancelled_at"),
            rs.getString("cancel_reason"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    ReceivablesStore(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    public NamedParameterJdbcTemplate named() {
        return named;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /** Serialises ledger writes for one School until the transaction ends. */
    public void lockSchool(UUID schoolId) {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtext(?))::text", String.class, "school-billing:" + schoolId);
    }

    // ---- dues

    public String nextReference(YearMonth month) {
        Long seq = jdbc.queryForObject("select nextval('monthly_due_reference_seq')", Long.class);
        return "MD-%04d%02d-%04d".formatted(month.getYear(), month.getMonthValue(), seq);
    }

    public void insertDue(
            UUID id,
            String reference,
            UUID schoolId,
            YearMonth month,
            LocalDate raisedOn,
            LocalDate dueOn,
            BigDecimal total,
            UUID actor,
            Instant now) {
        jdbc.update(
                """
                insert into monthly_due (id, reference, school_id, month, raised_on, due_on, total, raised_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, reference, schoolId, month.atDay(1), raisedOn, dueOn, total, actor, ts(now));
    }

    public void insertLine(UUID dueId, DueCalculator.Line l) {
        jdbc.update(
                """
                insert into due_line (id, due_id, teacher_id, position_id, position_number, contract_id, salary, span_from,
                                      span_to, worked_days, month_days, full_month, amount)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), dueId, l.teacherId(), l.positionId(), l.positionNumber(), l.contractId(), l.salary(),
                l.from(), l.to(), l.workedDays(), l.monthDays(), l.fullMonth(), l.amount());
    }

    public Optional<DueRecord> due(UUID id) {
        return jdbc.query(DUE_SELECT + " where d.id = ?", DUE, id).stream().findFirst();
    }

    public Optional<DueRecord> liveDue(UUID schoolId, YearMonth month) {
        return jdbc
                .query(DUE_SELECT + " where d.school_id = ? and d.month = ? and d.cancelled_at is null", DUE, schoolId, month.atDay(1))
                .stream()
                .findFirst();
    }

    public void cancelDue(UUID id, UUID actor, String reason, Instant now) {
        jdbc.update(
                "update monthly_due set cancelled_at = ?, cancelled_by = ?, cancel_reason = ? where id = ? and cancelled_at is null",
                ts(now), actor, reason, id);
    }

    /** Non-cancelled dues of the School that still have a balance, oldest due first. */
    public List<DueRecord> openDues(UUID schoolId) {
        return jdbc.query(
                DUE_SELECT + " where d.school_id = ? and d.cancelled_at is null and b.balance > 0 order by d.due_on, d.month, d.reference",
                DUE,
                schoolId);
    }

    public List<DueRecord> duesOfSchool(UUID schoolId) {
        return jdbc.query(DUE_SELECT + " where d.school_id = ? order by d.month desc, d.reference desc", DUE, schoolId);
    }

    public List<DueRecord> duesOfSchools(Collection<UUID> schoolIds) {
        if (schoolIds.isEmpty()) {
            return List.of();
        }
        return named.query(
                DUE_SELECT + " where d.school_id in (:ids) order by d.month desc, d.reference desc",
                new MapSqlParameterSource("ids", schoolIds),
                DUE);
    }

    public List<DueRecord> allDues() {
        return jdbc.query(DUE_SELECT + " order by d.month desc, d.reference desc", DUE);
    }

    // ---- lines

    public List<LineRow> lines(UUID dueId) {
        return jdbc.query(
                "select * from due_line where due_id = ? order by position_number, span_from",
                (rs, n) -> new LineRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("teacher_id", UUID.class),
                        (Integer) rs.getObject("position_number"),
                        rs.getBigDecimal("salary"),
                        rs.getDate("span_from").toLocalDate(),
                        rs.getDate("span_to").toLocalDate(),
                        rs.getBigDecimal("worked_days"),
                        rs.getBigDecimal("month_days"),
                        rs.getBoolean("full_month"),
                        rs.getBigDecimal("amount")),
                dueId);
    }

    public record LineRow(
            UUID id,
            UUID teacherId,
            Integer positionNumber,
            BigDecimal salary,
            LocalDate from,
            LocalDate to,
            BigDecimal worked,
            BigDecimal monthDays,
            boolean full,
            BigDecimal amount) {}

    // ---- adjustments

    public void insertAdjustment(UUID id, UUID dueId, BigDecimal amount, String reason, UUID reverses, UUID actor, Instant now) {
        jdbc.update(
                "insert into due_adjustment (id, due_id, amount, reason, reverses_id, created_by, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                id, dueId, amount, reason, reverses, actor, ts(now));
    }

    public Optional<AdjustmentView> adjustment(UUID id) {
        return jdbc.query(ADJUSTMENT_SELECT + " where a.id = ?", ADJUSTMENT, id).stream().findFirst();
    }

    private static final String ADJUSTMENT_SELECT = """
            select a.id, a.due_id, a.amount, a.reason, a.reverses_id, a.created_at,
                   exists (select 1 from due_adjustment r where r.reverses_id = a.id) as reversed
            from due_adjustment a
            """;

    private static final RowMapper<AdjustmentView> ADJUSTMENT = (rs, n) -> new AdjustmentView(
            rs.getObject("id", UUID.class),
            rs.getObject("due_id", UUID.class),
            rs.getBigDecimal("amount"),
            rs.getString("reason"),
            rs.getObject("reverses_id", UUID.class),
            rs.getBoolean("reversed"),
            instant(rs, "created_at"));

    public List<AdjustmentView> adjustmentsOfDue(UUID dueId) {
        return jdbc.query(ADJUSTMENT_SELECT + " where a.due_id = ? order by a.created_at, a.id", ADJUSTMENT, dueId);
    }

    public List<AdjustmentView> adjustmentsOfSchool(UUID schoolId) {
        return jdbc.query(
                ADJUSTMENT_SELECT + " join monthly_due d on d.id = a.due_id where d.school_id = ? order by a.created_at desc, a.id",
                ADJUSTMENT,
                schoolId);
    }

    // ---- collections

    public void insertCollection(
            UUID id,
            UUID schoolId,
            BigDecimal amount,
            LocalDate receivedOn,
            String mode,
            String reference,
            String receiver,
            String comment,
            UUID reverses,
            String reason,
            UUID actor,
            Instant now) {
        jdbc.update(
                """
                insert into school_collection (id, school_id, amount, received_on, mode, reference, receiver, comment,
                                               reverses_id, reason, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, schoolId, amount, receivedOn, mode, reference, receiver, comment, reverses, reason, actor, ts(now));
    }

    private static final String COLLECTION_SELECT = """
            select c.id, c.school_id, c.amount, c.received_on, c.mode, c.reference, c.receiver, c.comment, c.reverses_id,
                   c.reason, c.created_at,
                   exists (select 1 from school_collection r where r.reverses_id = c.id) as reversed,
                   coalesce((select sum(a.amount) from collection_allocation a where a.collection_id = c.id), 0) as allocated
            from school_collection c
            """;

    private static final RowMapper<CollectionView> COLLECTION = (rs, n) -> {
        BigDecimal amount = rs.getBigDecimal("amount");
        BigDecimal allocated = rs.getBigDecimal("allocated");
        boolean reversal = rs.getObject("reverses_id", UUID.class) != null;
        boolean reversed = rs.getBoolean("reversed");
        return new CollectionView(
                rs.getObject("id", UUID.class),
                rs.getObject("school_id", UUID.class),
                amount,
                rs.getDate("received_on").toLocalDate(),
                rs.getString("mode"),
                rs.getString("reference"),
                rs.getString("receiver"),
                rs.getString("comment"),
                rs.getObject("reverses_id", UUID.class),
                reversed,
                rs.getString("reason"),
                allocated,
                reversal || reversed ? BigDecimal.ZERO.setScale(2) : amount.subtract(allocated),
                instant(rs, "created_at"));
    };

    public Optional<CollectionView> collection(UUID id) {
        return jdbc.query(COLLECTION_SELECT + " where c.id = ?", COLLECTION, id).stream().findFirst();
    }

    public List<CollectionView> collectionsOfSchool(UUID schoolId) {
        return jdbc.query(COLLECTION_SELECT + " where c.school_id = ? order by c.received_on desc, c.created_at desc", COLLECTION, schoolId);
    }

    public boolean likelyDuplicate(UUID schoolId, BigDecimal amount, LocalDate receivedOn, String mode, String reference) {
        Integer n = jdbc.queryForObject(
                """
                select count(*) from school_collection c
                where c.school_id = ? and c.amount = ? and c.received_on = ? and c.mode = ?
                  and coalesce(c.reference, '') = coalesce(?, '') and c.reverses_id is null
                  and not exists (select 1 from school_collection r where r.reverses_id = c.id)
                """,
                Integer.class, schoolId, amount, receivedOn, mode, reference);
        return n != null && n > 0;
    }

    /** Sum of collections of the School not reversed (and not themselves a reversal) less every allocation of them. */
    public BigDecimal advanceCredit(UUID schoolId) {
        return jdbc.queryForObject(
                """
                select coalesce(sum(c.amount), 0)
                       - coalesce((select sum(a.amount) from collection_allocation a
                                   join school_collection c2 on c2.id = a.collection_id
                                   where c2.school_id = ? and c2.reverses_id is null
                                     and not exists (select 1 from school_collection r where r.reverses_id = c2.id)), 0)
                from school_collection c
                where c.school_id = ? and c.reverses_id is null
                  and not exists (select 1 from school_collection r where r.reverses_id = c.id)
                """,
                BigDecimal.class, schoolId, schoolId);
    }

    // ---- allocations

    public void insertAllocation(UUID collectionId, UUID dueId, BigDecimal amount, UUID actor, Instant now) {
        jdbc.update(
                "insert into collection_allocation (id, collection_id, due_id, amount, created_by, created_at) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), collectionId, dueId, amount, actor, ts(now));
    }

    public List<AllocationView> allocationsOfDue(UUID dueId) {
        return jdbc.query(
                """
                select a.id, a.collection_id, a.due_id, d.reference, a.amount, a.created_at
                from collection_allocation a join monthly_due d on d.id = a.due_id
                where a.due_id = ? order by a.created_at, a.id
                """,
                (rs, n) -> new AllocationView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("collection_id", UUID.class),
                        rs.getObject("due_id", UUID.class),
                        rs.getString("reference"),
                        rs.getBigDecimal("amount"),
                        instant(rs, "created_at")),
                dueId);
    }

    /** Net allocation of one collection per due, only the non-zero ones. */
    public List<AllocationView> netAllocationsOfCollection(UUID collectionId) {
        return jdbc.query(
                """
                select min(a.id::text)::uuid as id, a.collection_id, a.due_id, d.reference, sum(a.amount) as amount,
                       max(a.created_at) as created_at
                from collection_allocation a join monthly_due d on d.id = a.due_id
                where a.collection_id = ? group by a.collection_id, a.due_id, d.reference having sum(a.amount) <> 0
                """,
                (rs, n) -> new AllocationView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("collection_id", UUID.class),
                        rs.getObject("due_id", UUID.class),
                        rs.getString("reference"),
                        rs.getBigDecimal("amount"),
                        instant(rs, "created_at")),
                collectionId);
    }

    public BigDecimal allocationsOnDue(UUID dueId) {
        return jdbc.queryForObject("select coalesce(sum(amount), 0) from collection_allocation where due_id = ?", BigDecimal.class, dueId);
    }

    // ---- follow-up

    public Optional<UUID> followUpTask(UUID dueId) {
        List<UUID> ids = jdbc.query("select task_id from overdue_follow_up where due_id = ?", (rs, n) -> rs.getObject("task_id", UUID.class), dueId);
        return ids.stream().filter(java.util.Objects::nonNull).findFirst();
    }
}
