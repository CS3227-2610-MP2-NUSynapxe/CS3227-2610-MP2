package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import org.junit.jupiter.api.Test;

final class SessionManagerTest {
  @Test
  void storesAndClearsOnlyTheCurrentSession() {
    SessionManager sessions = new SessionManager();
    Session session = new Session(1, "admin", Role.SYSTEM_ADMIN);

    assertFalse(sessions.current().isPresent());
    sessions.start(session);
    assertEquals(session, sessions.current().orElseThrow());
    assertEquals(session, sessions.requireCurrent());
    sessions.clear();
    assertFalse(sessions.current().isPresent());
    assertThrows(AuthorizationException.class, sessions::requireCurrent);
  }
}
