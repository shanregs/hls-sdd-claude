package com.hls.content;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 029 T027: content reaches other modules only through their api packages; only notification reads content.api. */
class ContentModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsideContentUsesContentInternalOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.content..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.content.internal..", "com.hls.content.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void contentReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"organization", "files", "school", "designation", "tasks", "notification", "reporting"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.content..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void onlyNotificationAndContentUseTheContentApi() {
        noClasses()
                .that()
                .resideOutsideOfPackages("com.hls.content..", "com.hls.notification..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.content.api..")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
