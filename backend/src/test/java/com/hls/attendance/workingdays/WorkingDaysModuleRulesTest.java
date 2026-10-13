package com.hls.attendance.workingdays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Spec 037 plan.md, Principle VII: the School working days live in {@code attendance.workingdays}; other modules read
 * the figure through {@code attendance.api.SchoolWorkingDays} only. FR-010: storing a figure never touches attendance
 * marks, leave balances or payroll, so the package depends on none of them.
 */
class WorkingDaysModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void attendanceStillDoesNotDependOnLeaveOrPayroll() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.attendance..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.leave..", "com.hls.payroll..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void workingDaysTouchNoAttendanceMarksLeaveOrPayroll() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.attendance.workingdays..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.hls.leave..",
                        "com.hls.payroll..",
                        "com.hls.attendance.internal..",
                        "com.hls.attendance.manager..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void nothingOutsideWorkingDaysUsesItsInternalsOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.attendance.workingdays..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.attendance.workingdays.internal..", "com.hls.attendance.workingdays.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void workingDaysReadOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"organization", "school", "audit", "identity"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.attendance.workingdays..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void noModuleOutsideWorkingDaysReadsItsTablesOrEntities() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.attendance.workingdays..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.attendance.workingdays.internal.SchoolWorkingDaysRow")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.attendance.workingdays.internal.SchoolWorkingDaysRepository")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
