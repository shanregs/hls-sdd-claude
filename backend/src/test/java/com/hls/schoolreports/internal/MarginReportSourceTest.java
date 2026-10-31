package com.hls.schoolreports.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.Role;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayrollFigures;
import com.hls.payroll.api.PayrollFigures.PersonFigures;
import com.hls.payroll.api.PayrollFigures.RunState;
import com.hls.payroll.api.PayrollFigures.RunSummary;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.Cell.State;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportPage.Row;
import com.hls.reporting.api.ReportPage.Section;
import com.hls.reporting.api.ReportRequest;
import com.hls.reporting.api.ReportScope;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.api.SchoolReceivables;
import com.hls.schoolbilling.api.SchoolReceivables.TeacherSchoolBilled;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Spec 014b Margin report logic without Spring: the payroll read failing, distinct Teachers, and same-named Schools. */
class MarginReportSourceTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 9);
    private static final UUID ZONE_A = UUID.randomUUID();
    private static final UUID ZONE_B = UUID.randomUUID();
    private static final UUID T1 = UUID.randomUUID();
    private static final UUID T2 = UUID.randomUUID();
    private static final SchoolInfo S1 = new SchoolInfo(UUID.randomUUID(), "Same", null, ZONE_A, true);
    private static final SchoolInfo S2 = new SchoolInfo(UUID.randomUUID(), "Same", null, ZONE_A, true);
    private static final SchoolInfo S3 = new SchoolInfo(UUID.randomUUID(), "Other", null, ZONE_B, true);

    private final SchoolReceivables receivables = mock(SchoolReceivables.class);
    private final PayrollFigures payroll = mock(PayrollFigures.class);
    private final TeacherDirectory teachers = mock(TeacherDirectory.class);
    private final SchoolScope scope = mock(SchoolScope.class);

    private MarginReportSource source() {
        List<SchoolInfo> all = List.of(S1, S2, S3);
        when(scope.all()).thenReturn(all);
        when(scope.byId(any())).thenAnswer(i -> {
            Map<UUID, SchoolInfo> m = new HashMap<>();
            all.forEach(s -> m.put(s.id(), s));
            return m;
        });
        when(scope.inScope(any())).thenReturn(all);
        when(scope.zoneNames(any())).thenReturn(Map.of(ZONE_A, "Zone A", ZONE_B, "Zone B"));
        when(scope.isolated(any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
        when(teachers.teacherInfo(anyCollection())).thenAnswer(i -> {
            Map<UUID, TeacherInfo> m = new HashMap<>();
            m.put(T1, new TeacherInfo(T1, "Asha", "ACTIVE", null));
            m.put(T2, new TeacherInfo(T2, "Bina", "ACTIVE", null));
            return m;
        });
        BusinessDate date = mock(BusinessDate.class);
        when(date.today()).thenReturn(LocalDate.of(2026, 10, 1));
        return new MarginReportSource(receivables, payroll, teachers, scope, date);
    }

    private static TeacherSchoolBilled billed(UUID teacher, SchoolInfo school, String amount, String days) {
        return new TeacherSchoolBilled(
                teacher, school.id(), MONTH, new BigDecimal(amount), new BigDecimal(days), new BigDecimal("30"));
    }

    private static PersonFigures slip(UUID teacher, String net) {
        return new PersonFigures(
                teacher, Kind.TEACHER, "x", ZONE_A, null, true, new BigDecimal(net), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(net));
    }

    private static RunSummary run() {
        return new RunSummary(
                UUID.randomUUID(), MONTH, RunState.APPROVED, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1, 0);
    }

    private static ReportContext ctx() {
        Map<String, Object> memo = new HashMap<>();
        return new ReportContext() {
            public UUID userId() {
                return UUID.randomUUID();
            }

            public Set<Role> roles() {
                return Set.of();
            }

            public LocalDate today() {
                return LocalDate.of(2026, 10, 1);
            }

            public ReportScope scope() {
                return new ReportScope(true, Set.of(), Set.of());
            }

            public ReportRequest request() {
                return new ReportRequest(MONTH, null, null, null, null);
            }

            @SuppressWarnings("unchecked")
            public <T> T memo(String key, Supplier<T> compute) {
                return (T) memo.computeIfAbsent(key, k -> compute.get());
            }
        };
    }

    private static Section section(MarginReportSource source, String key, ReportContext ctx) {
        return source.sections().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow().read(ctx);
    }

    @Test
    void feeStillShowsWhenThePayslipReadFailsAfterTheRunWasFound() {
        MarginReportSource source = source();
        when(receivables.billedPerTeacherAndSchool(any(), any(), any(), any()))
                .thenReturn(List.of(billed(T1, S1, "1000", "30"), billed(T2, S3, "500", "30")));
        when(payroll.runOf(MONTH)).thenReturn(Optional.of(run()));
        when(payroll.peopleOf(MONTH)).thenThrow(new IllegalStateException("down"));

        Section schools = section(source, "schools", ctx());

        Row all = schools.totals();
        assertThat(all.cells().get(2)).isEqualTo(Cell.of(new BigDecimal("1500")));
        assertThat(all.cells().get(3).state()).isEqualTo(State.NOT_AVAILABLE);
        assertThat(all.cells().get(4).state()).isEqualTo(State.NOT_AVAILABLE);
        assertThat(schools.rows())
                .extracting(r -> r.cells().get(2))
                .contains(Cell.of(new BigDecimal("1000")), Cell.of(new BigDecimal("500")));
        Section teachersSection = section(source, "teachers", ctx());
        assertThat(teachersSection.rows()).allSatisfy(r -> assertThat(r.cells().get(6)).isEqualTo(Cell.of("pay not available")));
    }

    @Test
    void theStatusSaysPayNotAvailableWhenTheRunItselfCannotBeRead() {
        MarginReportSource source = source();
        when(receivables.billedPerTeacherAndSchool(any(), any(), any(), any())).thenReturn(List.of(billed(T1, S1, "1000", "30")));
        when(payroll.runOf(MONTH)).thenThrow(new IllegalStateException("down"));

        Section teachersSection = section(source, "teachers", ctx());

        assertThat(teachersSection.rows().get(0).cells().get(6)).isEqualTo(Cell.of("pay not available"));
        assertThat(section(source, "schools", ctx()).totals().cells().get(2)).isEqualTo(Cell.of(new BigDecimal("1000")));
    }

    @Test
    void teachersMatchedCountsDistinctTeachersNotLines() {
        MarginReportSource source = source();
        // T1 is billed at two Schools of Zone A: one Teacher, two lines
        when(receivables.billedPerTeacherAndSchool(any(), any(), any(), any()))
                .thenReturn(List.of(billed(T1, S1, "400", "10"), billed(T1, S2, "800", "20")));
        when(payroll.runOf(MONTH)).thenReturn(Optional.of(run()));
        when(payroll.peopleOf(MONTH)).thenReturn(List.of(slip(T1, "900")));

        ReportContext ctx = ctx();
        Section zones = section(source, "zones", ctx);
        Section schools = section(source, "schools", ctx);

        assertThat(zones.rows()).hasSize(1);
        assertThat(zones.rows().get(0).cells().get(1)).isEqualTo(Cell.of(1L));
        assertThat(zones.totals().cells().get(1)).isEqualTo(Cell.of(1L));
        assertThat(schools.rows()).allSatisfy(r -> assertThat(r.cells().get(1)).isEqualTo(Cell.of(1L)));
    }

    @Test
    void twoSchoolsWithTheSameNameStayTwoRows() {
        MarginReportSource source = source();
        when(receivables.billedPerTeacherAndSchool(any(), any(), any(), any()))
                .thenReturn(List.of(billed(T1, S1, "400", "30"), billed(T2, S2, "800", "30")));
        when(payroll.runOf(MONTH)).thenReturn(Optional.of(run()));
        when(payroll.peopleOf(MONTH)).thenReturn(List.of(slip(T1, "300"), slip(T2, "300")));

        Section schools = section(source, "schools", ctx());

        assertThat(schools.rows()).hasSize(2);
        assertThat(schools.rows()).extracting(Row::id).containsExactlyInAnyOrder(S1.id().toString(), S2.id().toString());
        assertThat(schools.rows())
                .extracting(r -> r.cells().get(2))
                .containsExactlyInAnyOrder(Cell.of(new BigDecimal("400")), Cell.of(new BigDecimal("800")));
    }

    @Test
    void zonesMissingFromTheNameMapStayApart() {
        MarginReportSource source = source();
        when(scope.zoneNames(any())).thenReturn(Map.of());
        when(receivables.billedPerTeacherAndSchool(any(), any(), any(), any()))
                .thenReturn(List.of(billed(T1, S1, "400", "30"), billed(T2, S3, "800", "30")));
        when(payroll.runOf(MONTH)).thenReturn(Optional.empty());

        Section zones = section(source, "zones", ctx());

        assertThat(zones.rows()).hasSize(2);
        assertThat(zones.rows()).extracting(Row::id).containsExactlyInAnyOrder(ZONE_A.toString(), ZONE_B.toString());
    }
}
