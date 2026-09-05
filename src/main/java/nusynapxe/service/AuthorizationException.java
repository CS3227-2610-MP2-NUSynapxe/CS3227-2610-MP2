package nusynapxe.service;

/** Indicates that the current session cannot perform an operation. */
public final class AuthorizationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates an authorization failure with a user-safe message.
   *
   * @param message user-safe authorization message
   */
  public AuthorizationException(String message) {
    super(message);
  }
}
