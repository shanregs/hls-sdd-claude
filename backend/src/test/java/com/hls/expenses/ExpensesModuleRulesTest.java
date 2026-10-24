package com.hls.expenses;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec 015 plan.md, Principle VII: expenses reads other modules through their api packages and nobody reads its internals. */
class ExpensesModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsideExpensesUsesExpensesInternalOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.expenses..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.expenses.internal..", "com.hls.expenses.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void expensesReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module : new String[] {
            "payroll", "recruitment", "tasks", "training", "files", "school", "notification", "designation", "organization", "teacher"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.expenses..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void theExpensesApiHasNoWebTypes() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.expenses.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "com.hls.expenses.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void noModuleReadsTheClaimEntitiesOrRepositories() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.expenses..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.expenses.internal.ExpenseClaim")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.expenses.internal.ExpenseClaimRepository")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
