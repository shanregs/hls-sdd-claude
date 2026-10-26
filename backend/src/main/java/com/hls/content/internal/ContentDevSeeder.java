package com.hls.content.internal;

import com.hls.content.internal.ContentService.Caller;
import com.hls.content.internal.ContentService.CreateRequest;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}; runs only while there is no content): content in every
 * status, written by the demo Content Contributor and reviewed by the demo Director, with small PDF files.
 */
@Component
@Order(96)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
class ContentDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentDevSeeder.class);

    private final ContentService content;
    private final ContentRepository repository;
    private final RoleAssignmentRepository roles;
    private final PermissionMatrixService matrix;

    ContentDevSeeder(
            ContentService content, ContentRepository repository, RoleAssignmentRepository roles, PermissionMatrixService matrix) {
        this.content = content;
        this.repository = repository;
        this.roles = roles;
        this.matrix = matrix;
    }

    private UUID first(Role role) {
        List<UUID> ids = roles.userIdsWithRole(role);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static byte[] pdf(String text) {
        return ("%PDF-1.4 " + text).getBytes();
    }

    private void submit(Caller caller, UUID id, String name) {
        byte[] bytes = pdf(name);
        content.submit(caller, id, name + ".pdf", new ByteArrayInputStream(bytes), bytes.length, "Demo version");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        // the default grants are seeded after the demo users; content needs them to decide who may write and review
        matrix.seedDefaults();
        UUID contributor = first(Role.CONTENT_CONTRIBUTOR);
        UUID director = first(Role.DIRECTOR);
        if (contributor == null || director == null) {
            return;
        }
        Caller author = new Caller(contributor, Set.of(Role.CONTENT_CONTRIBUTOR));
        Caller reviewer = new Caller(director, Set.of(Role.DIRECTOR));

        content.create(author, new CreateRequest("Phonics worksheet draft", "Worksheet", "English", "Letters a to e", null, null));

        UUID waiting = content.create(author, new CreateRequest("Counting song lyrics", "Song", "Maths", "Numbers 1 to 20", null, null));
        submit(author, waiting, "counting-song");

        UUID approved = content.create(author, new CreateRequest("Colours flashcards", "Flashcards", "English", "Primary colours", null, null));
        submit(author, approved, "colours-flashcards");
        content.approve(reviewer, approved);

        UUID returned = content.create(author, new CreateRequest("Rhymes pack", "Audio notes", "English", "Ten nursery rhymes", null, null));
        submit(author, returned, "rhymes-pack");
        content.returnForRevision(reviewer, returned, "Please add the lyrics for rhyme 7.");

        log.info("[DEV SEED] Content demo data ready: one draft, one submitted, one approved and one returned.");
    }
}
