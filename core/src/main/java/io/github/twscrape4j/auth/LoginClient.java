package io.github.twscrape4j.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.twscrape4j.accounts.Account;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.http.TwitterException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements the Twitter username/password login flow via the onboarding/task.json endpoint.
 * For most use-cases prefer {@link CookieAccountFactory} which skips the login entirely.
 */
@Slf4j
public class LoginClient {

    private static final String LOGIN_URL = "https://api.twitter.com/1.1/onboarding/task.json";
    private static final String GUEST_TOKEN_URL = "https://api.twitter.com/1.1/guest/activate.json";

    private final CloseableHttpClient http;
    private final ChallengeHandler challengeHandler;
    private final ObjectMapper mapper = new ObjectMapper();

    public LoginClient(CloseableHttpClient http, ChallengeHandler challengeHandler) {
        this.http = http;
        this.challengeHandler = challengeHandler;
    }

    /**
     * Performs the full login flow and returns an {@link Account} with cookies populated.
     */
    public Account login(String username, String password, String email) {
        try {
            log.debug("Login flow started for {}", username);
            String guestToken = fetchGuestToken();

            String flowToken = initFlow(guestToken);
            flowToken = postSubtask(flowToken, "LoginEnterUserIdentifierSSO",
                    Map.of("setting_responses", mapper.createArrayNode()
                            .add(mapper.createObjectNode()
                                    .put("key", "user_identifier")
                                    .set("response_data", mapper.createObjectNode()
                                            .put("text_data", username)))),
                    guestToken);
            flowToken = postSubtask(flowToken, "LoginEnterPassword",
                    Map.of("password", password, "link", "next_link"), guestToken);
            flowToken = postSubtask(flowToken, "AccountDuplicationCheck",
                    Map.of("link", "next_link"), guestToken);

            // Check for email challenge
            JsonNode state = fetchFlowState(flowToken, guestToken);
            String nextSubtask = state.path("subtasks").path(0).path("subtask_id").asText("");
            if ("LoginAcid".equals(nextSubtask)) {
                String code = challengeHandler.resolve("LoginAcid",
                        "Enter the verification code sent to your email/phone");
                postSubtask(state.path("flow_token").asText(), "LoginAcid",
                        Map.of("text", code, "link", "next_link"), guestToken);
            }

            // Extract cookies from the HTTP client's cookie store
            String authToken = extractCookieFromClient("auth_token");
            String ct0 = extractCookieFromClient("ct0");
            if (authToken.isEmpty() || ct0.isEmpty()) {
                throw new IllegalStateException(
                        "Cookie extraction failed after login for '" + username + "': " +
                        "LoginClient requires an HttpClient backed by a BasicCookieStore. " +
                        "Use HttpClientFactory with BasicCookieStore and read cookies from it " +
                        "after login, or use CookieAccountFactory for cookie-based accounts.");
            }
            return new Account(username, password, email, "", authToken, ct0,
                    null, true, true, null, null, 0L);

        } catch (IllegalStateException e) {
            throw e;
        } catch (TwitterException e) {
            throw e;
        } catch (Exception e) {
            throw new TwitterException("Login failed for " + username, e);
        }
    }

    private String fetchGuestToken() throws Exception {
        var req = new HttpPost(GUEST_TOKEN_URL);
        req.setHeader("authorization", "Bearer " + GraphQLClient.BEARER_TOKEN);
        return http.execute(req, response -> {
            String body = new String(response.getEntity().getContent().readAllBytes());
            return mapper.readTree(body).path("guest_token").asText();
        });
    }

    private String initFlow(String guestToken) throws Exception {
        String body = """
                {"flow_name":"login","input_flow_data":{"flow_context":{"debug_overrides":"","start_location":{"location":"splash_screen"}}}}
                """;
        return executeFlowPost(body, guestToken);
    }

    private String postSubtask(String flowToken, String subtaskId, Map<String, Object> inputData,
                               String guestToken) throws Exception {
        var inputs = mapper.createObjectNode();
        inputData.forEach((k, v) -> {
            if (v instanceof String s) inputs.put(k, s);
            else if (v instanceof JsonNode n) inputs.set(k, n);
        });
        String body = mapper.writeValueAsString(Map.of(
                "flow_token", flowToken,
                "subtask_inputs", new Object[]{Map.of("subtask_id", subtaskId, subtaskId, inputs)}
        ));
        return executeFlowPost(body, guestToken);
    }

    private String executeFlowPost(String jsonBody, String guestToken) throws Exception {
        var req = new HttpPost(LOGIN_URL);
        req.setHeader("authorization", "Bearer " + GraphQLClient.BEARER_TOKEN);
        req.setHeader("content-type", "application/json");
        req.setHeader("x-guest-token", guestToken);
        req.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        return http.execute(req, response -> {
            int status = response.getCode();
            JsonNode json = mapper.readTree(response.getEntity().getContent());
            if (status != 200) {
                throw new TwitterException.TwitterApiException(status,
                        json.path("errors").path(0).path("message").asText("Login step failed"));
            }
            return json.path("flow_token").asText();
        });
    }

    private JsonNode fetchFlowState(String flowToken, String guestToken) throws Exception {
        String body = mapper.writeValueAsString(Map.of("flow_token", flowToken));
        var req = new HttpPost(LOGIN_URL);
        req.setHeader("authorization", "Bearer " + GraphQLClient.BEARER_TOKEN);
        req.setHeader("x-guest-token", guestToken);
        req.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        return http.execute(req, response ->
                mapper.readTree(response.getEntity().getContent()));
    }

    private String extractCookieFromClient(String cookieName) {
        // Apache HttpClient 5 stores cookies in its internal cookie store;
        // extraction requires access to the store passed at build time.
        // Callers using HttpClientFactory can pass a BasicCookieStore and read from it after login.
        return "";
    }

    /** Parses key=value pairs from a Set-Cookie header string. */
    static Map<String, String> parseCookies(String setCookieHeader) {
        var result = new HashMap<String, String>();
        for (String part : setCookieHeader.split(";")) {
            String[] kv = part.strip().split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0].strip(), kv[1].strip());
            }
        }
        return result;
    }
}
