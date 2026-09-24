package io.github.twscrape4j.cli;

import io.github.twscrape4j.auth.ChallengeHandler;

import java.io.Console;
import java.util.Map;

/**
 * Resolves login challenges without touching stdout: uses {@code TWSCRAPE_CHALLENGE_CODE} if set, otherwise
 * prompts on the terminal via {@link Console}, otherwise fails with a configuration error.
 */
public class CliChallengeHandler implements ChallengeHandler {

    static final String CHALLENGE_CODE = "TWSCRAPE_CHALLENGE_CODE";

    private final Map<String, String> env;
    private final Console console;

    /** @param console the process console, or {@code null} when there is none */
    public CliChallengeHandler(Map<String, String> env, Console console) {
        this.env = env;
        this.console = console;
    }

    @Override
    public String resolve(String challengeType, String prompt) {
        String code = env.get(CHALLENGE_CODE);
        if (code != null && !code.isBlank()) {
            return code.strip();
        }
        // JDK 22+ may return a console that is not attached to a terminal (e.g. when stdin/stdout are piped)
        if (console != null && console.isTerminal()) {
            String answer = console.readLine("[twscrape] Challenge (%s): %s: ", challengeType, prompt);
            if (answer != null && !answer.isBlank()) {
                return answer.strip();
            }
            throw new CliConfigException("no verification code entered");
        }
        throw new CliConfigException("login requires a verification code; set " + CHALLENGE_CODE);
    }
}
