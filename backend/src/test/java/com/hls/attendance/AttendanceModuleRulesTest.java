package com.hls.attendance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 008 plan.md: attendance -> teacher -> organization -> school, through public APIs only. */
class AttendanceModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void otherModulesDoNotDependOnAttendance() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.attendance..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.attendance..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void attendanceReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"school", "organization", "teacher"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.attendance..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }
}
