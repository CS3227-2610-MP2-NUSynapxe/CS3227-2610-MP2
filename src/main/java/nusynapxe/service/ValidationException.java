package nusynapxe.service;

/** Indicates that a user-provided value or requested operation is invalid. */
public final class ValidationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a validation failure with a user-safe message.
   *
   * @param message user-safe validation message
   */
  public ValidationException(String message) {
    super(message);
  }

  /**
   * Creates a validation failure that retains the persistence cause.
   *
   * @param message user-safe validation message
   * @param cause underlying failure
   */
  public ValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
