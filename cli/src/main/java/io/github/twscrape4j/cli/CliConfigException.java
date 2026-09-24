package io.github.twscrape4j.cli;

/** Configuration or authentication problem (missing env, login rejected, no challenge code); exits with code 3. */
public class CliConfigException extends RuntimeException {

    public CliConfigException(String message) {
        super(message);
    }

    public CliConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
