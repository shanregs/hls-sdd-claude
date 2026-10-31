package com.hls.incentive;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 030 plan.md, Principle VII: incentive reads other modules through their api packages and nobody reads its internals. */
class IncentiveModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsideIncentiveUsesIncentiveInternalOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.incentive..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.incentive.internal..", "com.hls.incentive.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void incentiveReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {
            "payroll", "recruitment", "schoolbilling", "school", "notification", "organization", "reporting", "settings", "audit"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.incentive..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void theIncentiveApiHasNoWebTypes() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.incentive.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "com.hls.incentive.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void noModuleReadsTheIncentiveEntitiesOrRepositories() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.incentive..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.incentive.internal.Incentive")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.incentive.internal.IncentiveRepository")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void payrollNeverDependsOnIncentive() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.payroll..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.incentive..")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
