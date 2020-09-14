package io.gatehill.imposter.embedded;

/**
 * @author pete
 */
public class ImposterLaunchException extends RuntimeException {
    public ImposterLaunchException(String message, Exception cause) {
        super(message, cause);
    }
}
