package io.github.twscrape4j.graphql;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.github.twscrape4j.accounts.Account;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.AccountHandle;
import io.github.twscrape4j.http.GraphQLClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserOperationsTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
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

    private String userResultJson(long id, String screenName) {
        return """
                {"rest_id":"%d","legacy":{"screen_name":"%s","name":"Name %s",
                 "followers_count":100,"friends_count":50,
                 "created_at":"Mon Jan 01 00:00:00 +0000 2020"}}
                """.formatted(id, screenName, screenName);
    }

    @Test
    void userByScreenNameReturnsParsedUser() throws Exception {
        String json = "{\"data\":{\"user\":{\"result\":" + userResultJson(1L, "alice") + "}}}";
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));

        var op = new UserByScreenNameOperation(graphQLClient);
        var user = op.fetch("alice", pool);
        assertTrue(user.isPresent());
        assertEquals("alice", user.get().username());
    }

    @Test
    void userByScreenNameReturnsEmptyForMissing() throws Exception {
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(response("{\"data\":{\"user\":{}}}"));
        var op = new UserByScreenNameOperation(graphQLClient);
        assertTrue(op.fetch("nobody", pool).isEmpty());
    }

    @Test
    void userByIdReturnsParsedUser() throws Exception {
        String json = "{\"data\":{\"user\":{\"result\":" + userResultJson(42L, "bob") + "}}}";
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));

        var op = new UserByIdOperation(graphQLClient);
        var user = op.fetch(42L, pool);
        assertTrue(user.isPresent());
        assertEquals(42L, user.get().id());
        assertEquals("bob", user.get().username());
    }

    @Test
    void userTweetsFetchReturnsTweets() throws Exception {
        String json = """
                {"data":{"user":{"result":{"timeline_v2":{"timeline":{"instructions":[
                  {"type":"TimelineAddEntries","entries":[
                    {"entryId":"tweet-3","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"itemType":"TimelineTweet","tweet_results":{"result":{
                       "rest_id":"3","legacy":{"full_text":"my tweet","created_at":"Mon Jan 01 00:00:00 +0000 2024",
                        "lang":"en","conversation_id_str":"3","favorite_count":0,"reply_count":0,
                        "retweet_count":0,"quote_count":0}}}}}}]}]}}}}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var op = new UserTweetsOperation(graphQLClient);
        var page = op.fetch(1L, null, pool);
        assertEquals(1, page.items().size());
        assertEquals("my tweet", page.items().get(0).text());
    }

    @Test
    void userMediaFetchReturnsTweets() throws Exception {
        // Same shape as UserTweets — just verify it compiles and parses
        String json = """
                {"data":{"user":{"result":{"timeline_v2":{"timeline":{"instructions":[]}}}}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var page = new UserMediaOperation(graphQLClient).fetch(1L, null, pool);
        assertTrue(page.items().isEmpty());
    }

    @Test
    void userMediaFetchReturnsTweetsWithContent() throws Exception {
        String json = """
                {"data":{"user":{"result":{"timeline_v2":{"timeline":{"instructions":[
                  {"type":"TimelineAddEntries","entries":[
                    {"entryId":"tweet-55","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"itemType":"TimelineTweet","tweet_results":{"result":{
                       "rest_id":"55","legacy":{"full_text":"media tweet","created_at":"Mon Jan 01 00:00:00 +0000 2024",
                        "lang":"en","conversation_id_str":"55","favorite_count":3,"reply_count":0,
                        "retweet_count":1,"quote_count":0}}}}}}]}]}}}}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var page = new UserMediaOperation(graphQLClient).fetch(1L, null, pool);
        assertEquals(1, page.items().size());
        assertEquals(55L, page.items().get(0).id());
        assertEquals("media tweet", page.items().get(0).text());
    }

    @Test
    void userFollowersFetchReturnsUsers() throws Exception {
        String json = """
                {"data":{"followers_timeline":{"timeline":{"instructions":[
                  {"entries":[
                    {"entryId":"user-9","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"user_results":{"result":%s}}}}]}]}}}}
                """.formatted(userResultJson(9L, "carol"));
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var page = new UserFollowersOperation(graphQLClient).fetch(1L, null, pool);
        assertEquals(1, page.items().size());
        assertEquals("carol", page.items().get(0).username());
    }

    @Test
    void userFollowingFetchReturnsUsers() throws Exception {
        String json = """
                {"data":{"following_timeline":{"timeline":{"instructions":[
                  {"entries":[
                    {"entryId":"user-8","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"user_results":{"result":%s}}}}]}]}}}}
                """.formatted(userResultJson(8L, "dave"));
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var page = new UserFollowingOperation(graphQLClient).fetch(1L, null, pool);
        assertEquals(1, page.items().size());
        assertEquals("dave", page.items().get(0).username());
    }
}
