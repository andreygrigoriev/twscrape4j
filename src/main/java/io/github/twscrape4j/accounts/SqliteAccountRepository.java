package io.github.twscrape4j.accounts;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Slf4j
public class SqliteAccountRepository implements AccountRepository {

    private static final String TABLE = "accounts";

    private final Connection connection;
    private final DSLContext dsl;

    public SqliteAccountRepository(String dbPath) {
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            this.dsl = DSL.using(connection, SQLDialect.SQLITE);
            createSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open SQLite database: " + dbPath, e);
        }
    }

    private void createSchema() {
        dsl.execute("""
                CREATE TABLE IF NOT EXISTS accounts (
                    username      TEXT PRIMARY KEY,
                    password      TEXT NOT NULL,
                    email         TEXT NOT NULL,
                    email_password TEXT NOT NULL,
                    auth_token    TEXT,
                    ct0           TEXT,
                    proxy         TEXT,
                    active        INTEGER NOT NULL DEFAULT 1,
                    logged_in     INTEGER NOT NULL DEFAULT 0,
                    locked_until  TEXT,
                    error_msg     TEXT,
                    total_requests INTEGER NOT NULL DEFAULT 0
                )
                """);
        log.debug("Schema ready");
    }

    @Override
    public void save(Account a) {
        dsl.insertInto(table(TABLE))
                .set(field("username"), a.username())
                .set(field("password"), a.password())
                .set(field("email"), a.email())
                .set(field("email_password"), a.emailPassword())
                .set(field("auth_token"), a.authToken())
                .set(field("ct0"), a.ct0())
                .set(field("proxy"), a.proxy())
                .set(field("active"), a.active() ? 1 : 0)
                .set(field("logged_in"), a.loggedIn() ? 1 : 0)
                .set(field("locked_until"), a.lockedUntil() != null ? a.lockedUntil().toString() : null)
                .set(field("error_msg"), a.errorMsg())
                .set(field("total_requests"), a.totalRequests())
                .onDuplicateKeyUpdate()
                .set(field("password"), a.password())
                .set(field("email"), a.email())
                .set(field("email_password"), a.emailPassword())
                .set(field("auth_token"), a.authToken())
                .set(field("ct0"), a.ct0())
                .set(field("proxy"), a.proxy())
                .set(field("active"), a.active() ? 1 : 0)
                .set(field("logged_in"), a.loggedIn() ? 1 : 0)
                .set(field("locked_until"), a.lockedUntil() != null ? a.lockedUntil().toString() : null)
                .set(field("error_msg"), a.errorMsg())
                .set(field("total_requests"), a.totalRequests())
                .execute();
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return dsl.selectFrom(table(TABLE))
                .where(field("username").eq(username))
                .fetchOptional(r -> toAccount(r));
    }

    @Override
    public List<Account> findActive() {
        var now = Instant.now();
        return dsl.selectFrom(table(TABLE))
                .where(field("active").eq(1))
                .fetch(r -> toAccount(r))
                .stream()
                .filter(a -> a.lockedUntil() == null || a.lockedUntil().isBefore(now))
                .toList();
    }

    @Override
    public void updateState(Account a) {
        dsl.update(table(TABLE))
                .set(field("active"), a.active() ? 1 : 0)
                .set(field("logged_in"), a.loggedIn() ? 1 : 0)
                .set(field("locked_until"), a.lockedUntil() != null ? a.lockedUntil().toString() : null)
                .set(field("error_msg"), a.errorMsg())
                .set(field("total_requests"), a.totalRequests())
                .where(field("username").eq(a.username()))
                .execute();
    }

    private Account toAccount(org.jooq.Record r) {
        String lockedUntilStr = r.get(field("locked_until"), String.class);
        Instant lockedUntil = lockedUntilStr != null ? Instant.parse(lockedUntilStr) : null;
        return new Account(
                r.get(field("username"), String.class),
                r.get(field("password"), String.class),
                r.get(field("email"), String.class),
                r.get(field("email_password"), String.class),
                r.get(field("auth_token"), String.class),
                r.get(field("ct0"), String.class),
                r.get(field("proxy"), String.class),
                r.get(field("active"), Integer.class) == 1,
                r.get(field("logged_in"), Integer.class) == 1,
                lockedUntil,
                r.get(field("error_msg"), String.class),
                r.get(field("total_requests"), Long.class)
        );
    }
}
