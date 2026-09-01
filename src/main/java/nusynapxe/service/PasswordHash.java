package nusynapxe.service;

import java.util.Objects;

/** Salt and derived verifier produced for a password. */
public record PasswordHash(byte[] salt, byte[] verifier) {
  /** Creates a password hash and defensively copies its byte arrays. */
  public PasswordHash {
    salt = Objects.requireNonNull(salt, "salt").clone();
    verifier = Objects.requireNonNull(verifier, "verifier").clone();
  }

  /** Returns a copy of the salt. */
  @Override
  public byte[] salt() {
    return salt.clone();
  }

  /** Returns a copy of the derived verifier. */
  @Override
  public byte[] verifier() {
    return verifier.clone();
  }
}
