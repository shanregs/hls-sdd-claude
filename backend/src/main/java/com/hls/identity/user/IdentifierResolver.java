package com.hls.identity.user;

import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves a password-login identifier as a phone number or a username (research.md §13,
 * FR-027): a normalized-phone lookup is tried first, falling back to a case-insensitive username
 * lookup on any miss (including a normalization failure), so a numeric username is never shadowed
 * by the phone check.
 */
@Component
public class IdentifierResolver {

    private final AppUserRepository appUserRepository;
    private final PhoneNumberNormalizer phoneNumberNormalizer;

    public IdentifierResolver(AppUserRepository appUserRepository, PhoneNumberNormalizer phoneNumberNormalizer) {
        this.appUserRepository = appUserRepository;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
    }

    public record Resolution(Optional<AppUser> user, String normalizedIdentifier) {}

    public Resolution resolve(String identifier) {
        Optional<AppUser> maybeUser = Optional.empty();
        String normalized = identifier;
        try {
            String phone = phoneNumberNormalizer.normalize(identifier);
            maybeUser = appUserRepository.findByPhone(phone);
            normalized = phone;
        } catch (PhoneNumberNormalizer.InvalidPhoneNumberException e) {
            // Not phone-shaped; fall through to the username lookup below.
        }
        if (maybeUser.isEmpty() && identifier != null) {
            maybeUser = appUserRepository.findByUsernameLower(identifier.toLowerCase(Locale.ROOT));
        }
        return new Resolution(maybeUser, normalized);
    }
}
