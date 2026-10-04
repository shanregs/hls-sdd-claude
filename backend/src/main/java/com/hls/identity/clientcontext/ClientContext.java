package com.hls.identity.clientcontext;

/**
 * Facts about the client that made the current request, built once per request from the {@code
 * X-HLS-*} headers (spec 018 research.md §5, §7). {@code audit} stores these on Login History, User
 * Activity and API Access entries; nothing here is ever used to allow or deny a request (FR-025).
 */
public record ClientContext(ClientSource source, String appVersion, LocationCapture location, boolean deviceRooted) {

    /** The request attribute under which {@link ClientContextFilter} stores the parsed context. */
    public static final String REQUEST_ATTRIBUTE = "com.hls.identity.clientcontext.ClientContext";

    public ClientContext {
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }

    /** A request from the web app, or outside any request: no location, never rooted. */
    public static ClientContext web() {
        return new ClientContext(
                ClientSource.WEB, null, LocationCapture.unavailable(LocationStatus.NOT_APPLICABLE), false);
    }

    public boolean isAndroid() {
        return source == ClientSource.ANDROID;
    }
}
