package com.hls.training;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 024 plan.md, Principle VII: the training calendar keeps to its module boundary. */
class TrainingModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsideTrainingReadsTheCalendarInternalOrWebPackages() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.training..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.training.calendar.internal..", "com.hls.training.calendar.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void attendanceDoesNotDependOnTraining() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.attendance..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.training..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void theCalendarUsesNoOtherModulesInternalOrWebPackages() {
        for (String module : new String[] {
            "attendance", "teacher", "organization", "school", "schoolbilling", "identity", "audit", "designation",
            "notification", "settings", "activitycalendar", "expenses", "recruitment", "leave", "transfer"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.training.calendar..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void trainingApiHasNoWebTypesOrEntities() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.training.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "com.hls.training.calendar.web..", "jakarta.persistence..")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
