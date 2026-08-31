package nusynapxe.domain;

import java.util.Objects;

/** Account information together with the credential material used for verification. */
public record AccountCredential(Account account, byte[] salt, byte[] verifier) {
  /** Creates a credential row and defensively copies its byte arrays. */
  public AccountCredential {
    Objects.requireNonNull(account, "account");
    salt = Objects.requireNonNull(salt, "salt").clone();
    verifier = Objects.requireNonNull(verifier, "verifier").clone();
  }

  /** Returns a copy of the account salt. */
  @Override
  public byte[] salt() {
    return salt.clone();
  }

  /** Returns a copy of the stored password verifier. */
  @Override
  public byte[] verifier() {
    return verifier.clone();
  }
}
