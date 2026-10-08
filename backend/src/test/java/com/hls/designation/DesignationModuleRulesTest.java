package com.hls.designation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 005a plan.md: designation depends on neither organization nor teacher; only its api is used from outside. */
class DesignationModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void designationDoesNotDependOnOrganizationOrTeacher() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.designation..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.organization..", "com.hls.teacher..")
                .check(MAIN);
    }

    @Test
    void onlyTheApiPackageIsUsedFromOutside() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.designation..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.designation.internal..", "com.hls.designation.web..")
                .check(MAIN);
    }

    @Test
    void theManagerDesignationHistoryIsPrivateToOrganization() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.organization..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.organization.internal.ManagerDesignation")
                .check(MAIN);
    }
}
