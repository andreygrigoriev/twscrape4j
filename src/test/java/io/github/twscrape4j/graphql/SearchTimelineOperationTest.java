package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.github.twscrape4j.accounts.Account;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.api.SearchMode;
import io.github.twscrape4j.http.AccountHandle;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Tweet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SearchTimelineOperationTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private GraphQLClient graphQLClient;
    private AccountPool pool;
    private AccountHandle handle;

    @BeforeEach
    void setUp() {
        graphQLClient = mock(GraphQLClient.class);
        pool = mock(AccountPool.class);
        var account = new Account("alice", "", "", "", "tok", "ct0",
                null, true, true, null, null, 0L);
        handle = new AccountHandle(account, mock(CloseableHttpClient.class));
        when(pool.acquire(anyString())).thenReturn(handle);
    }

    private JsonNode searchResponseWithTweet(long tweetId, String text, String nextCursor) throws Exception {
        // Build cursor entry only when a real cursor is provided
        String cursorEntry = (nextCursor != null && !nextCursor.isBlank()) ? """
                ,{
                  "entryId": "cursor-bottom-1",
                  "content": {
                    "entryType": "TimelineTimelineCursor",
                    "cursorType": "Bottom",
                    "value": "%s"
                  }
                }
                """.formatted(nextCursor) : "";

        String json = """
                {
                  "data": {
                    "search_by_raw_query": {
                      "search_timeline": {
                        "timeline": {
                          "instructions": [
                            {
                              "type": "TimelineAddEntries",
                              "entries": [
                                {
                                  "entryId": "tweet-%d",
                                  "content": {
                                    "entryType": "TimelineTimelineItem",
                                    "itemContent": {
                                      "itemType": "TimelineTweet",
                                      "tweet_results": {
                                        "result": {
                                          "rest_id": "%d",
                                          "legacy": {
                                            "full_text": "%s",
                                            "created_at": "Mon Jan 01 12:00:00 +0000 2024",
                                            "lang": "en",
                                            "conversation_id_str": "%d",
                                            "favorite_count": 5,
                                            "reply_count": 1,
                                            "retweet_count": 2,
                                            "quote_count": 0
                                          },
                                          "core": {
                                            "user_results": {
                                              "result": {
                                                "rest_id": "99",
                                                "legacy": {
                                                  "screen_name": "testuser",
                                                  "name": "Test User",
                                                  "followers_count": 100,
                                                  "friends_count": 50,
                                                  "created_at": "Mon Jan 01 00:00:00 +0000 2020"
                                                }
                                              }
                                            }
                                          }
                                        }
                                      }
                                    }
                                  }
                                }%s
                              ]
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """.formatted(tweetId, tweetId, text, tweetId, cursorEntry);
        return MAPPER.readTree(json);
    }

    @Test
    void fetchReturnsParsedTweets() throws Exception {
        JsonNode body = searchResponseWithTweet(42L, "Hello #java", "next_cursor_abc");
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(new GraphQLClient.GraphQLResponse(body, 450, Instant.now().plusSeconds(900)));

        var op = new SearchTimelineOperation(graphQLClient);
        Page<Tweet> page = op.fetch("#java", SearchMode.LATEST, null, pool);

        assertEquals(1, page.items().size());
        assertEquals(42L, page.items().get(0).id());
        assertEquals("Hello #java", page.items().get(0).text());
        assertEquals("en", page.items().get(0).lang());
        assertEquals("next_cursor_abc", page.nextCursor());
        assertTrue(page.hasMore());
    }

    @Test
    void fetchRawReturnsJsonNodes() throws Exception {
        JsonNode body = searchResponseWithTweet(7L, "raw tweet", "cursor_x");
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(new GraphQLClient.GraphQLResponse(body, 100, Instant.now().plusSeconds(900)));

        var op = new SearchTimelineOperation(graphQLClient);
        Page<JsonNode> page = op.fetchRaw("#java", SearchMode.TOP, null, pool);

        assertEquals(1, page.items().size());
        assertEquals("7", page.items().get(0).path("rest_id").asText());
    }

    @Test
    void fetchWithNoCursorInResponseHasNoMore() throws Exception {
        String json = """
                {"data":{"search_by_raw_query":{"search_timeline":{"timeline":{"instructions":[
                  {"type":"TimelineAddEntries","entries":[]}
                ]}}}}}
                """;
        JsonNode body = MAPPER.readTree(json);
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(new GraphQLClient.GraphQLResponse(body, 10, Instant.now().plusSeconds(900)));

        var op = new SearchTimelineOperation(graphQLClient);
        Page<Tweet> page = op.fetch("#empty", SearchMode.LATEST, null, pool);

        assertTrue(page.items().isEmpty());
        assertFalse(page.hasMore());
    }

    @Test
    void paginatingSpliteratorStopsWhenExhausted() throws Exception {
        JsonNode firstPage = searchResponseWithTweet(1L, "tweet 1", "cursor_1");
        JsonNode secondPage = searchResponseWithTweet(2L, "tweet 2", null);

        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(new GraphQLClient.GraphQLResponse(firstPage, 100, Instant.now().plusSeconds(900)))
                .thenReturn(new GraphQLClient.GraphQLResponse(secondPage, 99, Instant.now().plusSeconds(900)));

        var op = new SearchTimelineOperation(graphQLClient);
        var spliterator = new PaginatingSpliterator<Tweet>(pool,
                (cursor, p) -> op.fetch("#java", SearchMode.LATEST, cursor, p));

        List<Tweet> tweets = new java.util.ArrayList<>();
        spliterator.forEachRemaining(tweets::add);

        assertEquals(2, tweets.size());
        assertEquals(1L, tweets.get(0).id());
        assertEquals(2L, tweets.get(1).id());
    }

    @Test
    void paginatingSpliteratorCharacteristicsAreOrderedAndNonnull() {
        var spliterator = new PaginatingSpliterator<Tweet>(pool, (c, p) -> new Page<>(List.of(), null));
        int chars = spliterator.characteristics();
        assertTrue((chars & java.util.Spliterator.ORDERED) != 0);
        assertTrue((chars & java.util.Spliterator.NONNULL) != 0);
    }

    // --- ModelMapper.extractTimeline dead-branch coverage ---

    @Test
    void timelinePinEntryInstructionTypeExtractsTweet() throws Exception {
        // TimelinePinEntry has "entry.content.items"; each item has content.entryType (same shape as normal entries)
        String json = """
                {"data":{"search_by_raw_query":{"search_timeline":{"timeline":{"instructions":[
                  {
                    "type": "TimelinePinEntry",
                    "entry": {
                      "entryId": "tweet-pin-1",
                      "content": {
                        "items": [
                          {
                            "entryId": "pinned-tweet-100",
                            "content": {
                              "entryType": "TimelineTimelineItem",
                              "itemContent": {
                                "tweet_results": {
                                  "result": {
                                    "rest_id": "100",
                                    "legacy": {
                                      "full_text": "pinned tweet",
                                      "created_at": "Mon Jan 01 12:00:00 +0000 2024",
                                      "lang": "en",
                                      "conversation_id_str": "100",
                                      "favorite_count": 0,
                                      "reply_count": 0,
                                      "retweet_count": 0,
                                      "quote_count": 0
                                    }
                                  }
                                }
                              }
                            }
                          }
                        ]
                      }
                    }
                  }
                ]}}}}}
                """;
        JsonNode body = MAPPER.readTree(json);
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(new GraphQLClient.GraphQLResponse(body, 100, Instant.now().plusSeconds(900)));

        var op = new SearchTimelineOperation(graphQLClient);
        Page<Tweet> page = op.fetch("#pinned", SearchMode.LATEST, null, pool);

        assertEquals(1, page.items().size());
        assertEquals(100L, page.items().get(0).id());
        assertEquals("pinned tweet", page.items().get(0).text());
    }

    @Test
    void timelineTimelineModuleEntryTypeExtractsTweets() throws Exception {
        String json = """
                {"data":{"search_by_raw_query":{"search_timeline":{"timeline":{"instructions":[
                  {
                    "type": "TimelineAddEntries",
                    "entries": [
                      {
                        "entryId": "module-1",
                        "content": {
                          "entryType": "TimelineTimelineModule",
                          "items": [
                            {
                              "item": {
                                "itemContent": {
                                  "tweet_results": {
                                    "result": {
                                      "rest_id": "200",
                                      "legacy": {
                                        "full_text": "module tweet",
                                        "created_at": "Mon Jan 01 12:00:00 +0000 2024",
                                        "lang": "en",
                                        "conversation_id_str": "200",
                                        "favorite_count": 0,
                                        "reply_count": 0,
                                        "retweet_count": 0,
                                        "quote_count": 0
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          ]
                        }
                      }
                    ]
                  }
                ]}}}}}
                """;
        JsonNode body = MAPPER.readTree(json);
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(new GraphQLClient.GraphQLResponse(body, 100, Instant.now().plusSeconds(900)));

        var op = new SearchTimelineOperation(graphQLClient);
        Page<Tweet> page = op.fetch("#module", SearchMode.LATEST, null, pool);

        assertEquals(1, page.items().size());
        assertEquals(200L, page.items().get(0).id());
        assertEquals("module tweet", page.items().get(0).text());
    }

    @Test
    void timelineReplaceEntryUpdatesBottomCursor() throws Exception {
        // First emit a tweet via TimelineAddEntries, then update the cursor via TimelineReplaceEntry
        String json = """
                {"data":{"search_by_raw_query":{"search_timeline":{"timeline":{"instructions":[
                  {
                    "type": "TimelineAddEntries",
                    "entries": [
                      {
                        "entryId": "tweet-300",
                        "content": {
                          "entryType": "TimelineTimelineItem",
                          "itemContent": {
                            "tweet_results": {
                              "result": {
                                "rest_id": "300",
                                "legacy": {
                                  "full_text": "replace cursor tweet",
                                  "created_at": "Mon Jan 01 12:00:00 +0000 2024",
                                  "lang": "en",
                                  "conversation_id_str": "300",
                                  "favorite_count": 0,
                                  "reply_count": 0,
                                  "retweet_count": 0,
                                  "quote_count": 0
                                }
                              }
                            }
                          }
                        }
                      },
                      {
                        "entryId": "cursor-bottom-0",
                        "content": {
                          "entryType": "TimelineTimelineCursor",
                          "cursorType": "Bottom",
                          "value": "old_cursor"
                        }
                      }
                    ]
                  },
                  {
                    "type": "TimelineReplaceEntry",
                    "entry": {
                      "content": {
                        "entryType": "TimelineTimelineCursor",
                        "cursorType": "Bottom",
                        "value": "new_cursor"
                      }
                    }
                  }
                ]}}}}}
                """;
        JsonNode body = MAPPER.readTree(json);
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(new GraphQLClient.GraphQLResponse(body, 100, Instant.now().plusSeconds(900)));

        var op = new SearchTimelineOperation(graphQLClient);
        Page<Tweet> page = op.fetch("#replace", SearchMode.LATEST, null, pool);

        assertEquals(1, page.items().size());
        assertEquals("new_cursor", page.nextCursor());
    }
}
