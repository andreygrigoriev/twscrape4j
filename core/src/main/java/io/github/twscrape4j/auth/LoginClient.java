package io.github.twscrape4j.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.github.twscrape4j.accounts.Account;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.http.TwitterException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Implements the Twitter username/password login flow via the onboarding/task.json endpoint.
 *
 * <p>The flow is driven by the server: every response lists the next {@code subtasks[].subtask_id}
 * and this client answers the first one it knows (the same approach as the Python twscrape project).
 * The login flow is undocumented and changes without notice, so this is best-effort; for most
 * use-cases prefer {@link CookieAccountFactory}, which skips the login entirely.
 */
@Slf4j
public class LoginClient {

    private static final String LOGIN_URL = "https://api.twitter.com/1.1/onboarding/task.json";
    private static final String GUEST_TOKEN_URL = "https://api.twitter.com/1.1/guest/activate.json";

    /** Upper bound on flow steps, protects against a server that keeps repeating subtasks. */
    static final int MAX_STEPS = 20;

    static final String JS_INSTRUMENTATION = "LoginJsInstrumentationSubtask";
    static final String ENTER_USER_IDENTIFIER = "LoginEnterUserIdentifierSSO";
    static final String ENTER_ALTERNATE_IDENTIFIER = "LoginEnterAlternateIdentifierSubtask";
    static final String ENTER_PASSWORD = "LoginEnterPassword";
    static final String ACCOUNT_DUPLICATION_CHECK = "AccountDuplicationCheck";
    static final String LOGIN_ACID = "LoginAcid";
    static final String TWO_FACTOR = "LoginTwoFactorAuthChallenge";
    static final String DENY_LOGIN = "DenyLoginSubtask";
    static final String LOGIN_SUCCESS = "LoginSuccessSubtask";

    private static final Set<String> KNOWN_SUBTASKS = Set.of(JS_INSTRUMENTATION, ENTER_USER_IDENTIFIER,
            ENTER_ALTERNATE_IDENTIFIER, ENTER_PASSWORD, ACCOUNT_DUPLICATION_CHECK, LOGIN_ACID, TWO_FACTOR,
            DENY_LOGIN, LOGIN_SUCCESS);

    private static final String INIT_BODY = """
            {"input_flow_data":{"flow_context":{"debug_overrides":{},"start_location":{"location":"splash_screen"}}}}
            """;

    private final CloseableHttpClient http;
    private final CookieStore cookies;
    private final ChallengeHandler challengeHandler;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    /**
     * @param http             client used for the login flow; must use {@code cookies} as its cookie store
     * @param cookies          the cookie store backing {@code http}; session cookies are read from it after login
     * @param challengeHandler resolves email/phone verification challenges
     */
    public LoginClient(CloseableHttpClient http, CookieStore cookies, ChallengeHandler challengeHandler) {
        this.http = http;
        this.cookies = cookies;
        this.challengeHandler = challengeHandler;
    }

    /**
     * Performs the full login flow and returns an {@link Account} with cookies populated.
     *
     * @throws TwitterException.LoginFailedException      if X denies the login or no session cookies were set
     * @throws TwitterException.LoginUnsupportedException if X asks for a step this client does not support or
     *                                                    the flow does not follow the expected protocol
     */
    public Account login(String username, String password, String email) {
        try {
            log.debug("Login flow started for {}", username);
            String guestToken = fetchGuestToken();
            JsonNode state = executeFlowPost(LOGIN_URL + "?flow_name=login", INIT_BODY, guestToken);

            for (int step = 0; ; step++) {
                List<String> ids = subtaskIds(state);
                if (ids.isEmpty()) {
                    break; // flow finished
                }
                if (step >= MAX_STEPS) {
                    throw new TwitterException.LoginUnsupportedException(username,
                            "the login flow did not finish after " + MAX_STEPS + " steps (last: " + ids + ")");
                }
                JsonNode subtask = firstKnownSubtask(state);
                if (subtask == null) {
                    throw new TwitterException.LoginUnsupportedException(username,
                            "unsupported login step " + ids + "; use cookie-based login instead");
                }
                String flowToken = state.path("flow_token").asText("");
                if (flowToken.isEmpty()) {
                    throw new TwitterException.LoginUnsupportedException(username,
                            "the login flow response has no flow_token");
                }
                String id = subtask.path("subtask_id").asText();
                log.debug("Login step {}: {}", step, id);
                if (DENY_LOGIN.equals(id)) {
                    throw new TwitterException.LoginFailedException(username, "X denied the login (" + id + ")");
                }
                if (LOGIN_SUCCESS.equals(id)) {
                    postSubtaskInputs(flowToken, mapper.createArrayNode(), guestToken);
                    break;
                }
                ObjectNode input = subtaskInput(id, subtask, username, password, email);
                state = postSubtaskInputs(flowToken, mapper.createArrayNode().add(input), guestToken);
            }

            // Session cookies set by the login flow responses
            String authToken = cookieValue("auth_token");
            String ct0 = cookieValue("ct0");
            if (authToken.isEmpty() || ct0.isEmpty()) {
                throw new TwitterException.LoginFailedException(username,
                        "the login flow did not return session cookies (auth_token/ct0)");
            }
            return new Account(username, password, email, "", authToken, ct0,
                    null, true, true, null, null, 0L);

        } catch (TwitterException e) {
            throw e;
        } catch (Exception e) {
            throw new TwitterException("Login failed for " + username, e);
        }
    }

