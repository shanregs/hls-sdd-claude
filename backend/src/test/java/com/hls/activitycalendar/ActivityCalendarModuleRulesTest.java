package com.hls.activitycalendar;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 035 plan.md, Principles VII and SC-008: a read-only module nothing else reads, reaching others through api only. */
class ActivityCalendarModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsideTheCalendarReadsIt() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.activitycalendar..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.activitycalendar..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void theCalendarUsesNoOtherModulesInternalOrWebPackages() {
        for (String module : new String[] {
            "recruitment", "tasks", "organization", "school", "designation", "identity", "attendance", "leave", "teacher",
            "notification", "files", "training", "payroll"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.activitycalendar..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void theWebLayerDeclaresOnlyGetMappings() {
        for (String annotation : new String[] {
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.RequestMapping"
        }) {
            noMethods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage("com.hls.activitycalendar.web..")
                    .should()
                    .beAnnotatedWith(annotation)
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
        methods()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.GetMapping")
                .and()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.hls.activitycalendar.web..")
                .should()
                .bePublic()
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void theInternalPackageHasNoEntityRepositoryOrWriteCall() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.activitycalendar..")
                .should()
                .beAnnotatedWith("jakarta.persistence.Entity")
                .allowEmptyShould(true)
                .check(MAIN);
        noClasses()
                .that()
                .resideInAPackage("com.hls.activitycalendar..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(org.springframework.data.repository.Repository.class)
                .allowEmptyShould(true)
                .check(MAIN);
        noClasses()
                .that()
                .resideInAPackage("com.hls.activitycalendar..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.school.api.ChangeRecorder")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("jakarta.persistence.EntityManager")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void nothingInTheCalendarMarksATransactionWritable() {
        classes()
                .that()
                .resideInAPackage("com.hls.activitycalendar..")
                .and()
                .areAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .should()
                .resideInAPackage("com.hls.activitycalendar.internal..")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
