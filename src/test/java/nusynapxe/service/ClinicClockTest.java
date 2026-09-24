package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import nusynapxe.ClinicClock;
import org.junit.jupiter.api.Test;

class ClinicClockTest {
  @Test
  void clinicDateUsesSingaporeZoneAtUtcMidnightBoundary() {
    Clock clock = Clock.fixed(Instant.parse("2026-09-21T16:30:00Z"), ZoneOffset.UTC);

    assertEquals(LocalDate.of(2026, 9, 22), ClinicClock.today(clock));
  }

  @Test
  void productionClockUsesSingaporeZone() {
    assertEquals(ZoneId.of("Asia/Singapore"), ClinicClock.system().getZone());
  }
}