    /** Builds the answer for subtask {@code id}, including its {@code subtask_id}. */
    private ObjectNode subtaskInput(String id, JsonNode subtask, String username, String password, String email) {
        ObjectNode input = mapper.createObjectNode().put("subtask_id", id);
        switch (id) {
            case JS_INSTRUMENTATION -> input.putObject("js_instrumentation")
                    .put("response", "{}").put("link", "next_link");
            case ENTER_USER_IDENTIFIER -> {
                ObjectNode settings = input.putObject("settings_list");
                settings.putArray("setting_responses").addObject()
                        .put("key", "user_identifier")
                        .putObject("response_data").putObject("text_data").put("result", username);
                settings.put("link", "next_link");
            }
            case ENTER_ALTERNATE_IDENTIFIER -> enterText(input, isBlank(email) ? username : email);
            case ENTER_PASSWORD -> input.putObject("enter_password")
                    .put("password", password).put("link", "next_link");
            case ACCOUNT_DUPLICATION_CHECK -> input.putObject("check_logged_in_account")
                    .put("link", "AccountDuplicationCheck_false");
            case LOGIN_ACID -> {
                String hint = subtask.path("enter_text").path("hint_text").asText("");
                boolean asksForEmail = !hint.isBlank() && !hint.toLowerCase(Locale.ROOT).contains("code");
                if (asksForEmail && !isBlank(email)) {
                    enterText(input, email);
                } else {
                    enterText(input, challengeHandler.resolve(LOGIN_ACID,
                            "Enter the verification code sent to your email/phone"));
                }
            }
            case TWO_FACTOR -> enterText(input, challengeHandler.resolve(TWO_FACTOR,
                    "Enter the two-factor authentication code"));
            default -> throw new IllegalStateException("unhandled subtask " + id);
        }
        return input;
    }

    private static void enterText(ObjectNode input, String text) {
        input.putObject("enter_text").put("text", text).put("link", "next_link");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static List<String> subtaskIds(JsonNode state) {
        var ids = new ArrayList<String>();
        for (JsonNode subtask : state.path("subtasks")) {
            String id = subtask.path("subtask_id").asText("");
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static JsonNode firstKnownSubtask(JsonNode state) {
        for (JsonNode subtask : state.path("subtasks")) {
            if (KNOWN_SUBTASKS.contains(subtask.path("subtask_id").asText(""))) {
                return subtask;
            }
        }
        return null;
    }

    private String fetchGuestToken() throws Exception {
        var req = new HttpPost(GUEST_TOKEN_URL);
        req.setHeader("authorization", "Bearer " + GraphQLClient.BEARER_TOKEN);
        return http.execute(req, response -> {
            String body = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
            return mapper.readTree(body).path("guest_token").asText();
        });
    }

    private JsonNode postSubtaskInputs(String flowToken, JsonNode subtaskInputs, String guestToken)
            throws Exception {
        ObjectNode body = mapper.createObjectNode().put("flow_token", flowToken);
        body.set("subtask_inputs", subtaskInputs);
        return executeFlowPost(LOGIN_URL, mapper.writeValueAsString(body), guestToken);
    }

    private JsonNode executeFlowPost(String url, String jsonBody, String guestToken) throws Exception {
        var req = new HttpPost(url);
        req.setHeader("authorization", "Bearer " + GraphQLClient.BEARER_TOKEN);
        req.setHeader("content-type", "application/json");
        req.setHeader("x-guest-token", guestToken);
        // Once X sets ct0 mid-flow the cookie store sends it back; the header must match or X answers 403
        String ct0 = cookieValue("ct0");
        if (!ct0.isEmpty()) {
            req.setHeader("x-csrf-token", ct0);
            req.setHeader("x-twitter-auth-type", "OAuth2Session");
        }
        req.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        return http.execute(req, response -> {
            int status = response.getCode();
            JsonNode json = mapper.readTree(response.getEntity().getContent());
            if (status != 200) {
                throw new TwitterException.TwitterApiException(status,
                        json.path("errors").path(0).path("message").asText("Login step failed"));
            }
            return json;
        });
    }

    /** Returns the value of the newest non-expired cookie named {@code cookieName}, or {@code ""}. */
    private String cookieValue(String cookieName) {
        Instant now = Instant.now();
        String value = "";
        for (Cookie cookie : cookies.getCookies()) {
            if (cookieName.equals(cookie.getName()) && !cookie.isExpired(now)
                    && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                value = cookie.getValue();
            }
        }
        return value;
    }
}
