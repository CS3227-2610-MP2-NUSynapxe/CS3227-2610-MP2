package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DoctorAppointmentTargetTest {
  @Test
  void capturesAppointmentAndSelectionGenerationTogether() {
    DoctorAppointmentTarget target = DoctorAppointmentTarget.capture(41, 7);

    assertTrue(target.isCurrent(41, 7));
    assertFalse(target.isCurrent(42, 7));
    assertFalse(target.isCurrent(41, 8));
  }

  @Test
  void refusesToCaptureAnEmptySelection() {
    assertThrows(IllegalArgumentException.class, () -> DoctorAppointmentTarget.capture(0, 7));
  }
}
