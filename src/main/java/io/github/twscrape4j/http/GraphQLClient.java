package io.github.twscrape4j.http;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Slf4j
public class GraphQLClient {

    public static final String BEARER_TOKEN =
            "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA";
    private static final String BASE_URL = "https://x.com/i/api/graphql";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Executes a Twitter GraphQL GET request and returns the parsed response body.
     *
     * @param handle        the account + HTTP client to use
     * @param operationId   the Twitter-assigned operation ID (e.g. "nK1dw4oV3k4w5TdtcAdSww")
     * @param operationName the operation name (e.g. "SearchTimeline")
     * @param variables     request variables (will be JSON-encoded as a query param)
     * @param features      feature flags (may be null or empty)
     */
    public GraphQLResponse get(AccountHandle handle, String operationId, String operationName,
                               Map<String, Object> variables, Map<String, Object> features) {
        log.debug("GraphQL {} via account {}", operationName, handle.account().username());
        try {
            URI uri = new URIBuilder(BASE_URL + "/" + operationId + "/" + operationName)
                    .addParameter("variables", mapper.writeValueAsString(variables))
                    .addParameter("features", mapper.writeValueAsString(features != null ? features : Map.of()))
                    .build();

            var request = new HttpGet(uri);
            request.setHeader("authorization", "Bearer " + BEARER_TOKEN);
            request.setHeader("x-csrf-token", handle.account().ct0());
            request.setHeader("x-twitter-auth-type", "OAuth2Session");
            request.setHeader("x-twitter-client-language", "en");
            request.setHeader("x-twitter-active-user", "yes");
            request.setHeader("content-type", "application/json");
            request.setHeader("cookie", "auth_token=" + handle.account().authToken()
                    + "; ct0=" + handle.account().ct0());

            return handle.http().execute(request, response -> {
                int status = response.getCode();

                // Parse rate limit headers before reading the entity (no entity dependency)
                Instant resetAt = parseResetAt(response);
                int remaining = parseRemaining(response);

                if (status == 429) {
                    log.warn("Rate limited for {} (account {})", operationName, handle.account().username());
                    throw new TwitterException.RateLimitedException(resetAt);
                }

                // Guard against null entity (e.g. 204 No Content or responses with no body)
                var entity = response.getEntity();
                String body = (entity != null) ? new String(entity.getContent().readAllBytes()) : "";

                if (status < 200 || status >= 300) {
                    throw new TwitterException.TwitterApiException(status, body);
                }

                JsonNode json;
                try {
                    json = mapper.readTree(body);
                } catch (Exception e) {
                    throw new TwitterException.TwitterApiException(status, "Non-JSON response: " + body);
                }

                // Check for API-level errors in the JSON body
                if (json.has("errors")) {
                    for (JsonNode error : json.get("errors")) {
                        int code = error.path("code").asInt(-1);
                        if (code == 88) {
                            throw new TwitterException.RateLimitedException(resetAt);
                        }
                        if (code == 64 || code == 141 || code == 326) {
                            throw new TwitterException.AccountSuspendedException(handle.account().username());
                        }
                    }
                }

                return new GraphQLResponse(json, remaining, resetAt);
            });
        } catch (TwitterException e) {
            throw e;
        } catch (Exception e) {
            throw new TwitterException("GraphQL request failed: " + operationName, e);
        }
    }

    private Instant parseResetAt(org.apache.hc.core5.http.HttpResponse response) {
        var header = response.getFirstHeader("x-rate-limit-reset");
        if (header == null) return Instant.now().plusSeconds(900);
        try {
            return Instant.ofEpochSecond(Long.parseLong(header.getValue()));
        } catch (NumberFormatException e) {
            return Instant.now().plusSeconds(900);
        }
    }

    private int parseRemaining(org.apache.hc.core5.http.HttpResponse response) {
        var header = response.getFirstHeader("x-rate-limit-remaining");
        if (header == null) return -1;
        try {
            return Integer.parseInt(header.getValue());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public record GraphQLResponse(JsonNode body, int rateLimitRemaining, Instant rateLimitResetAt) {}
}
