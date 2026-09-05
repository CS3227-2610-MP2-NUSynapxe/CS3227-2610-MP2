package nusynapxe.service;

/** Validates passwords before account creation. */
@SuppressWarnings("PMD.UseVarargs")
public final class PasswordPolicy {
  /** Minimum number of characters accepted for a staff password. */
  public static final int MINIMUM_LENGTH = 8;

  /** Creates the default password policy. */
  public PasswordPolicy() {
    // Uses the class's default validation rules.
  }

  /**
   * Reports whether a password is non-blank and meets the minimum length.
   *
   * @param password password characters to validate
   * @return {@code true} when the password is long enough and contains a non-whitespace character
   */
  public boolean isValid(char[] password) {
    if (password == null || password.length < MINIMUM_LENGTH) {
      return false;
    }
    for (char character : password) {
      if (!Character.isWhitespace(character)) {
        return true;
      }
    }
    return false;
  }
}
