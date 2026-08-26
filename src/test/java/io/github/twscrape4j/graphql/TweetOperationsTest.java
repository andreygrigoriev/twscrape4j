package io.github.twscrape4j.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twscrape4j.accounts.Account;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.AccountHandle;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Tweet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TweetOperationsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private GraphQLClient graphQLClient;
    private AccountPool pool;

    @BeforeEach
    void setUp() {
        graphQLClient = mock(GraphQLClient.class);
        pool = mock(AccountPool.class);
        var account = new Account("alice", "", "", "", "tok", "ct0",
                null, true, true, null, null, 0L);
        when(pool.acquire(anyString())).thenReturn(
                new AccountHandle(account, mock(CloseableHttpClient.class)));
    }

    private GraphQLClient.GraphQLResponse response(String json) throws Exception {
        return new GraphQLClient.GraphQLResponse(MAPPER.readTree(json), 100, Instant.now().plusSeconds(900));
    }

    // --- TweetDetail ---

    @Test
    void tweetDetailReturnsParsedTweet() throws Exception {
        String json = """
                {"data":{"threaded_conversation_with_injections_v2":{"instructions":[
                  {"type":"TimelineAddEntries","entries":[
                    {"entryId":"tweet-42","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"itemType":"TimelineTweet","tweet_results":{"result":{
                       "rest_id":"42","legacy":{"full_text":"Hello","created_at":"Mon Jan 01 00:00:00 +0000 2024",
                        "lang":"en","conversation_id_str":"42","favorite_count":1,"reply_count":0,
                        "retweet_count":0,"quote_count":0},
                       "core":{"user_results":{"result":{"rest_id":"1","legacy":{
                         "screen_name":"alice","name":"Alice","followers_count":10,"friends_count":5,
                         "created_at":"Mon Jan 01 00:00:00 +0000 2020"}}}}}}}}}]}]}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));

        var op = new TweetDetailOperation(graphQLClient);
        Optional<Tweet> tweet = op.fetch(42L, pool);

        assertTrue(tweet.isPresent());
        assertEquals(42L, tweet.get().id());
        assertEquals("Hello", tweet.get().text());
    }

    @Test
    void tweetDetailReturnsEmptyWhenNotFound() throws Exception {
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(
                response("{\"data\":{\"threaded_conversation_with_injections_v2\":{\"instructions\":[]}}}"));
        var op = new TweetDetailOperation(graphQLClient);
        assertTrue(op.fetch(99L, pool).isEmpty());
    }

    @Test
    void tweetDetailRawReturnsJsonNode() throws Exception {
        String json = """
                {"data":{"threaded_conversation_with_injections_v2":{"instructions":[
                  {"type":"TimelineAddEntries","entries":[
                    {"entryId":"tweet-7","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"itemType":"TimelineTweet","tweet_results":{"result":{
                       "rest_id":"7","legacy":{"full_text":"raw","created_at":"Mon Jan 01 00:00:00 +0000 2024",
                        "lang":"de","conversation_id_str":"7","favorite_count":0,"reply_count":0,
                        "retweet_count":0,"quote_count":0}}}}}}]}]}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var op = new TweetDetailOperation(graphQLClient);
        Optional<JsonNode> raw = op.fetchRaw(7L, pool);
        assertTrue(raw.isPresent());
        assertEquals("7", raw.get().path("rest_id").asText());
    }

    // --- TweetReplies ---

    @Test
    void tweetRepliesFetchReturnsTweets() throws Exception {
        String json = """
                {"data":{"threaded_conversation_with_injections_v2":{"instructions":[
                  {"type":"TimelineAddEntries","entries":[
                    {"entryId":"tweet-5","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"itemType":"TimelineTweet","tweet_results":{"result":{
                       "rest_id":"5","legacy":{"full_text":"reply","created_at":"Mon Jan 01 00:00:00 +0000 2024",
                        "lang":"en","conversation_id_str":"1","favorite_count":0,"reply_count":0,
                        "retweet_count":0,"quote_count":0}}}}}}]}]}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var op = new TweetRepliesOperation(graphQLClient);
        var page = op.fetch(1L, null, pool);
        assertEquals(1, page.items().size());
        assertEquals(5L, page.items().get(0).id());
    }

    // --- TweetRetweeters ---

    @Test
    void tweetRetweetersFetchReturnsUsers() throws Exception {
        String json = """
                {"data":{"retweeters_timeline":{"timeline":{"instructions":[
                  {"entries":[
                    {"entryId":"user-1","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"user_results":{"result":{"rest_id":"1","legacy":{
                       "screen_name":"bob","name":"Bob","followers_count":50,"friends_count":20,
                       "created_at":"Mon Jan 01 00:00:00 +0000 2021"}}}}}}]}]}}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var op = new TweetRetweetersOperation(graphQLClient);
        var page = op.fetch(42L, null, pool);
        assertEquals(1, page.items().size());
        assertEquals("bob", page.items().get(0).username());
    }

    @Test
    void tweetRetweetersEmptyResult() throws Exception {
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(
                response("{\"data\":{\"retweeters_timeline\":{\"timeline\":{\"instructions\":[]}}}}"));
        var op = new TweetRetweetersOperation(graphQLClient);
        assertTrue(op.fetch(1L, null, pool).items().isEmpty());
    }
}
