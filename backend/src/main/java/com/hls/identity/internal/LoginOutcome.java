package com.hls.identity.internal;

import java.util.UUID;

/** FR-007/FR-018: a login either succeeds outright or needs a second MFA step. */
public sealed interface LoginOutcome {

    record Authenticated(TokenService.IssuedTokens tokens) implements LoginOutcome {
    }

    record MfaRequired(UUID mfaChallengeId, MfaMethod method) implements LoginOutcome {
    }
}
