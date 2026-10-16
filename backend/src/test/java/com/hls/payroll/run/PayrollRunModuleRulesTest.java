package com.hls.payroll.run;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Spec 013b plan.md, Principle VII: the run lives inside {@code payroll}, reaches other modules only through their
 * {@code api} packages, and nobody else reads its types or its tables.
 */
class PayrollRunModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsidePayrollUsesTheRunInternalOrWebPackages() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.payroll..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.payroll.run..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void theRunReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {
            "attendance", "leave", "files", "audit", "notification", "identity", "teacher", "organization", "school"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.payroll.run..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void theRunDoesNotReachIntoAttendanceOrLeaveOrTeacherTypesOutsideTheirApi() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.payroll.run..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.attendance.manager..", "com.hls.leave.manager..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void thePayrollApiHasNoWebTypesAndNoRunInternals() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.payroll.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "com.hls.payroll.web..", "com.hls.payroll.run..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void theOtherModulesDoNotDependOnThePayrollRun() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.hls.attendance..", "com.hls.leave..", "com.hls.files..", "com.hls.teacher..", "com.hls.organization..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.payroll.run..")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
