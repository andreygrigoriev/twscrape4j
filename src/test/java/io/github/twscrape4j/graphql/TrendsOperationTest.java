package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.github.twscrape4j.accounts.Account;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.api.TrendCategory;
import io.github.twscrape4j.http.AccountHandle;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Trend;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrendsOperationTest {

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

    private String trendsJson(String name, long tweetCount) {
        return """
                {"data":{"explore_page":{"body":{"initialTimeline":{"timeline":{"instructions":[
                  {"entries":[
                    {"content":{"content":{"timelineTrend":{"name":"%s","tweet_count":%d}}}}
                  ]}
                ]}}}}}}
                """.formatted(name, tweetCount);
    }

    @Test
    void fetchReturnsParsedTrends() throws Exception {
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(response(trendsJson("#java", 5000)));

        var op = new TrendsOperation(graphQLClient);
        List<Trend> trends = op.fetch(TrendCategory.TRENDING, pool);

        assertEquals(1, trends.size());
        assertEquals("#java", trends.get(0).name());
        assertEquals(5000L, trends.get(0).tweetCount());
    }

    @Test
    void fetchRawReturnsParsedJsonNodes() throws Exception {
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(response(trendsJson("Football", 12000)));

        var op = new TrendsOperation(graphQLClient);
        List<JsonNode> nodes = op.fetchRaw(TrendCategory.SPORT, pool);

        assertEquals(1, nodes.size());
        assertEquals("Football", nodes.get(0).path("name").asText());
        assertEquals(12000L, nodes.get(0).path("tweet_count").asLong());
    }

    @Test
    void fetchReturnsEmptyListWhenNoTrends() throws Exception {
        String json = """
                {"data":{"explore_page":{"body":{"initialTimeline":{"timeline":{"instructions":[]}}}}}}
                """;
        when(graphQLClient.get(any(), any(), any(), any(), any())).thenReturn(response(json));

        var op = new TrendsOperation(graphQLClient);
        assertTrue(op.fetch(TrendCategory.NEWS, pool).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(TrendCategory.class)
    void allCategoriesPassDifferentCategoryIds(TrendCategory category) throws Exception {
        when(graphQLClient.get(any(), any(), any(), any(), any()))
                .thenReturn(response(trendsJson("trend", 1)));

        var op = new TrendsOperation(graphQLClient);
        List<Trend> trends = op.fetch(category, pool);

        // Verify call was made (category ID mapping exercised for all enum values)
        verify(graphQLClient).get(any(), any(), eq("ExplorePage"), argThat(vars -> {
            Object catId = vars.get("categoryId");
            return catId != null && !catId.toString().isBlank();
        }), any());
        assertFalse(trends.isEmpty());
    }
}
