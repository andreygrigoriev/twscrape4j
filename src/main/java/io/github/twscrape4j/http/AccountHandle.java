package io.github.twscrape4j.http;

import io.github.twscrape4j.accounts.Account;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

public record AccountHandle(Account account, CloseableHttpClient http) {}
