package com.hls;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 005 plan.md: the master-data module graph is teacher -> organization -> school, audit is a sink. */
class MasterDataModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void schoolDoesNotDependOnOrganizationOrTeacher() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.school..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.organization..", "com.hls.teacher..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void organizationDoesNotDependOnTeacher() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.organization..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.teacher..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void auditDoesNotDependOnMasterDataModules() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.audit..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.school..", "com.hls.organization..", "com.hls.teacher..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void onlyApiPackagesAreAccessedFromOutsideAModule() {
        for (String module : new String[] {"school", "organization", "teacher"}) {
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.hls." + module + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }
}
