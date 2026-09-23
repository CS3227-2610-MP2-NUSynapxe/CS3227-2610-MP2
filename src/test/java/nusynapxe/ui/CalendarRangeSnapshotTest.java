package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

final class CalendarRangeSnapshotTest {
  @Test
  void retainsDatesCapturedBeforeAQueuedTaskRuns() {
    LocalDate from = LocalDate.of(2026, 9, 22);
    LocalDate to = LocalDate.of(2026, 9, 28);

    CalendarRangeSnapshot snapshot = CalendarRangeSnapshot.capture(from, to);

    assertEquals(LocalDate.of(2026, 9, 22), snapshot.from());
    assertEquals(LocalDate.of(2026, 9, 28), snapshot.to());
  }
}
