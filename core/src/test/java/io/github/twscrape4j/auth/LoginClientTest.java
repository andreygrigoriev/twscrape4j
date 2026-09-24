package io.github.twscrape4j.auth;

import io.github.twscrape4j.accounts.Account;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void parseCookiesExtractsKeyValuePairs() {
        String header = "auth_token=abc123; Path=/; Domain=.x.com; Secure";
        Map<String, String> cookies = LoginClient.parseCookies(header);
        assertEquals("abc123", cookies.get("auth_token"));
    }

    @Test
    void parseCookiesHandlesMultipleValues() {
        String header = "ct0=xyztoken; auth_token=tok456";
        Map<String, String> cookies = LoginClient.parseCookies(header);
        assertEquals("xyztoken", cookies.get("ct0"));
        assertEquals("tok456", cookies.get("auth_token"));
    }

    /**
     * Verifies that login() throws IllegalStateException instead of silently
     * producing an Account with empty credentials when the HttpClient has no
     * cookie store wired in (the extractCookieFromClient stub always returns "").
     */
    @SuppressWarnings("unchecked")
    @Test
    void loginThrowsIllegalStateWhenCookiesNotAvailable() throws Exception {
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        LoginClient loginClient = new LoginClient(httpClient, ChallengeHandler.stdin());

        // Responses for the sequential HTTP calls in the login flow:
        //  0 - fetchGuestToken
        //  1 - initFlow (executeFlowPost)
        //  2 - postSubtask LoginEnterUserIdentifierSSO
        //  3 - postSubtask LoginEnterPassword
        //  4 - postSubtask AccountDuplicationCheck
        //  5 - fetchFlowState (no LoginAcid challenge)
        String[] bodies = {
            "{\"guest_token\":\"testtoken\"}",
            "{\"flow_token\":\"ft1\"}",
            "{\"flow_token\":\"ft2\"}",
            "{\"flow_token\":\"ft3\"}",
            "{\"flow_token\":\"ft4\"}",
            "{\"subtasks\":[{\"subtask_id\":\"LoginSuccessSubtask\"}],\"flow_token\":\"ft5\"}"
        };
        AtomicInteger callIndex = new AtomicInteger(0);

        doAnswer(inv -> {
            int idx = callIndex.getAndIncrement();
            String responseBody = bodies[idx];
            HttpClientResponseHandler<Object> handler = inv.getArgument(1);
            ClassicHttpResponse response = mock(ClassicHttpResponse.class);
            when(response.getCode()).thenReturn(200);
            HttpEntity entity = mock(HttpEntity.class);
            // Use thenAnswer so each invocation gets a fresh stream
            when(entity.getContent()).thenAnswer(
                    i -> new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)));
            when(response.getEntity()).thenReturn(entity);
            return handler.handleResponse(response);
        }).when(httpClient).execute(any(), any(HttpClientResponseHandler.class));

        assertThrows(IllegalStateException.class, () ->
                loginClient.login("alice", "pass", "alice@test.com"));
    }
}
