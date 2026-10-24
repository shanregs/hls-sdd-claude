package com.hls.tasks;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 025 plan.md, Principle VII: tasks reads other modules through their api packages; only notification, Manager attendance (planned activities) and Teacher performance (task creation) and Expenses (links, spec 015) read tasks. */
class TasksModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsideTasksUsesTasksInternalOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.tasks..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.tasks.internal..", "com.hls.tasks.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void tasksReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {"organization", "files", "school", "designation", "recruitment", "notification"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.tasks..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void onlyNotificationAndTheTasksModuleUseTheTasksApi() {
        // Manager attendance (spec 032) reads tasks as planned activities through TaskActivities (contract C1); Teacher
        // performance (spec 026) creates and completes Observation tasks through TaskCreation (contract C2).
        noClasses()
                .that()
                .resideOutsideOfPackages("com.hls.tasks..", "com.hls.notification..", "com.hls.attendance.manager..", "com.hls.activitycalendar..", "com.hls.performance..", "com.hls.expenses..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.tasks..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void theTasksApiHasNoWebTypes() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.tasks.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "com.hls.tasks.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void noModuleReadsTheTaskEntitiesOrRepositories() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.tasks..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.tasks.internal.Task")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.tasks.internal.TaskRepository")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
