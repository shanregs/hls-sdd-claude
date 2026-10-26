package com.hls.schoolbilling.web;

import com.hls.files.api.FileStore.FileContent;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.schoolbilling.internal.MouDocumentService;
import com.hls.schoolbilling.internal.MouDtos.ApprovalDto;
import com.hls.schoolbilling.internal.MouDtos.RemoveRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The signed document of a MoU (spec 031 US1). A download is always sent as an attachment with the type decided at upload
 * and {@code nosniff}, never shown inline; the stored file id is never a route.
 */
@RestController
@RequestMapping("/api/v1/school-contracts")
public class MouDocumentController {

    private final MouDocumentService documents;
    private final PermissionGuard guard;

    public MouDocumentController(MouDocumentService documents, PermissionGuard guard) {
        this.documents = documents;
        this.guard = guard;
    }

    @PostMapping("/contracts/{id}/documents")
    public ResponseEntity<ApprovalDto> upload(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        try {
            return ResponseEntity.status(201)
                    .body(documents.upload(
                            CallerContext.userId(jwt),
                            CallerContext.roles(jwt),
                            id,
                            file.getOriginalFilename(),
                            file.getBytes()));
        } catch (IOException e) {
            throw new InvalidInputException("The file could not be read.");
        }
    }

    @GetMapping("/contracts/{id}/documents/{documentId}/file")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id, @PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        FileContent file = documents.download(CallerContext.userId(jwt), CallerContext.roles(jwt), id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.file().contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.file().name()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.bytes());
    }

    @PostMapping("/contracts/{id}/documents/{documentId}/remove")
    public ApprovalDto remove(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @RequestBody RemoveRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return documents.remove(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                id,
                documentId,
                request == null ? null : request.reason());
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SCHOOL_CONTRACTS, action);
    }
}
