package io.github.twscrape4j.auth;

import java.util.Scanner;

/**
 * Resolves an authentication challenge (e.g. email verification code).
 *
 * <p>Inject a custom implementation to handle challenges in non-interactive environments:
 * <pre>{@code
 * TwScrape.create(repo, (type, prompt) -> myEmailClient.fetchCode());
 * }</pre>
 */
@FunctionalInterface
public interface ChallengeHandler {

    /**
     * @param challengeType the Twitter challenge type string (e.g. "LoginAcid", "EmailVerification")
     * @param prompt        human-readable description of what is needed
     * @return the value to submit (e.g. the 6-digit verification code)
     */
    String resolve(String challengeType, String prompt);

    /** Default implementation that reads the code from stdin. */
    static ChallengeHandler stdin() {
        return (type, prompt) -> {
            System.out.println("[twscrape4j] Challenge (" + type + "): " + prompt);
            System.out.print("Enter value: ");
            return new Scanner(System.in).nextLine().trim();
        };
    }
}
