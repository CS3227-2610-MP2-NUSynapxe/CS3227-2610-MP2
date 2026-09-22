package nusynapxe;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** Provides the single normalized clock used for Singapore clinic time. */
public final class ClinicClock {
  public static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

  private ClinicClock() {
    throw new AssertionError("Utility class");
  }

  /** Returns the production clock in the clinic timezone. */
  public static Clock system() {
    return Clock.system(ZONE);
  }

  /** Returns the supplied clock normalized to the clinic timezone. */
  public static Clock withClinicZone(Clock clock) {
    return Objects.requireNonNull(clock, "clock").withZone(ZONE);
  }

  /** Returns the current clinic date from any instant source. */
  public static LocalDate today(Clock clock) {
    return LocalDate.now(withClinicZone(clock));
  }

  /** Returns the current clinic local timestamp from any instant source. */
  public static LocalDateTime now(Clock clock) {
    return LocalDateTime.now(withClinicZone(clock));
  }
}
