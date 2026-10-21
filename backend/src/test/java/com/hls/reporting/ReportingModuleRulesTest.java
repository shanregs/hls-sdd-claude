package com.hls.reporting;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 028 plan.md, Principle VII: reporting owns no tables and depends on no source module; source modules depend on it. */
class ReportingModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void theReportingApiUsesOnlyIdentityTypes() {
        classes()
                .that()
                .resideInAPackage("com.hls.reporting.api..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..", "com.hls.reporting.api..", "com.hls.identity.permissions..", "com.hls.identity.user..",
                        "org.springframework.modulith..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void reportingInternalUsesOnlyItsApiIdentityAndTheSharedReadApis() {
        classes()
                .that()
                .resideInAPackage("com.hls.reporting.internal..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..", "javax..", "jakarta..", "org.slf4j..", "org.springframework..", "com.fasterxml..",
                        "com.hls.reporting.api..", "com.hls.reporting.internal..", "com.hls.identity.permissions..",
                        "com.hls.identity.user..", "com.hls.organization.api..", "com.hls.designation.api..",
                        "com.hls.school.api..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void nothingOutsideReportingUsesReportingInternalOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.reporting..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.reporting.internal..", "com.hls.reporting.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void reportingReadsNoOtherModulesInternalsOrTables() {
        for (String module : new String[] {
            "organization", "school", "designation", "attendance", "leave", "payroll", "recruitment", "teacher", "tasks",
            "substitution", "training", "schoolbilling", "files", "notification", "settings", "audit"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.reporting..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
        noClasses()
                .that()
                .resideInAPackage("com.hls.reporting..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith("jakarta.persistence.Entity")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
