package nusynapxe.service;

/** Validates passwords before account creation. */
@SuppressWarnings("PMD.UseVarargs")
public final class PasswordPolicy {
  /** Minimum number of characters accepted for a staff password. */
  public static final int MINIMUM_LENGTH = 8;

  /** Reports whether a password is non-blank and meets the minimum length. */
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
