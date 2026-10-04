package com.hls.audit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * FR-004 ("audit entries MUST NOT be editable or deletable through any capability") enforced at
 * the code level, plus confirmation that tasks.md T019's decommission of identity's interim
 * {@code login_history_event} table is complete.
 */
class ArchitectureRulesTest {

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter().importPackages("com.hls");

    private static final List<String> APPEND_ONLY_REPOSITORIES = List.of(
            "com.hls.audit.loginhistory.LoginHistoryEntryRepository",
            "com.hls.audit.changehistory.ChangeHistoryEntryRepository",
            "com.hls.audit.useractivity.UserActivityEntryRepository",
            "com.hls.audit.apiaccess.ApiAccessEntryRepository");

    @Test
    void auditRepositoriesExposeNoUpdateOrDeleteMethod() {
        for (String repositoryClassName : APPEND_ONLY_REPOSITORIES) {
            var repositoryClass = ALL_CLASSES.get(repositoryClassName);
            boolean hasUpdateOrDelete = repositoryClass.getMethods().stream()
                    .map(JavaMethod::getName)
                    .anyMatch(name -> name.startsWith("update") || name.startsWith("delete"));

            assertThat(hasUpdateOrDelete)
                    .as("%s must not expose an update*/delete* method (FR-004)", repositoryClassName)
                    .isFalse();
        }
    }

    @Test
    void onlyAuditReferencesItsOwnAppendOnlyRepositoriesAndEntities() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.hls.audit..")
                .should()
                .dependOnClassesThat()
                .haveNameMatching(
                        "com\\.hls\\.audit\\.(loginhistory\\.(LoginHistoryEntry|LoginHistoryEntryRepository)"
                                + "|changehistory\\.(ChangeHistoryEntry|ChangeHistoryEntryRepository)"
                                + "|useractivity\\.(UserActivityEntry|UserActivityEntryRepository)"
                                + "|apiaccess\\.(ApiAccessEntry|ApiAccessEntryRepository))")
                .check(ALL_CLASSES);
    }

    /**
     * Spec 018 T016: audit never reads the HTTP request. The client context reaches it only through
     * events published by {@code identity}, captured at publish time.
     */
    @Test
    void auditDoesNotReadTheHttpRequestOrTheClientContextHolder() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.audit..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.identity.clientcontext.ClientContextHolder")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.identity.clientcontext.ClientContextFilter")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.hls.identity.clientcontext.ClientContextParser")
                .check(ALL_CLASSES);
    }

    @Test
    void auditEntitiesAndEventConsumersHaveNoServletOrWebDependency() {
        noClasses()
                .that()
                .resideInAPackage("com.hls.audit..")
                .and()
                .haveSimpleNameEndingWith("EventConsumer")
                .or()
                .resideInAPackage("com.hls.audit..")
                .and()
                .haveSimpleNameEndingWith("Entry")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.servlet..", "org.springframework.web..")
                .check(ALL_CLASSES);
    }

    @Test
    void identityNoLongerHasItsOwnLoginHistoryTable() {
        boolean legacyClassesStillExist = ALL_CLASSES.stream()
                .anyMatch(javaClass -> javaClass.getFullName().equals("com.hls.identity.loginhistory.LoginHistoryEvent")
                        || javaClass
                                .getFullName()
                                .equals("com.hls.identity.loginhistory.LoginHistoryEventRepository"));

        assertThat(legacyClassesStillExist)
                .as("tasks.md T019's decommission of identity's interim login_history_event table must be complete")
                .isFalse();
    }
}
