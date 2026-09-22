package com.hls;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Enforces constitution Principle V (modular monolith with enforced boundaries):
 * cross-module calls happen only through a module's public interface, and this
 * ArchUnit rule must fail the build on violation.
 *
 * <p>The {@code status} package is the only bounded-context package that exists at
 * this point in the project (before Identity & Access / teacher / school /
 * schoolbilling etc. are built), so today's rule asserts it has no outgoing
 * dependency on any of the other bounded-context packages named in the
 * constitution's Principle V. Later modules will extend this test as more
 * bounded contexts are added.
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
                        "com.hls.organization..",
                        "com.hls.teacher..",
                        "com.hls.school..",
                        "com.hls.schoolbilling..",
                        "com.hls.payroll..",
                        "com.hls.expense..",
                        "com.hls.training..",
                        "com.hls.substitution..",
                        "com.hls.recruitment..",
                        "com.hls.marketing..",
                        "com.hls.reporting..",
                        "com.hls.audit..");

        noDependencyOnOtherModules.check(classes);
    }

    /**
     * Identity-specific rule added with the Identity & Access module (spec 002):
     * nothing outside {@code com.hls.identity} may reach into
     * {@code com.hls.identity.internal} — only {@code com.hls.identity.api} is public.
     */
    @Test
    void identityInternalsAreOnlyAccessedFromWithinIdentity() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hls");

        ArchRule onlyIdentityAccessesIdentityInternals = noClasses()
                .that().resideOutsideOfPackage("com.hls.identity..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.hls.identity.internal..");

        onlyIdentityAccessesIdentityInternals.check(classes);
    }

    /**
     * Organization-specific rule added with the Organization module (spec 003):
     * nothing outside {@code com.hls.organization} may reach into
     * {@code com.hls.organization.internal} — only {@code com.hls.organization.api}
     * is public. This is what makes `identity`'s dependency on `organization.api`
     * (for {@code ManagerScopeGuard}) one-directional rather than a cycle: `identity`
     * never needs to reach into `organization.internal`.
     */
    @Test
    void organizationInternalsAreOnlyAccessedFromWithinOrganization() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hls");

        ArchRule onlyOrganizationAccessesOrganizationInternals = noClasses()
                .that().resideOutsideOfPackage("com.hls.organization..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.hls.organization.internal..");

        onlyOrganizationAccessesOrganizationInternals.check(classes);
    }

    /**
     * Audit-specific rule added with the Audit module (spec 004): nothing
     * outside {@code com.hls.audit} may reach into {@code com.hls.audit.internal}
     * — only {@code com.hls.audit.api} is public. No module depends on
     * {@code audit.internal} yet (research.md §2), but this rule is added
     * up front rather than only once a real caller exists.
     */
    @Test
    void auditInternalsAreOnlyAccessedFromWithinAudit() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hls");

        ArchRule onlyAuditAccessesAuditInternals = noClasses()
                .that().resideOutsideOfPackage("com.hls.audit..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.hls.audit.internal..");

        onlyAuditAccessesAuditInternals.check(classes);
    }

    /**
     * School-specific rule added with the School module (spec 007): nothing
     * outside {@code com.hls.school} may reach into {@code com.hls.school.internal}
     * — only {@code com.hls.school.api} is public. No module depends on
     * {@code school.internal} yet, but this rule is added up front rather
     * than only once a real caller exists (the same discipline specs/004/005
     * already applied).
     */
    @Test
    void schoolInternalsAreOnlyAccessedFromWithinSchool() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hls");

        ArchRule onlySchoolAccessesSchoolInternals = noClasses()
                .that().resideOutsideOfPackage("com.hls.school..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.hls.school.internal..");

        onlySchoolAccessesSchoolInternals.check(classes);
    }
}
