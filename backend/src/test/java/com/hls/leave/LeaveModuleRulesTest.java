package com.hls.leave;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 009 plan.md: leave -> attendance -> teacher -> organization -> school, through public APIs only. */
class LeaveModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void otherModulesUseLeaveOnlyThroughItsApiPackage() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.leave..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.leave.internal..", "com.hls.leave.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void leaveReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"attendance", "teacher", "organization", "school"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.leave..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void attendanceDoesNotDependOnLeave() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.attendance..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.leave..")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
