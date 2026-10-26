package com.hls.content.web;

import com.hls.content.internal.ContentQueries;
import com.hls.content.internal.ContentService;
import com.hls.content.internal.ContentService.Caller;
import com.hls.content.internal.ContentService.CreateRequest;
import com.hls.content.internal.ContentService.UpdateRequest;
import com.hls.files.api.FileLimits;
import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileStream;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.PageResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Content management (contracts/content-api.md), gated by the CONTENT grants and the content own rules. */
@RestController
@RequestMapping("/api/v1/content")
public class ContentController {

    private final ContentService service;
    private final ContentQueries queries;
    private final FileStore files;
    private final PermissionGuard guard;

    public ContentController(ContentService service, ContentQueries queries, FileStore files, PermissionGuard guard) {
        this.service = service;
        this.queries = queries;
        this.files = files;
        this.guard = guard;
    }

    public record CreateBody(String title, String category, String subject, String description, UUID authorUserId, LocalDate dueOn) {}

    public record UpdateBody(
            String title, String category, String subject, String description, UUID authorUserId, LocalDate dueOn, Long version) {}

    public record CommentBody(String comment) {}

    public record ReasonBody(String reason) {}

    public record TypeLimit(String extension, long maxBytes) {}

    public record Limits(List<TypeLimit> types) {}

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.CONTENT, action);
    }

    private static Caller caller(Jwt jwt) {
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @GetMapping
    public PageResponse<ContentQueries.Row> list(
            @RequestParam(required = false) String view,
            @RequestParam(required = false) UUID authorUserId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.list(caller(jwt), authorUserId, category, subject, status, view, from, to, page, size);
    }

    @GetMapping("/summary")
    public ContentQueries.Summary summary(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.summary(caller(jwt));
    }

    @GetMapping("/authors")
    public List<ContentQueries.Author> authors(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return queries.authors(caller(jwt));
    }

    @GetMapping("/limits")
    public Limits limits(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        Map<String, Long> sorted = new TreeMap<>(ContentService.LIMITS.maxBytesByExtension());
        List<TypeLimit> types = new ArrayList<>();
        sorted.forEach((ext, max) -> types.add(new TypeLimit(ext, max)));
        return new Limits(types);
    }

    @PostMapping
    public ResponseEntity<ContentQueries.Detail> create(@RequestBody CreateBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        Caller caller = caller(jwt);
        UUID id = service.create(
                caller, new CreateRequest(body.title(), body.category(), body.subject(), body.description(), body.authorUserId(), body.dueOn()));
        return ResponseEntity.status(201).body(queries.detail(caller, id));
    }

    @GetMapping("/{id}")
    public ContentQueries.Detail detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.detail(caller(jwt), id);
    }

    @PutMapping("/{id}")
    public ContentQueries.Detail update(@PathVariable UUID id, @RequestBody UpdateBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.update(
                caller,
                id,
                new UpdateRequest(
                        body.title(), body.category(), body.subject(), body.description(), body.authorUserId(), body.dueOn(), body.version()));
        return queries.detail(caller, id);
    }

    @PostMapping(path = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContentQueries.Detail> submit(
            @PathVariable UUID id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "note", required = false) String note,
            @AuthenticationPrincipal Jwt jwt)
            throws IOException {
        require(jwt, PermissionAction.EDIT);
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("Attach the content file. Content cannot be submitted without it.");
        }
        Caller caller = caller(jwt);
        try (var in = file.getInputStream()) {
            service.submit(caller, id, file.getOriginalFilename(), in, file.getSize(), note);
        }
        return ResponseEntity.status(201).body(queries.detail(caller, id));
    }

    @GetMapping("/{id}/versions/{number}/file")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id, @PathVariable int number, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        var version = queries.versionOf(caller(jwt), id, number);
        if (version.isRemoved()) {
            throw new NotFoundException("This file was removed.");
        }
        FileStream stream = files.openStream(version.getFileId()).orElseThrow(() -> new NotFoundException("File not found."));
        StreamingResponseBody body = out -> {
            try (var in = stream.bytes()) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(stream.file().name()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(stream.file().contentType()))
                .contentLength(stream.file().sizeBytes())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @DeleteMapping("/{id}/versions/{number}/file")
    public ContentQueries.Detail removeFile(
            @PathVariable UUID id, @PathVariable int number, @RequestBody ReasonBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.removeFile(caller, id, number, body.reason());
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/approve")
    public ContentQueries.Detail approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        Caller caller = caller(jwt);
        service.approve(caller, id);
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/return")
    public ContentQueries.Detail returnForRevision(@PathVariable UUID id, @RequestBody CommentBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        Caller caller = caller(jwt);
        service.returnForRevision(caller, id, body.comment());
        return queries.detail(caller, id);
    }
}
