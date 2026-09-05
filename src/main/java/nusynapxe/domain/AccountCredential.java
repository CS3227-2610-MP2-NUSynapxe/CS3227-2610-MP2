package nusynapxe.domain;

import java.util.Objects;

/**
 * Account information together with the credential material used for verification.
 *
 * @param account public account projection
 * @param salt per-account password-hashing salt
 * @param verifier stored password verifier
 */
public record AccountCredential(Account account, byte[] salt, byte[] verifier) {
  /**
   * Creates a credential row and defensively copies its byte arrays.
   *
   * @throws NullPointerException if the account, salt, or verifier is {@code null}
   */
  public AccountCredential {
    Objects.requireNonNull(account, "account");
    salt = Objects.requireNonNull(salt, "salt").clone();
    verifier = Objects.requireNonNull(verifier, "verifier").clone();
  }

  /**
   * Returns a copy of the account salt.
   *
   * @return a defensive copy of the salt
   */
  @Override
  public byte[] salt() {
    return salt.clone();
  }

  /**
   * Returns a copy of the stored password verifier.
   *
   * @return a defensive copy of the verifier
   */
  @Override
  public byte[] verifier() {
    return verifier.clone();
  }
}
