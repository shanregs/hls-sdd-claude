package com.hls.recruitment.marketing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Spec 023 plan.md: the marketing sub-package and the shared {@code files} module respect module boundaries, and the
 * modules it reads never depend on marketing.
 */
class MarketingModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsideRecruitmentUsesMarketingInternalsOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.recruitment..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.recruitment.marketing.internal..", "com.hls.recruitment.marketing.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void nothingOutsideFilesUsesItsInternals() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.files..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.files.internal..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void theModulesMarketingReadsDoNotDependOnIt() {
        for (String module : new String[] {"school", "organization", "schoolbilling", "notification", "files"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls." + module + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.hls.recruitment.marketing..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void marketingReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"school", "organization", "schoolbilling", "notification", "files", "teacher", "attendance"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.recruitment.marketing..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }
}
