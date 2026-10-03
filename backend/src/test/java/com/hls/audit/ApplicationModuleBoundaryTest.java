package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * Verifies Constitution Principle VII ("no module writes audit history to its own tables" /
 * audit is a pure sink) at the event-wiring level, which {@code ApplicationModulesTest}'s
 * {@code ApplicationModules.verify()} does not check on its own: that {@code audit} consumes
 * exactly the domain events spec.md's Assumptions and data-model.md document — spec 001's
 * {@code LoginHistoryRecorded}, spec 002's {@code PermissionMatrixChanged}, and the five lifecycle
 * events this spec added to {@code identity} (tasks.md T034) — and publishes none of its own.
 */
class ApplicationModuleBoundaryTest {

    private static final Set<String> EXPECTED_CONSUMED_EVENTS = Set.of(
            "com.hls.identity.loginhistory.LoginHistoryRecorded",
            "com.hls.identity.permissions.PermissionMatrixChanged",
            "com.hls.identity.activity.PasswordResetRequested",
            "com.hls.identity.activity.PasswordResetCompleted",
            "com.hls.identity.activity.SessionEnded",
            "com.hls.identity.activity.AccountLockChanged",
            "com.hls.identity.activity.AccountActivationChanged");

    // Main production classes only (target/classes) — not target/test-classes, which legitimately
    // use ApplicationEventPublisher in test fixtures to simulate redelivery of consumed events.
    private static final JavaClasses AUDIT_CLASSES =
            new ClassFileImporter().importPath(Path.of("target/classes/com/hls/audit"));

    @Test
    void auditConsumesOnlyTheDocumentedDomainEvents() {
        Set<String> consumed = new HashSet<>();
        for (var javaClass : AUDIT_CLASSES) {
            for (JavaMethod method : javaClass.getMethods()) {
                if (method.isAnnotatedWith(ApplicationModuleListener.class)) {
                    method.getParameters().forEach(p -> consumed.add(p.getRawType().getFullName()));
                }
            }
        }

        assertThat(consumed).isEqualTo(EXPECTED_CONSUMED_EVENTS);
    }

    @Test
    void auditPublishesNoEventsOfItsOwn() {
        boolean dependsOnEventPublisher = AUDIT_CLASSES.stream()
                .anyMatch(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency -> dependency
                                .getTargetClass()
                                .isEquivalentTo(ApplicationEventPublisher.class)));

        assertThat(dependsOnEventPublisher)
                .as("audit must only consume domain events, never publish its own (Constitution Principle VII)")
                .isFalse();
    }
}
