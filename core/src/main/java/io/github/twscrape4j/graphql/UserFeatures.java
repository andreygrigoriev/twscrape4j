package io.github.twscrape4j.graphql;

import java.util.Map;

/** Extra feature flags required by the single-user lookups (UserByRestId, UserByScreenName). */
final class UserFeatures {

    static final Map<String, Object> FLAGS = Map.of(
            "highlights_tweets_tab_ui_enabled", true,
            "hidden_profile_likes_enabled", true,
            "creator_subscriptions_tweet_preview_api_enabled", true,
            "hidden_profile_subscriptions_enabled", true,
            "subscriptions_verification_info_verified_since_enabled", true,
            "subscriptions_verification_info_is_identity_verified_enabled", false,
            "responsive_web_twitter_article_notes_tab_enabled", false,
            "subscriptions_feature_can_gift_premium", false,
            "profile_label_improvements_pcf_label_in_post_enabled", false
    );

    private UserFeatures() {}
}
