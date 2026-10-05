package com.hls.notification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 010 plan.md: producers publish events; notification listens and nothing depends on it. */
class NotificationModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void otherModulesDoNotUseNotificationInternalsOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.notification..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.notification.internal..", "com.hls.notification.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void leaveAndAttendanceDoNotDependOnNotificationAtAll() {
        for (String producer : new String[] {"leave", "attendance"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls." + producer + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.hls.notification..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void notificationReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"leave", "attendance", "teacher", "organization", "school"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.notification..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }
}
