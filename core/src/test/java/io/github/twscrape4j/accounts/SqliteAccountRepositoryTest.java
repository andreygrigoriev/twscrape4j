package io.github.twscrape4j.accounts;

class SqliteAccountRepositoryTest extends AccountRepositoryContractTest {

    @Override
    protected AccountRepository createRepository() {
        return new SqliteAccountRepository(":memory:");
    }
}
