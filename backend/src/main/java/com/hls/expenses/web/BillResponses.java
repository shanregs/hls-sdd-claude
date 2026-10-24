package com.hls.expenses.web;

import com.hls.files.api.FileStore.FileContent;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A bill is always sent as an attachment with {@code nosniff}, never shown inline by the server. */
final class BillResponses {

    private BillResponses() {}

    static ResponseEntity<byte[]> download(FileContent content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.file().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.file().name()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }
}
