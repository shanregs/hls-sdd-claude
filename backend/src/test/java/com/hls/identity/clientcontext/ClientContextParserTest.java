package com.hls.identity.clientcontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T015: the header parser never throws, stores nothing for an invalid location, and treats
 * a missing or malformed client header as an ordinary web request (FR-021, FR-024, FR-025).
 */
class ClientContextParserTest {

    private static final Instant NOW = Instant.parse("2026-10-04T09:30:00Z");
    private final ClientContextParser parser = new ClientContextParser(Clock.fixed(NOW, ZoneOffset.UTC));

    private static String loc(String lat, String lng, String acc, Instant ts) {
        return "lat=" + lat + ";lng=" + lng + ";acc=" + acc + ";ts=" + ts.toEpochMilli();
    }

    private ClientContext android(String location, String status) {
        return parser.parse("android/1.0.0", location, status, null);
    }

    @Test
    void validLocationIsAvailable() {
        ClientContext context = android(loc("12.971599", "77.594566", "18.5", NOW.minusSeconds(2)), null);

        assertThat(context.source()).isEqualTo(ClientSource.ANDROID);
        assertThat(context.appVersion()).isEqualTo("1.0.0");
        assertThat(context.location().status()).isEqualTo(LocationStatus.AVAILABLE);
        assertThat(context.location().latitude()).isEqualTo(12.971599);
        assertThat(context.location().longitude()).isEqualTo(77.594566);
        assertThat(context.location().accuracyMeters()).isEqualTo(18.5);
        assertThat(context.location().capturedAt()).isEqualTo(NOW.minusSeconds(2));
        assertThat(context.deviceRooted()).isFalse();
    }

    @Test
    void boundaryValuesAreAccepted() {
        assertThat(android(loc("90", "180", "5000", NOW), null).location().status())
                .isEqualTo(LocationStatus.AVAILABLE);
        assertThat(android(loc("-90", "-180", "0", NOW), null).location().status())
                .isEqualTo(LocationStatus.AVAILABLE);
        assertThat(android(loc("1", "1", "1", NOW.minusSeconds(300)), null).location().status())
                .isEqualTo(LocationStatus.AVAILABLE);
    }

    @Test
    void outOfRangeOrImplausibleValuesBecomeInvalidAndDiscardTheValues() {
        for (String header : new String[] {
            loc("91", "77", "10", NOW),
            loc("-90.0001", "77", "10", NOW),
            loc("12", "181", "10", NOW),
            loc("12", "-181", "10", NOW),
            loc("12", "77", "-1", NOW),
            loc("12", "77", "5001", NOW),
            loc("12", "77", "10", NOW.minusSeconds(361)),
            loc("12", "77", "10", NOW.plusSeconds(361)),
            loc("NaN", "77", "10", NOW),
            loc("Infinity", "77", "10", NOW),
        }) {
            LocationCapture location = android(header, null).location();
            assertThat(location.status()).as(header).isEqualTo(LocationStatus.INVALID);
            assertThat(location.latitude()).isNull();
            assertThat(location.longitude()).isNull();
            assertThat(location.accuracyMeters()).isNull();
            assertThat(location.capturedAt()).isNull();
        }
    }

    @Test
    void malformedLocationHeaderIsInvalidNeverAnError() {
        for (String header : new String[] {
            "garbage", "lat=;lng=;acc=;ts=", "lat=12;lng=77", "lat=abc;lng=77;acc=1;ts=1", "lat=12;lng=77;acc=1;ts=x",
            "lat=12;lng=77;acc=1;ts=99999999999999999999", "=;=;=",
        }) {
            assertThat(android(header, null).location().status()).as(header).isEqualTo(LocationStatus.INVALID);
        }
    }

    @Test
    void statusOnlyHeaderCarriesTheReason() {
        assertThat(android(null, "PERMISSION_DENIED").location().status()).isEqualTo(LocationStatus.PERMISSION_DENIED);
        assertThat(android(null, "SERVICES_OFF").location().status()).isEqualTo(LocationStatus.SERVICES_OFF);
        assertThat(android(null, "NO_FIX").location().status()).isEqualTo(LocationStatus.NO_FIX);
        assertThat(android(null, "OTHER").location().status()).isEqualTo(LocationStatus.OTHER);
        assertThat(android(null, "no_fix").location().status()).isEqualTo(LocationStatus.NO_FIX);
    }

    @Test
    void aClientCannotClaimAStatusItShouldNotSend() {
        for (String status : new String[] {"AVAILABLE", "INVALID", "NOT_APPLICABLE", "whatever"}) {
            assertThat(android(null, status).location().status()).as(status).isEqualTo(LocationStatus.OTHER);
        }
    }

    @Test
    void androidRequestWithNoLocationHeadersIsOther() {
        assertThat(android(null, null).location().status()).isEqualTo(LocationStatus.OTHER);
        assertThat(android("  ", "  ").location().status()).isEqualTo(LocationStatus.OTHER);
    }

    @Test
    void locationHeaderWinsWhenBothAreSent() {
        ClientContext context = android(loc("12", "77", "10", NOW), "NO_FIX");
        assertThat(context.location().status()).isEqualTo(LocationStatus.AVAILABLE);
    }

    @Test
    void webRequestsAreNotApplicableAndNeverRooted() {
        ClientContext context = parser.parse(null, loc("12", "77", "10", NOW), "NO_FIX", "ROOTED_SUSPECTED");

        assertThat(context.source()).isEqualTo(ClientSource.WEB);
        assertThat(context.appVersion()).isNull();
        assertThat(context.location().status()).isEqualTo(LocationStatus.NOT_APPLICABLE);
        assertThat(context.deviceRooted()).isFalse();
    }

    @Test
    void malformedClientHeaderIsTreatedAsWeb() {
        for (String header : new String[] {"android", "android/", "android/1.0", "ios/1.0.0", "android/1.0.0; DROP", ""}) {
            assertThat(parser.parse(header, loc("12", "77", "10", NOW), null, null).source())
                    .as(header)
                    .isEqualTo(ClientSource.WEB);
        }
    }

    @Test
    void rootedFlagIsOnlyHonouredForAndroidAndOnlyForTheExactValue() {
        assertThat(parser.parse("android/1.0.0", null, null, "ROOTED_SUSPECTED").deviceRooted()).isTrue();
        assertThat(parser.parse("android/1.0.0", null, null, "rooted").deviceRooted()).isFalse();
        assertThat(parser.parse("android/1.0.0", null, null, null).deviceRooted()).isFalse();
    }

    @Test
    void locationCaptureInvariantIsEnforced() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new LocationCapture(LocationStatus.NO_FIX, 1.0, 2.0, 3.0, NOW));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new LocationCapture(LocationStatus.AVAILABLE, 1.0, null, 3.0, NOW));
    }
}
