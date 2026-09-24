package io.github.twscrape4j.cli;

/** The requested tweet or user does not exist; exits with code 4. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
