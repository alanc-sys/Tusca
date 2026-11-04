package com.rutatur.match.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rutatur.common.dto.ContactDto;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.match.config.MatchProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakAdminClientTest {

    private static final MatchProperties PROPERTIES = new MatchProperties(
            null,
            "http://keycloak.test:8080",
            "rutatur-realm",
            "rutatur-match",
            "secret",
            null,
            null,
            null,
            null);
    private static final String TOKEN_URL =
            "http://keycloak.test:8080/realms/rutatur-realm/protocol/openid-connect/token";
    private static final String USER_URL =
            "http://keycloak.test:8080/admin/realms/rutatur-realm/users/user-1";

    @Test
    void clientCredentialsTokenIsReusedUntilExpiry() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak.test:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-03T12:00:00Z"));
        KeycloakAdminClient client = new KeycloakAdminClient(builder.build(), PROPERTIES, clock);

        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(token("token-1", 300), MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
                .andRespond(withSuccess(user("user-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
                .andRespond(withSuccess(user("user-1"), MediaType.APPLICATION_JSON));

        ContactDto first = client.lookupUser("user-1");
        ContactDto second = client.lookupUser("user-1");

        server.verify();
        assertThat(first.userId()).isEqualTo("user-1");
        assertThat(second.email()).isEqualTo("user-1@example.com");
    }

    @Test
    void tokenIsFetchedAgainShortlyBeforeExpiry() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak.test:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-03T12:00:00Z"));
        KeycloakAdminClient client = new KeycloakAdminClient(builder.build(), PROPERTIES, clock);

        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(token("token-1", 60), MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
                .andRespond(withSuccess(user("user-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(token("token-2", 60), MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
                .andRespond(withSuccess(user("user-1"), MediaType.APPLICATION_JSON));

        client.lookupUser("user-1");
        clock.advance(Duration.ofSeconds(31));
        client.lookupUser("user-1");

        server.verify();
    }

    @Test
    void undecodableTokenBodyBecomesContactProviderUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak.test:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = new KeycloakAdminClient(
                builder.build(), PROPERTIES, new MutableClock(Instant.parse("2026-11-03T12:00:00Z")));

        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.lookupUser("user-1"))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.CONTACT_PROVIDER_UNAVAILABLE);
    }

    private static String token(String accessToken, long expiresIn) {
        return "{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn + "}";
    }

    private static String user(String id) {
        return "{\"id\":\"" + id + "\",\"username\":\"tester\",\"email\":\"" + id + "@example.com\"}";
    }

    /** Fixed-origin clock the test can move forward. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
