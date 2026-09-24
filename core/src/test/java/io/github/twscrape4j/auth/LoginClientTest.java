package io.github.twscrape4j.auth;

import io.github.twscrape4j.accounts.Account;
import io.github.twscrape4j.http.TwitterException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginClientTest {

    @Test
    void cookieAccountFactoryCreatesValidAccount() {
        Account account = CookieAccountFactory.fromCookies("alice", "myauthtoken", "myct0");
        assertEquals("alice", account.username());
        assertEquals("myauthtoken", account.authToken());
        assertEquals("myct0", account.ct0());
        assertTrue(account.active());
        assertTrue(account.loggedIn());
    }

    @Test
    void cookieAccountFactoryRejectsBlankUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> CookieAccountFactory.fromCookies("", "tok", "ct0"));
    }

    @Test
    void cookieAccountFactoryRejectsBlankAuthToken() {
        assertThrows(IllegalArgumentException.class,
                () -> CookieAccountFactory.fromCookies("alice", "", "ct0"));
    }

    @Test
    void cookieAccountFactoryRejectsBlankCt0() {
        assertThrows(IllegalArgumentException.class,
                () -> CookieAccountFactory.fromCookies("alice", "tok", ""));
    }

    @Test
    void cookieAccountFactoryRejectsNullValues() {
        assertThrows(IllegalArgumentException.class,
                () -> CookieAccountFactory.fromCookies(null, "tok", "ct0"));
    }

    @Test
    void challengeHandlerStdinExists() {
        ChallengeHandler handler = ChallengeHandler.stdin();
        assertNotNull(handler);
    }

    private static final String GUEST = "{\"guest_token\":\"testtoken\"}";

    /** A flow response asking for the given subtasks, with flow token {@code token}. */
    private static String step(String token, String... subtaskIds) {
        var sb = new StringBuilder("{\"flow_token\":\"").append(token).append("\",\"subtasks\":[");
        for (int i = 0; i < subtaskIds.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"subtask_id\":\"").append(subtaskIds[i]).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static final String DONE = "{\"flow_token\":\"done\",\"subtasks\":[]}";

    /** Records the request bodies sent by the mocked client. */
    private static final class Flow {
        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<String> csrfHeaders = new ArrayList<>();
        final List<String> authTypeHeaders = new ArrayList<>();
        final CloseableHttpClient client;

        /**
         * Mocks the sequential HTTP calls of the login flow; {@code onResponse} runs with the 1-based call
         * index before each response, e.g. to simulate Set-Cookie headers.
         */
        @SuppressWarnings("unchecked")
        Flow(IntConsumer onResponse, String... responses) throws Exception {
            this(200, onResponse, responses);
        }

        @SuppressWarnings("unchecked")
        Flow(int lastStatus, IntConsumer onResponse, String... responses) throws Exception {
            client = mock(CloseableHttpClient.class);
            AtomicInteger callIndex = new AtomicInteger(0);
            doAnswer(inv -> {
                int idx = callIndex.getAndIncrement();
                assertTrue(idx < responses.length, "unexpected extra request #" + (idx + 1));
                HttpPost req = inv.getArgument(0);
                urls.add(req.getUri().toString());
                csrfHeaders.add(req.getFirstHeader("x-csrf-token") == null ? null
                        : req.getFirstHeader("x-csrf-token").getValue());
                authTypeHeaders.add(req.getFirstHeader("x-twitter-auth-type") == null ? null
                        : req.getFirstHeader("x-twitter-auth-type").getValue());
                bodies.add(req.getEntity() == null ? ""
                        : new String(req.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8));
                String responseBody = responses[idx];
                onResponse.accept(idx + 1);
                HttpClientResponseHandler<Object> handler = inv.getArgument(1);
                ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                when(response.getCode()).thenReturn(idx == responses.length - 1 ? lastStatus : 200);
                HttpEntity entity = mock(HttpEntity.class);
                when(entity.getContent()).thenAnswer(
                        i -> new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)));
                when(response.getEntity()).thenReturn(entity);
                return handler.handleResponse(response);
            }).when(client).execute(any(), any(HttpClientResponseHandler.class));
        }

        /** The subtask input sent in flow request {@code index} (0-based over all requests). */
        JsonNode input(int index) {
            return MAPPER.readTree(bodies.get(index)).path("subtask_inputs").path(0);
        }

        List<String> sentSubtaskIds() {
            var ids = new ArrayList<String>();
            for (String body : bodies) {
                if (body.isEmpty()) continue;
                for (JsonNode in : MAPPER.readTree(body).path("subtask_inputs")) {
                    ids.add(in.path("subtask_id").asText());
                }
            }
            return ids;
        }
    }

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static BasicClientCookie cookie(String name, String value) {
        var cookie = new BasicClientCookie(name, value);
        cookie.setDomain(".twitter.com");
        cookie.setPath("/");
        return cookie;
    }

    private static IntConsumer setSessionCookiesAt(BasicCookieStore store, int call) {
        return idx -> {
            if (idx == call) {
                store.addCookie(cookie("auth_token", "tok123"));
                store.addCookie(cookie("ct0", "csrf456"));
            }
        };
    }

    private static final ChallengeHandler NO_CHALLENGE = (type, prompt) -> {
        throw new AssertionError("unexpected challenge " + type);
    };

    @Test
    void typicalFlowIsDrivenBySubtaskIds() throws Exception {
        var store = new BasicCookieStore();
        var flow = new Flow(setSessionCookiesAt(store, 6),
                GUEST,
                step("ft1", "LoginJsInstrumentationSubtask"),
                step("ft2", "LoginEnterUserIdentifierSSO"),
                step("ft3", "LoginEnterPassword"),
                step("ft4", "AccountDuplicationCheck"),
                step("ft5", "LoginSuccessSubtask"),
                DONE);

        Account account = new LoginClient(flow.client, store, NO_CHALLENGE)
                .login("alice", "s3cret", "alice@test.com");

        assertEquals("alice", account.username());
        assertEquals("tok123", account.authToken());
        assertEquals("csrf456", account.ct0());
        assertTrue(account.loggedIn());
        assertTrue(flow.urls.get(1).endsWith("?flow_name=login"), flow.urls.get(1));
        assertEquals(List.of("LoginJsInstrumentationSubtask", "LoginEnterUserIdentifierSSO",
                "LoginEnterPassword", "AccountDuplicationCheck"), flow.sentSubtaskIds());
        // flow_token is chained from each response
        assertEquals("ft1", MAPPER.readTree(flow.bodies.get(2)).path("flow_token").asText());
        assertEquals("ft5", MAPPER.readTree(flow.bodies.get(6)).path("flow_token").asText());
        assertEquals(0, MAPPER.readTree(flow.bodies.get(6)).path("subtask_inputs").size());
        assertEquals("alice", flow.input(3).path("settings_list").path("setting_responses").path(0)
                .path("response_data").path("text_data").path("result").asText());
        assertEquals("s3cret", flow.input(4).path("enter_password").path("password").asText());
        assertEquals("AccountDuplicationCheck_false",
                flow.input(5).path("check_logged_in_account").path("link").asText());
    }

    @Test
    void ct0SetMidFlowIsSentAsCsrfHeaderOnLaterRequests() throws Exception {
        var store = new BasicCookieStore();
        var flow = new Flow(idx -> {
            if (idx == 2) {
                store.addCookie(cookie("ct0", "midflow"));
            } else if (idx == 4) {
                store.addCookie(cookie("auth_token", "tok123"));
            }
        },
                GUEST,
                step("ft1", "LoginEnterUserIdentifierSSO"),
                step("ft2", "LoginEnterPassword"),
                DONE);

        new LoginClient(flow.client, store, NO_CHALLENGE).login("alice", "pass", "alice@test.com");

        // no ct0 yet: guest token and init requests carry no CSRF header
        assertNull(flow.csrfHeaders.get(0));
        assertNull(flow.csrfHeaders.get(1));
        assertNull(flow.authTypeHeaders.get(1));
        // ct0 set by the init response: every later request carries the matching header
        assertEquals("midflow", flow.csrfHeaders.get(2));
        assertEquals("midflow", flow.csrfHeaders.get(3));
        assertEquals("OAuth2Session", flow.authTypeHeaders.get(2));
        assertEquals("OAuth2Session", flow.authTypeHeaders.get(3));
    }

    @Test
    void acidChallengeRightAfterPasswordUsesChallengeHandler() throws Exception {
        var store = new BasicCookieStore();
        var challenges = new ArrayList<String>();
        var flow = new Flow(setSessionCookiesAt(store, 5),
                GUEST,
                step("ft1", "LoginEnterUserIdentifierSSO"),
                step("ft2", "LoginEnterPassword"),
                "{\"flow_token\":\"ft3\",\"subtasks\":[{\"subtask_id\":\"LoginAcid\","
                        + "\"enter_text\":{\"hint_text\":\"Confirmation code\"}}]}",
                step("ft4", "LoginSuccessSubtask"),
                DONE);

        Account account = new LoginClient(flow.client, store, (type, prompt) -> {
            challenges.add(type);
            return "123456";
        }).login("alice", "pass", "alice@test.com");

        assertEquals("tok123", account.authToken());
        assertEquals(List.of("LoginAcid"), challenges);
        assertEquals(List.of("LoginEnterUserIdentifierSSO", "LoginEnterPassword", "LoginAcid"),
                flow.sentSubtaskIds());
        assertEquals("123456", flow.input(4).path("enter_text").path("text").asText());
    }

    @Test
    void acidAskingForEmailSendsEmailWithoutChallenge() throws Exception {
        var store = new BasicCookieStore();
        var flow = new Flow(setSessionCookiesAt(store, 4),
                GUEST,
                step("ft1", "LoginEnterPassword"),
                "{\"flow_token\":\"ft2\",\"subtasks\":[{\"subtask_id\":\"LoginAcid\","
                        + "\"enter_text\":{\"hint_text\":\"Email address\"}}]}",
                DONE);

        new LoginClient(flow.client, store, NO_CHALLENGE).login("alice", "pass", "alice@test.com");

        assertEquals("alice@test.com", flow.input(3).path("enter_text").path("text").asText());
    }

    @Test
    void alternateIdentifierPromptSendsEmail() throws Exception {
        var store = new BasicCookieStore();
        var flow = new Flow(setSessionCookiesAt(store, 5),
                GUEST,
                step("ft1", "LoginEnterUserIdentifierSSO"),
                step("ft2", "LoginEnterAlternateIdentifierSubtask"),
                step("ft3", "LoginEnterPassword"),
                DONE);

        new LoginClient(flow.client, store, NO_CHALLENGE).login("alice", "pass", "alice@test.com");

        assertEquals(List.of("LoginEnterUserIdentifierSSO", "LoginEnterAlternateIdentifierSubtask",
                "LoginEnterPassword"), flow.sentSubtaskIds());
        assertEquals("alice@test.com", flow.input(3).path("enter_text").path("text").asText());
    }

    @Test
    void unknownSubtaskFailsWithItsId() throws Exception {
        var flow = new Flow(i -> { }, GUEST, step("ft1", "ArkoseLogin"));

        var ex = assertThrows(TwitterException.LoginUnsupportedException.class, () ->
                new LoginClient(flow.client, new BasicCookieStore(), NO_CHALLENGE)
                        .login("alice", "pass", "alice@test.com"));
        assertTrue(ex.getMessage().contains("ArkoseLogin"), ex.getMessage());
    }

    @Test
    void knownSubtaskIsPickedWhenListedAfterUnknownOne() throws Exception {
        var store = new BasicCookieStore();
        var flow = new Flow(setSessionCookiesAt(store, 3),
                GUEST, step("ft1", "SomethingNew", "LoginEnterPassword"), DONE);

        new LoginClient(flow.client, store, NO_CHALLENGE).login("alice", "pass", "alice@test.com");

        assertEquals(List.of("LoginEnterPassword"), flow.sentSubtaskIds());
    }

    @Test
    void denyLoginSubtaskFails() throws Exception {
        var flow = new Flow(i -> { }, GUEST, step("ft1", "DenyLoginSubtask"));

        var ex = assertThrows(TwitterException.LoginFailedException.class, () ->
                new LoginClient(flow.client, new BasicCookieStore(), NO_CHALLENGE)
                        .login("alice", "pass", "alice@test.com"));
        assertTrue(ex.getMessage().contains("DenyLoginSubtask"), ex.getMessage());
    }

    @Test
    void repeatingFlowIsBounded() throws Exception {
        var responses = new String[LoginClient.MAX_STEPS + 2];
        responses[0] = GUEST;
        for (int i = 1; i < responses.length; i++) {
            responses[i] = step("ft" + i, "LoginJsInstrumentationSubtask");
        }
        var flow = new Flow(i -> { }, responses);

        var ex = assertThrows(TwitterException.LoginUnsupportedException.class, () ->
                new LoginClient(flow.client, new BasicCookieStore(), NO_CHALLENGE)
                        .login("alice", "pass", "alice@test.com"));
        assertTrue(ex.getMessage().contains("did not finish"), ex.getMessage());
    }

    @Test
    void rejectedStepThrowsApiException() throws Exception {
        var flow = new Flow(400, i -> { }, GUEST, step("ft1", "LoginEnterPassword"),
                "{\"errors\":[{\"message\":\"Wrong password!\"}]}");

        var ex = assertThrows(TwitterException.TwitterApiException.class, () ->
                new LoginClient(flow.client, new BasicCookieStore(), NO_CHALLENGE)
                        .login("alice", "wrong", "alice@test.com"));
        assertTrue(ex.getMessage().contains("Wrong password!"), ex.getMessage());
    }

    @Test
    void loginWithoutSessionCookiesThrowsLoginFailed() throws Exception {
        var store = new BasicCookieStore();
        store.addCookie(cookie("guest_id", "v1"));
        var flow = new Flow(i -> { }, GUEST, step("ft1", "LoginSuccessSubtask"), DONE);

        var ex = assertThrows(TwitterException.LoginFailedException.class, () ->
                new LoginClient(flow.client, store, NO_CHALLENGE)
                        .login("alice", "pass", "alice@test.com"));
        assertTrue(ex.getMessage().contains("alice"), ex.getMessage());
    }

    @Test
    void expiredCookiesAreIgnored() throws Exception {
        var store = new BasicCookieStore();
        var expired = cookie("auth_token", "old");
        expired.setExpiryDate(Instant.parse("2000-01-01T00:00:00Z"));
        store.addCookie(expired);
        store.addCookie(cookie("ct0", "csrf456"));
        var flow = new Flow(i -> { }, GUEST, step("ft1", "LoginSuccessSubtask"), DONE);

        assertThrows(TwitterException.LoginFailedException.class, () ->
                new LoginClient(flow.client, store, NO_CHALLENGE)
                        .login("alice", "pass", "alice@test.com"));
    }
}
