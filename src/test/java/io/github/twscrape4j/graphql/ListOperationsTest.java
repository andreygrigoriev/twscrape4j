package io.github.twscrape4j.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class ListOperationsTest {

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

    @Test
    void listTimelineFetchReturnsTweets() throws Exception {
        String json = """
                {"data":{"list":{"tweets_timeline":{"timeline":{"instructions":[
                  {"type":"TimelineAddEntries","entries":[
                    {"entryId":"tweet-11","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"itemType":"TimelineTweet","tweet_results":{"result":{
                       "rest_id":"11","legacy":{"full_text":"list tweet",
                        "created_at":"Mon Jan 01 00:00:00 +0000 2024",
                        "lang":"en","conversation_id_str":"11","favorite_count":2,
                        "reply_count":0,"retweet_count":0,"quote_count":0}}}}}}]}]}}}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var page = new ListTimelineOperation(graphQLClient).fetch(999L, null, pool);
        assertEquals(1, page.items().size());
        assertEquals("list tweet", page.items().get(0).text());
    }

    @Test
    void listTimelineEmptyResult() throws Exception {
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(
                response("{\"data\":{\"list\":{\"tweets_timeline\":{\"timeline\":{\"instructions\":[]}}}}}"));
        var page = new ListTimelineOperation(graphQLClient).fetch(1L, null, pool);
        assertTrue(page.items().isEmpty());
        assertFalse(page.hasMore());
    }

    @Test
    void listTimelineRawReturnsJsonNodes() throws Exception {
        String json = """
                {"data":{"list":{"tweets_timeline":{"timeline":{"instructions":[
                  {"type":"TimelineAddEntries","entries":[
                    {"entryId":"tweet-22","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"itemType":"TimelineTweet","tweet_results":{"result":{
                       "rest_id":"22","legacy":{"full_text":"raw list","created_at":"Mon Jan 01 00:00:00 +0000 2024",
                        "lang":"fr","conversation_id_str":"22","favorite_count":0,"reply_count":0,
                        "retweet_count":0,"quote_count":0}}}}}}]}]}}}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var page = new ListTimelineOperation(graphQLClient).fetchRaw(1L, null, pool);
        assertEquals(1, page.items().size());
        assertEquals("22", page.items().get(0).path("rest_id").asText());
    }

    @Test
    void listMembersFetchReturnsUsers() throws Exception {
        String json = """
                {"data":{"list":{"members_timeline":{"timeline":{"instructions":[
                  {"entries":[
                    {"entryId":"user-5","content":{"entryType":"TimelineTimelineItem",
                     "itemContent":{"user_results":{"result":{
                       "rest_id":"5","legacy":{"screen_name":"eve","name":"Eve",
                        "followers_count":200,"friends_count":80,
                        "created_at":"Mon Jan 01 00:00:00 +0000 2019"}}}}}}]}]}}}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));
        var page = new ListMembersOperation(graphQLClient).fetch(1L, null, pool);
        assertEquals(1, page.items().size());
        assertEquals("eve", page.items().get(0).username());
    }

    @Test
    void listMembersEmptyResult() throws Exception {
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(
                response("{\"data\":{\"list\":{\"members_timeline\":{\"timeline\":{\"instructions\":[]}}}}}"));
        var page = new ListMembersOperation(graphQLClient).fetch(1L, null, pool);
        assertTrue(page.items().isEmpty());
    }
}
