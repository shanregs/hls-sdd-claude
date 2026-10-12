package com.hls.substitution;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 017a plan.md, Principle VII: substitution reads other modules through api packages, and nobody reads it. */
class SubstitutionModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsideSubstitutionUsesItsInternalOrWebPackages() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.substitution..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.substitution.internal..", "com.hls.substitution.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void substitutionReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"teacher", "organization", "school", "attendance", "leave", "notification", "payroll", "schoolbilling"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.substitution..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void attendanceLeaveTeacherAndOrganizationDoNotDependOnSubstitution() {
        noClasses()
                .that()
                .resideInAnyPackage("com.hls.attendance..", "com.hls.leave..", "com.hls.teacher..", "com.hls.organization..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.substitution..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void noTaskOrPayrollDependency() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.substitution..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.payroll..", "com.hls.task..")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
