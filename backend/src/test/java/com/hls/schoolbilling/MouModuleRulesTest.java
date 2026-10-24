package com.hls.schoolbilling;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 031 plan.md and FR-013: how the MoU approval code may be reached and what must never read it. */
class MouModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void schoolBillingUsesFilesOnlyThroughFilesApi() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.schoolbilling..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.files.internal..", "com.hls.files.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void nothingOutsideSchoolBillingUsesItsInternalsOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.schoolbilling..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.schoolbilling.internal..", "com.hls.schoolbilling.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void notificationReadsSchoolBillingApiOnly() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.notification..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.schoolbilling.internal..", "com.hls.schoolbilling.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    /** SC-006 / FR-013: mapping, assignment, placement and occupancy never read the approval state. */
    @Test
    void mappingAndAssignmentNeverReadApprovalState() {
        noClasses()
                .that()
                .haveSimpleNameContaining("Assignment")
                .or()
                .haveSimpleName("PlacementMovesImpl")
                .or()
                .haveSimpleName("RemapService")
                .or()
                .haveSimpleName("SchoolContractsImpl")
                .or()
                .haveSimpleName("TeacherPlacementSourceImpl")
                .or()
                .haveSimpleName("PlacementMoves")
                .and()
                .resideInAPackage("com.hls.schoolbilling..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameStartingWith("Mou")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
