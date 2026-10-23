package com.hls.performance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Spec 026 plan.md, Principle VII: performance reads other modules through their api packages; only notification (and later
 * specs 027 and 028) read performance.api.
 */
class PerformanceModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsidePerformanceUsesItsInternalOrWebPackages() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.performance..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.performance.internal..", "com.hls.performance.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void performanceReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"teacher", "organization", "school", "tasks", "files", "designation", "notification", "schoolbilling"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.performance..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void onlyNotificationAndThePerformanceModuleUseThePerformanceApi() {
        noClasses()
                .that()
                .resideOutsideOfPackages("com.hls.performance..", "com.hls.notification..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.performance..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void thePerformanceApiHasNoWebTypesOrEntities() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.performance.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "com.hls.performance.web..", "jakarta.persistence..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void noModuleReadsThePerformanceEntitiesOrRepositories() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.performance..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.performance.internal.Observation")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.performance.internal.PerformanceIssue")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
