package com.hls.recruitment.marketing.web;

import com.hls.files.api.FileStore.FileContent;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.marketing.internal.AttachmentService;
import com.hls.school.api.CallerContext;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads a visit's file. It is always sent as an attachment with the type decided at upload and
 * {@code nosniff}, never shown inline, so an uploaded page can never run in the application's origin.
 */
@RestController
@RequestMapping("/api/v1/marketing/files")
public class FileController {

    private final AttachmentService attachments;
    private final PermissionGuard guard;

    public FileController(AttachmentService attachments, PermissionGuard guard) {
        this.attachments = attachments;
        this.guard = guard;
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID fileId, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MARKETING, PermissionAction.VIEW);
        FileContent file = attachments.download(CallerContext.userId(jwt), CallerContext.roles(jwt), fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.file().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.file().name()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.bytes());
    }
}
