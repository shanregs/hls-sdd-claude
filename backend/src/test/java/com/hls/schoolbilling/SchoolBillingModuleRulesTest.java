package com.hls.schoolbilling;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Spec 012 plan.md: {@code schoolbilling} implements an interface owned by {@code teacher.api}, so
 * {@code teacher} never depends on it, and every other module reaches it only through its api package.
 */
class SchoolBillingModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void otherModulesDoNotUseSchoolBillingInternalsOrWeb() {
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
    void teacherDoesNotDependOnSchoolBillingAtAll() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.teacher..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.schoolbilling..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void schoolBillingReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"teacher", "organization", "school", "attendance", "leave"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.schoolbilling..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }
}
