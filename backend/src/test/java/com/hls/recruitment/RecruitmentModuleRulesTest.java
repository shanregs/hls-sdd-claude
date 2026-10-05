package com.hls.recruitment;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Spec 016 plan.md: {@code recruitment} and {@code training} reach other modules only through their api packages,
 * {@code teacher} and {@code schoolbilling} never depend on them, and {@code recruitment} does not depend on
 * {@code training} at all (an accepted offer reaches training by an event).
 */
class RecruitmentModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void otherModulesDoNotUseInternalsOrWeb() {
        for (String module : new String[] {"recruitment", "training"}) {
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

    @Test
    void teacherAndSchoolBillingDoNotDependOnRecruitmentOrTraining() {
        for (String owner : new String[] {"teacher", "schoolbilling"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls." + owner + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls.recruitment..", "com.hls.training..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void recruitmentAndTrainingReadOtherModulesOnlyThroughTheirApiPackages() {
        for (String own : new String[] {"recruitment", "training"}) {
            for (String module : new String[] {"teacher", "organization", "school", "attendance", "leave", "schoolbilling"}) {
                noClasses()
                        .that()
                        .resideInAPackage("com.hls." + own + "..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                        .allowEmptyShould(true)
                        .check(MAIN);
            }
        }
    }

    @Test
    void recruitmentDoesNotDependOnTrainingAtAll() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.recruitment..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.training..")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
