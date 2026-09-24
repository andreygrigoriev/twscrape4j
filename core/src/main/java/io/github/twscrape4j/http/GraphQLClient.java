package io.github.twscrape4j.http;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
public class GraphQLClient {

    public static final String BEARER_TOKEN =
            "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA";
    private static final String BASE_URL = "https://x.com/i/api/graphql";
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Feature flags sent with every request; per-operation flags are merged on top.
     * X rejects requests with 400 when a required flag is missing. Sync with GQL_FEATURES in
     * <a href="https://github.com/vladkens/twscrape/blob/main/twscrape/api.py">twscrape's api.py</a>.
     */
    static final Map<String, Object> DEFAULT_FEATURES = Map.ofEntries(
            Map.entry("articles_preview_enabled", true),
            Map.entry("c9s_tweet_anatomy_moderator_badge_enabled", true),
            Map.entry("communities_web_enable_tweet_community_results_fetch", true),
            Map.entry("creator_subscriptions_quote_tweet_preview_enabled", false),
            Map.entry("creator_subscriptions_tweet_preview_api_enabled", true),
            Map.entry("freedom_of_speech_not_reach_fetch_enabled", true),
            Map.entry("graphql_is_translatable_rweb_tweet_is_translatable_enabled", true),
            Map.entry("longform_notetweets_consumption_enabled", true),
            Map.entry("longform_notetweets_inline_media_enabled", true),
            Map.entry("longform_notetweets_rich_text_read_enabled", true),
            Map.entry("responsive_web_edit_tweet_api_enabled", true),
            Map.entry("responsive_web_enhance_cards_enabled", false),
            Map.entry("responsive_web_graphql_exclude_directive_enabled", true),
            Map.entry("responsive_web_graphql_skip_user_profile_image_extensions_enabled", false),
            Map.entry("responsive_web_grok_community_note_auto_translation_is_enabled", false),
            Map.entry("responsive_web_graphql_timeline_navigation_enabled", true),
            Map.entry("responsive_web_grok_imagine_annotation_enabled", false),
            Map.entry("responsive_web_media_download_video_enabled", false),
            Map.entry("responsive_web_profile_redirect_enabled", true),
            Map.entry("responsive_web_twitter_article_tweet_consumption_enabled", true),
            Map.entry("rweb_tipjar_consumption_enabled", true),
            Map.entry("rweb_video_timestamps_enabled", true),
            Map.entry("standardized_nudges_misinfo", true),
            Map.entry("tweet_awards_web_tipping_enabled", false),
            Map.entry("tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled", true),
            Map.entry("tweet_with_visibility_results_prefer_gql_media_interstitial_enabled", false),
            Map.entry("tweetypie_unmention_optimization_enabled", true),
            Map.entry("verified_phone_label_enabled", false),
            Map.entry("view_counts_everywhere_api_enabled", true),
            Map.entry("responsive_web_grok_analyze_button_fetch_trends_enabled", false),
            Map.entry("premium_content_api_read_enabled", false),
            Map.entry("profile_label_improvements_pcf_label_in_post_enabled", false),
            Map.entry("responsive_web_grok_share_attachment_enabled", false),
            Map.entry("responsive_web_grok_analyze_post_followups_enabled", false),
            Map.entry("responsive_web_grok_image_annotation_enabled", false),
            Map.entry("responsive_web_grok_analysis_button_from_backend", false),
            Map.entry("responsive_web_jetfuel_frame", false),
            Map.entry("rweb_video_screen_enabled", true),
            Map.entry("responsive_web_grok_show_grok_translated_post", true)
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private final Function<AccountHandle, ClientTransaction> transactionLoader;
    private final Duration retryDelay;
    private final Map<String, ClientTransaction> transactions = new ConcurrentHashMap<>();

    public GraphQLClient() {
        this(handle -> ClientTransaction.load(handle.http()), Duration.ofSeconds(1));
    }

    GraphQLClient(Function<AccountHandle, ClientTransaction> transactionLoader, Duration retryDelay) {
        this.transactionLoader = transactionLoader;
        this.retryDelay = retryDelay;
    }

    /**
     * Executes a Twitter GraphQL GET request and returns the parsed response body.
     *
     * @param handle        the account + HTTP client to use
     * @param operationId   the Twitter-assigned operation ID (e.g. "nK1dw4oV3k4w5TdtcAdSww")
     * @param operationName the operation name (e.g. "SearchTimeline")
     * @param variables     request variables (will be JSON-encoded as a query param)
     * @param features      operation-specific feature flags merged over {@link #DEFAULT_FEATURES}
     *                      (may be null or empty)
     */
    public GraphQLResponse get(AccountHandle handle, String operationId, String operationName,
                               Map<String, Object> variables, Map<String, Object> features) {
        log.debug("GraphQL {} via account {}", operationName, handle.account().username());
        // X answers 404 when the x-client-transaction-id is stale or invalid: regenerate and retry.
        // https://github.com/vladkens/twscrape/issues/248
        for (int attempt = 1; ; attempt++) {
            try {
                return execute(handle, operationId, operationName, variables, features, attempt > 1);
            } catch (TwitterException.TwitterApiException e) {
                if (e.code() != 404) throw e;
                if (attempt >= MAX_ATTEMPTS) {
                    throw new TwitterException.TwitterApiException(404, operationName + " not found after "
                            + attempt + " attempts: the operation ID may be outdated or X rejected the "
                            + "x-client-transaction-id");
                }
                log.debug("{} returned 404, retrying with a new x-client-transaction-id", operationName);
                sleep(retryDelay);
            }
        }
    }

    private GraphQLResponse execute(AccountHandle handle, String operationId, String operationName,
                                    Map<String, Object> variables, Map<String, Object> features,
                                    boolean freshTransaction) {
        try {
            var mergedFeatures = new HashMap<>(DEFAULT_FEATURES);
            if (features != null) mergedFeatures.putAll(features);
            String path = BASE_URL + "/" + operationId + "/" + operationName;
            URI uri = new URIBuilder(path)
                    .addParameter("variables", mapper.writeValueAsString(variables))
                    .addParameter("features", mapper.writeValueAsString(mergedFeatures))
                    .build();

            var request = new HttpGet(uri);
            request.setHeader("x-client-transaction-id",
                    transaction(handle, freshTransaction).generate("GET", uri.getRawPath()));
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

    private ClientTransaction transaction(AccountHandle handle, boolean fresh) {
        String username = handle.account().username();
        if (!fresh) {
            var cached = transactions.get(username);
            if (cached != null) return cached;
        }
        try {
            var loaded = transactionLoader.apply(handle);
            transactions.put(username, loaded);
            return loaded;
        } catch (TwitterException e) {
            throw new TwitterException("Failed to generate x-client-transaction-id for "
                    + username + ": " + e.getMessage(), e);
        }
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TwitterException("Interrupted while retrying request", e);
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
