package com.hls.transfer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 027 plan.md, Principle VII: transfer reads other modules only through their api packages. */
class TransferModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsideTransferUsesItsInternalOrWebPackages() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.transfer..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.transfer.internal..", "com.hls.transfer.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void transferReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {
            "teacher", "schoolbilling", "school", "organization", "attendance", "substitution", "identity", "audit",
            "designation", "notification"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.transfer..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void onlyNotificationAndTransferUseTheTransferApi() {
        noClasses()
                .that()
                .resideOutsideOfPackages("com.hls.transfer..", "com.hls.notification..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.transfer..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void transferApiHasNoWebTypesOrEntities() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.transfer.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "com.hls.transfer.web..", "jakarta.persistence..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void noModuleReadsTheMovementEntities() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.transfer..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.transfer.internal.MovementRequest")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.transfer.internal.MovementAssignment")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
