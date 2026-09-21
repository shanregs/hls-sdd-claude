package com.hls.identity.internal;

/**
 * research.md §3: isolates the one piece that's genuinely a separate decision
 * (which India SMS gateway to use, per Requirements §12) behind an interface with
 * one swap point. Also used for the EMAIL-method MFA second factor (FR-018).
 */
public interface OtpSender {

    void sendCode(String identifier, String code);
}
