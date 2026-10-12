package com.hls.attendance.manager;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Spec 032 plan.md, Principle VII: Manager attendance lives in {@code attendance.manager}, Manager leave in
 * {@code leave.manager}; {@code attendance} still never depends on {@code leave}; everything else reads Manager
 * attendance through {@code attendance.api} only.
 */
class ManagerAttendanceModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void attendanceStillDoesNotDependOnLeave() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.attendance..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.leave..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void managerLeaveUsesAttendanceOnlyThroughItsApiPackage() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.leave.manager..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.hls.attendance.internal..", "com.hls.attendance.web..", "com.hls.attendance.manager..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void nothingOutsideManagerAttendanceUsesItsInternalsOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.attendance.manager..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.attendance.manager.internal..", "com.hls.attendance.manager.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void managerAttendanceReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"organization", "files", "school", "recruitment", "tasks", "teacher"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.attendance.manager..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void noModuleOutsideManagerAttendanceReadsItsTablesOrEntities() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.attendance.manager..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.attendance.manager.internal.ManagerDay")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.attendance.manager.internal.ManagerDayRepository")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
