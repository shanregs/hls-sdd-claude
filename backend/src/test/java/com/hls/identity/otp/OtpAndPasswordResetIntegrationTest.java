package com.hls.identity.otp;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * User Story 2 (any-role OTP sign-in, FR-006) and the OTP-verification leg of User Story 5's
 * password reset by SMS or email (FR-016), Constitution v2.3.0.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OtpAndPasswordResetIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private RestTestClient client;
    private ListAppender<ILoggingEvent> smsAppender;
    private ListAppender<ILoggingEvent> emailAppender;

    private static final Pattern CODE_PATTERN = Pattern.compile("code is (\\d{6})");

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        smsAppender = attachAppender(SmsGateway.DevStubSmsGateway.class);
        emailAppender = attachAppender(EmailGateway.DevStubEmailGateway.class);
    }

    @AfterEach
    void tearDown() {
        detachAppender(SmsGateway.DevStubSmsGateway.class, smsAppender);
        detachAppender(EmailGateway.DevStubEmailGateway.class, emailAppender);
    }

    @Test
    void anyRoleSignsInWithOtp() {
        userAdminService.createUser("Teacher OTP", "9876511001", Set.of(Role.TEACHER), null, null);

        client.post()
                .uri("/api/v1/auth/otp/request")
                .body(new OtpDtos.OtpRequest("9876511001", OtpChannel.SMS, OtpPurpose.SIGN_IN))
                .exchange()
                .expectStatus()
                .isOk();

        String code = latestCode(smsAppender);

        var body = client.post()
                .uri("/api/v1/auth/otp/verify")
                .body(new OtpDtos.OtpVerifyRequest("9876511001", code, OtpPurpose.SIGN_IN, null))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.user().roles()).containsExactly("TEACHER");
    }

    @Test
    void adminCanAlsoSignInWithOtpEvenThoughAPasswordIsSet() {
        userAdminService.createUser("Admin Both", "9876511002", Set.of(Role.ADMIN), null, "some-strong-password");

        client.post()
                .uri("/api/v1/auth/otp/request")
                .body(new OtpDtos.OtpRequest("9876511002", OtpChannel.SMS, OtpPurpose.SIGN_IN))
                .exchange()
                .expectStatus()
                .isOk();
        String code = latestCode(smsAppender);

        var body = client.post()
                .uri("/api/v1/auth/otp/verify")
                .body(new OtpDtos.OtpVerifyRequest("9876511002", code, OtpPurpose.SIGN_IN, null))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.user().roles()).containsExactly("ADMIN");
    }

    @Test
    void unregisteredPhoneGetsNeutralResponseAndNoCode() {
        client.post()
                .uri("/api/v1/auth/otp/request")
                .body(new OtpDtos.OtpRequest("9876599998", OtpChannel.SMS, OtpPurpose.SIGN_IN))
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(smsAppender.list).isEmpty();
    }

    @Test
    void resetByEmailWorksWhenRegistered() {
        userAdminService.createUser(
                "Reset ByEmail",
                "9876511003",
                Set.of(Role.MANAGER),
                null,
                "old-password-123",
                "reset.byemail",
                "reset.byemail@example.com");

        var channels = client.get()
                .uri("/api/v1/auth/password-reset/channels?identifier=reset.byemail")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String[].class)
                .returnResult()
                .getResponseBody();
        assertThat(channels).containsExactlyInAnyOrder("SMS", "EMAIL");

        // The caller supplies the identifier (phone or username), not the raw email — the
        // destination is resolved server-side from the account (research.md §13 extended to
        // password reset).
        client.post()
                .uri("/api/v1/auth/otp/request")
                .body(new OtpDtos.OtpRequest("reset.byemail", OtpChannel.EMAIL, OtpPurpose.PASSWORD_RESET))
                .exchange()
                .expectStatus()
                .isOk();
        String code = latestCode(emailAppender);

        var resetTokenBody = client.post()
                .uri("/api/v1/auth/otp/verify")
                .body(new OtpDtos.OtpVerifyRequest("reset.byemail", code, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OtpDtos.ResetTokenResponse.class)
                .returnResult()
                .getResponseBody();

        client.post()
                .uri("/api/v1/auth/password-reset/complete")
                .body(new com.hls.identity.auth.AuthController.CompletePasswordResetRequest(
                        resetTokenBody.resetToken(), "brand-new-password-1"))
                .exchange()
                .expectStatus()
                .isOk();

        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("reset.byemail", "brand-new-password-1"))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void resetWithBothChannelsDeliversSameCodeAndSingleUseAcrossBoth() {
        userAdminService.createUser(
                "Reset ByBoth",
                "9876511005",
                Set.of(Role.MANAGER),
                null,
                "old-password-123",
                "reset.byboth",
                "reset.byboth@example.com");

        client.post()
                .uri("/api/v1/auth/otp/request")
                .body(new OtpDtos.OtpRequest("reset.byboth", OtpChannel.BOTH, OtpPurpose.PASSWORD_RESET))
                .exchange()
                .expectStatus()
                .isOk();

        String smsCode = latestCode(smsAppender);
        String emailCode = latestCode(emailAppender);
        assertThat(smsCode).isEqualTo(emailCode);

        // Verifying from the email message succeeds and returns a reset token.
        var resetTokenBody = client.post()
                .uri("/api/v1/auth/otp/verify")
                .body(new OtpDtos.OtpVerifyRequest("reset.byboth", emailCode, OtpPurpose.PASSWORD_RESET, OtpChannel.BOTH))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OtpDtos.ResetTokenResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(resetTokenBody.resetToken()).isNotBlank();

        // The identical code, now presented again (as if from the SMS message), is refused —
        // sending to both channels never allows the same code to complete two resets.
        client.post()
                .uri("/api/v1/auth/otp/verify")
                .body(new OtpDtos.OtpVerifyRequest("reset.byboth", smsCode, OtpPurpose.PASSWORD_RESET, OtpChannel.BOTH))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void usingOneUsersCodeDoesNotUseUpAnotherUsersCodeThatHappensToBeTheSameNumber() {
        userAdminService.createUser(
                "Collide First", "9876511101", Set.of(Role.MANAGER), null, "old-password-123", null, null);
        userAdminService.createUser(
                "Collide Second", "9876511102", Set.of(Role.MANAGER), null, "old-password-123", null, null);
        for (String phone : new String[] {"9876511101", "9876511102"}) {
            client.post()
                    .uri("/api/v1/auth/otp/request")
                    .body(new OtpDtos.OtpRequest(phone, OtpChannel.SMS, OtpPurpose.PASSWORD_RESET))
                    .exchange()
                    .expectStatus()
                    .isOk();
        }
        // Give the second user's stored code the same hash as the first user's (the code is only six digits,
        // so two users can really be sent the same number).
        String sharedHash = jdbc.queryForObject(
                "select code_hash from one_time_code where destination = '9876511101'", String.class);
        jdbc.update("update one_time_code set code_hash = ? where destination = '9876511102'", sharedHash);
        Matcher first = CODE_PATTERN.matcher(smsAppender.list.get(smsAppender.list.size() - 2).getFormattedMessage());
        assertThat(first.find()).isTrue();
        String firstUsersCode = first.group(1);

        client.post()
                .uri("/api/v1/auth/otp/verify")
                .body(new OtpDtos.OtpVerifyRequest("9876511101", firstUsersCode, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS))
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(jdbc.queryForObject(
                        "select used_at is null from one_time_code where destination = '9876511102'", Boolean.class))
                .as("the second user's code was left alone")
                .isTrue();
        client.post()
                .uri("/api/v1/auth/otp/verify")
                .body(new OtpDtos.OtpVerifyRequest("9876511102", firstUsersCode, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void bothChannelForUserWithNoEmailBehavesLikeSmsOnly() {
        userAdminService.createUser("Both NoEmail", "9876511006", Set.of(Role.MANAGER), null, "some-password-1");

        client.post()
                .uri("/api/v1/auth/otp/request")
                .body(new OtpDtos.OtpRequest("9876511006", OtpChannel.BOTH, OtpPurpose.PASSWORD_RESET))
                .exchange()
                .expectStatus()
                .isOk();

        String code = latestCode(smsAppender);
        assertThat(emailAppender.list).isEmpty();

        client.post()
                .uri("/api/v1/auth/otp/verify")
                .body(new OtpDtos.OtpVerifyRequest("9876511006", code, OtpPurpose.PASSWORD_RESET, OtpChannel.BOTH))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void userWithNoEmailIsOfferedOnlySms() {
        userAdminService.createUser("No Email", "9876511004", Set.of(Role.MANAGER), null, "some-password-1");

        var channels = client.get()
                .uri("/api/v1/auth/password-reset/channels?identifier=9876511004")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String[].class)
                .returnResult()
                .getResponseBody();

        assertThat(channels).containsExactly("SMS");
    }

    private static String latestCode(ListAppender<ILoggingEvent> appender) {
        assertThat(appender.list).isNotEmpty();
        String message = appender.list.get(appender.list.size() - 1).getFormattedMessage();
        Matcher matcher = CODE_PATTERN.matcher(message);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static ListAppender<ILoggingEvent> attachAppender(Class<?> loggerClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        return appender;
    }

    private static void detachAppender(Class<?> loggerClass, ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        logger.detachAppender(appender);
    }
}
