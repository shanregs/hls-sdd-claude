package com.hls.payroll;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Spec 013a plan.md, Principle VII: payroll reads other modules through their api packages only, nobody reads the
 * payroll tables or its internals, and the module has no way back into the modules it reads.
 */
class PayrollModuleRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hls");

    @Test
    void nothingOutsidePayrollUsesPayrollInternalOrWeb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.payroll..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.hls.payroll.internal..", "com.hls.payroll.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void payrollReadsOtherModulesOnlyThroughTheirApiPackages() {
        for (String module :
                new String[] {"teacher", "organization", "schoolbilling", "attendance", "leave", "designation", "school"}) {
            noClasses()
                    .that()
                    .resideInAPackage("com.hls.payroll..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.hls." + module + ".internal..", "com.hls." + module + ".web..")
                    .allowEmptyShould(true)
                    .check(MAIN);
        }
    }

    @Test
    void theOtherModulesDoNotDependOnPayroll() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.hls.teacher..",
                        "com.hls.organization..",
                        "com.hls.designation..",
                        "com.hls.attendance..",
                        "com.hls.leave..",
                        "com.hls.schoolbilling..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.hls.payroll..")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void noModuleReadsTheTwoPayrollEntitiesOrTheirRepositories() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.payroll..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.payroll.internal.DesignationSalary")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.payroll.internal.PayPolicy")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.payroll.internal.DesignationSalaryRepository")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.payroll.internal.PayPolicyRepository")
                .allowEmptyShould(true)
                .check(MAIN);
    }

    @Test
    void thePayRulesInterfaceHasNoControllerAndPayrollApiHasNoWebTypes() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.payroll.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "com.hls.payroll.web..")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
