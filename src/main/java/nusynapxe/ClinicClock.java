package nusynapxe;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** Provides the single normalized clock used for Singapore clinic time. */
public final class ClinicClock {
  /** Singapore time zone used for all clinic-local dates and timestamps. */
  public static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

  private ClinicClock() {
    throw new AssertionError("Utility class");
  }

  /**
   * Returns the production clock in the clinic timezone.
   *
   * @return a system clock normalized to {@link #ZONE}
   */
  public static Clock system() {
    return Clock.system(ZONE);
  }

  /**
   * Returns the supplied clock normalized to the clinic timezone.
   *
   * @param clock source clock
   * @return the source clock configured for {@link #ZONE}
   * @throws NullPointerException if {@code clock} is {@code null}
   */
  public static Clock withClinicZone(Clock clock) {
    return Objects.requireNonNull(clock, "clock").withZone(ZONE);
  }

  /**
   * Returns the current clinic date from any instant source.
   *
   * @param clock source clock
   * @return current date in {@link #ZONE}
   * @throws NullPointerException if {@code clock} is {@code null}
   */
  public static LocalDate today(Clock clock) {
    return LocalDate.now(withClinicZone(clock));
  }

  /**
   * Returns the current clinic local timestamp from any instant source.
   *
   * @param clock source clock
   * @return current local timestamp in {@link #ZONE}
   * @throws NullPointerException if {@code clock} is {@code null}
   */
  public static LocalDateTime now(Clock clock) {
    return LocalDateTime.now(withClinicZone(clock));
  }
}
