package nusynapxe.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Derives and verifies salted password verifiers using JDK security APIs. */
@SuppressWarnings("PMD.UseVarargs")
public final class PasswordHasher {
  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final int ITERATIONS = 210_000;
  private static final int KEY_LENGTH_BITS = 256;
  private static final int SALT_LENGTH_BYTES = 16;

  private final SecureRandom secureRandom;

  /** Creates a hasher using a cryptographically secure random source. */
  public PasswordHasher() {
    this(new SecureRandom());
  }

  PasswordHasher(SecureRandom secureRandom) {
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
  }

  /**
   * Creates a salted verifier for a password.
   *
   * @param password password to hash; the caller remains responsible for clearing it
   * @return salted password hash
   * @throws NullPointerException if {@code password} is {@code null}
   * @throws IllegalStateException if the configured JDK hashing algorithm is unavailable
   */
  public PasswordHash hash(char[] password) {
    Objects.requireNonNull(password, "password");
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    secureRandom.nextBytes(salt);
    return new PasswordHash(salt, derive(password, salt));
  }

  /**
   * Checks a password against a stored salt and verifier.
   *
   * @param password candidate password
   * @param salt stored password salt
   * @param expectedVerifier stored derived verifier
   * @return {@code true} when the candidate produces the expected verifier
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalStateException if the configured JDK hashing algorithm is unavailable
   */
  public boolean matches(char[] password, byte[] salt, byte[] expectedVerifier) {
    Objects.requireNonNull(password, "password");
    Objects.requireNonNull(salt, "salt");
    Objects.requireNonNull(expectedVerifier, "expectedVerifier");
    return MessageDigest.isEqual(expectedVerifier, derive(password, salt));
  }

  private static byte[] derive(char[] password, byte[] salt) {
    PBEKeySpec specification = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS);
    try {
      SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
      return factory.generateSecret(specification).getEncoded();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "The configured password hashing algorithm is unavailable", exception);
    } finally {
      specification.clearPassword();
    }
  }
}
