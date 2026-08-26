package io.github.twscrape4j.http;

import tools.jackson.databind.ObjectMapper;
import io.github.twscrape4j.accounts.Account;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphQLClientTest {

    private CloseableHttpClient httpClient;
    private AccountHandle handle;
    private GraphQLClient client;

    @BeforeEach
    void setUp() {
        httpClient = mock(CloseableHttpClient.class);
        var account = new Account("alice", "pass", "e@t.com", "ep",
                "auth_tok", "ct0_val", null, true, true, null, null, 0L);
        handle = new AccountHandle(account, httpClient);
        client = new GraphQLClient();
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body, String remaining, String reset) throws Exception {
        doAnswer(inv -> {
            HttpClientResponseHandler<Object> handler = inv.getArgument(1);
            ClassicHttpResponse response = mock(ClassicHttpResponse.class);
            when(response.getCode()).thenReturn(status);

            HttpEntity entity = mock(HttpEntity.class);
            when(entity.getContent())
                    .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            when(response.getEntity()).thenReturn(entity);

            when(response.getFirstHeader("x-rate-limit-remaining"))
                    .thenReturn(remaining != null ? new BasicHeader("x-rate-limit-remaining", remaining) : null);
            when(response.getFirstHeader("x-rate-limit-reset"))
                    .thenReturn(reset != null ? new BasicHeader("x-rate-limit-reset", reset) : null);

            return handler.handleResponse(response);
        }).when(httpClient).execute(any(), any(HttpClientResponseHandler.class));
    }

    @Test
    void successResponseReturnsBody() throws Exception {
        String json = "{\"data\":{\"search_by_raw_query\":{\"search_timeline\":{\"timeline\":{}}}}}";
        stubResponse(200, json, "450", String.valueOf(Instant.now().plusSeconds(900).getEpochSecond()));

        var result = client.get(handle, "nK1dw4oV3k4w5TdtcAdSww", "SearchTimeline",
                Map.of("rawQuery", "#java"), null);

        assertNotNull(result.body());
        assertTrue(result.body().has("data"));
        assertEquals(450, result.rateLimitRemaining());
    }

    @Test
    void http429ThrowsRateLimitedException() throws Exception {
        long resetEpoch = Instant.now().plusSeconds(900).getEpochSecond();
        stubResponse(429, "{}", "0", String.valueOf(resetEpoch));

        assertThrows(TwitterException.RateLimitedException.class, () ->
                client.get(handle, "opId", "SearchTimeline", Map.of(), null));
    }

    @Test
    void errorCode88ThrowsRateLimitedException() throws Exception {
        String json = "{\"errors\":[{\"code\":88,\"message\":\"Rate limit exceeded\"}]}";
        stubResponse(200, json, "0", String.valueOf(Instant.now().plusSeconds(900).getEpochSecond()));

        assertThrows(TwitterException.RateLimitedException.class, () ->
                client.get(handle, "opId", "SearchTimeline", Map.of(), null));
    }

    @Test
    void errorCode64ThrowsAccountSuspendedException() throws Exception {
        String json = "{\"errors\":[{\"code\":64,\"message\":\"Your account is suspended\"}]}";
        stubResponse(200, json, null, null);

        assertThrows(TwitterException.AccountSuspendedException.class, () ->
                client.get(handle, "opId", "SearchTimeline", Map.of(), null));
    }

    @Test
    void non2xxWithoutKnownCodeThrowsApiException() throws Exception {
        stubResponse(403, "Forbidden", null, null);

        assertThrows(TwitterException.TwitterApiException.class, () ->
                client.get(handle, "opId", "SearchTimeline", Map.of(), null));
    }

    @Test
    void errorCode141ThrowsAccountSuspendedException() throws Exception {
        String json = "{\"errors\":[{\"code\":141,\"message\":\"Account suspended\"}]}";
        stubResponse(200, json, null, null);

        assertThrows(TwitterException.AccountSuspendedException.class, () ->
                client.get(handle, "opId", "SearchTimeline", Map.of(), null));
    }

    @Test
    void errorCode326ThrowsAccountSuspendedException() throws Exception {
        String json = "{\"errors\":[{\"code\":326,\"message\":\"Account locked\"}]}";
        stubResponse(200, json, null, null);

        assertThrows(TwitterException.AccountSuspendedException.class, () ->
                client.get(handle, "opId", "SearchTimeline", Map.of(), null));
    }

    @Test
    void nonJson2xxBodyThrowsApiException() throws Exception {
        stubResponse(200, "Not JSON at all", "100", String.valueOf(Instant.now().plusSeconds(900).getEpochSecond()));

        assertThrows(TwitterException.TwitterApiException.class, () ->
                client.get(handle, "opId", "SearchTimeline", Map.of(), null));
    }

    @Test
    void rateLimitHeadersParsedIntoResponse() throws Exception {
        long resetEpoch = Instant.now().plusSeconds(300).getEpochSecond();
        stubResponse(200, "{\"data\":{}}", "42", String.valueOf(resetEpoch));

        var result = client.get(handle, "opId", "Op", Map.of(), null);
        assertEquals(42, result.rateLimitRemaining());
        assertEquals(resetEpoch, result.rateLimitResetAt().getEpochSecond());
    }

    @Test
    void nullEntityOn429ThrowsRateLimitedExceptionNotNpe() throws Exception {
        long resetEpoch = Instant.now().plusSeconds(900).getEpochSecond();
        doAnswer(inv -> {
            HttpClientResponseHandler<Object> handler = inv.getArgument(1);
            ClassicHttpResponse response = mock(ClassicHttpResponse.class);
            when(response.getCode()).thenReturn(429);
            when(response.getEntity()).thenReturn(null);
            when(response.getFirstHeader("x-rate-limit-remaining")).thenReturn(null);
            when(response.getFirstHeader("x-rate-limit-reset"))
                    .thenReturn(new BasicHeader("x-rate-limit-reset", String.valueOf(resetEpoch)));
            return handler.handleResponse(response);
        }).when(httpClient).execute(any(), any(HttpClientResponseHandler.class));

        assertThrows(TwitterException.RateLimitedException.class, () ->
                client.get(handle, "opId", "SearchTimeline", Map.of(), null));
    }

    @Test
    void nullEntityOn2xxDoesNotNpe() throws Exception {
        doAnswer(inv -> {
            HttpClientResponseHandler<Object> handler = inv.getArgument(1);
            ClassicHttpResponse response = mock(ClassicHttpResponse.class);
            when(response.getCode()).thenReturn(204);
            when(response.getEntity()).thenReturn(null);
            when(response.getFirstHeader(anyString())).thenReturn(null);
            return handler.handleResponse(response);
        }).when(httpClient).execute(any(), any(HttpClientResponseHandler.class));

        // Null entity must not cause NullPointerException — the guard produces an empty body
        // string; Jackson 3 readTree("") succeeds, so the call completes normally.
        assertDoesNotThrow(() -> client.get(handle, "opId", "SearchTimeline", Map.of(), null));
    }
}
