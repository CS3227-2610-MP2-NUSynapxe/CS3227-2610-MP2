package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class PasswordHasherTest {
  private final PasswordHasher hasher = new PasswordHasher();

  @Test
  void verifiesCorrectPasswordAndRejectsDifferentPassword() {
    char[] password = "correct-password".toCharArray();
    PasswordHash hash = hasher.hash(password);

    assertTrue(hasher.matches(password, hash.salt(), hash.verifier()));
    assertFalse(hasher.matches("wrong-password".toCharArray(), hash.salt(), hash.verifier()));
  }

  @Test
  void generatesDifferentSaltForEachHash() {
    PasswordHash first = hasher.hash("correct-password".toCharArray());
    PasswordHash second = hasher.hash("correct-password".toCharArray());

    assertFalse(Arrays.equals(first.salt(), second.salt()));
    assertNotEquals(first.verifier(), second.verifier());
  }

  @Test
  void passwordPolicyRejectsShortAndBlankValues() {
    PasswordPolicy policy = new PasswordPolicy();

    assertFalse(policy.isValid("short".toCharArray()));
    assertFalse(policy.isValid("        ".toCharArray()));
    assertTrue(policy.isValid("long-enough".toCharArray()));
  }
}
