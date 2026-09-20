package com.hls;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces constitution Principle V (modular monolith with enforced boundaries):
 * cross-module calls happen only through a module's public interface, and this
 * ArchUnit rule must fail the build on violation.
 *
 * <p>The {@code status} package is the only bounded-context package that exists at
 * this point in the project (before Identity & Access / roster / schoolbilling etc.
 * are built), so today's rule asserts it has no outgoing dependency on any of the
 * other bounded-context packages named in the constitution's Principle V. Later
 * modules will extend this test as more bounded contexts are added.
 */
class ArchitectureTest {

    @Test
    void statusPackageDoesNotDependOnOtherBoundedContexts() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hls");

        ArchRule noDependencyOnOtherModules = noClasses()
                .that().resideInAPackage("com.hls.status..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.hls.identity..",
                        "com.hls.roster..",
                        "com.hls.schoolbilling..",
                        "com.hls.payroll..",
                        "com.hls.expense..",
                        "com.hls.training..",
                        "com.hls.substitution..",
                        "com.hls.recruitment..",
                        "com.hls.marketing..");

        noDependencyOnOtherModules.check(classes);
    }
}
