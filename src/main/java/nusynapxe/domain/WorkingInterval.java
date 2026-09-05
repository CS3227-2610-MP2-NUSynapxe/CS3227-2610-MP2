package nusynapxe.domain;

/**
 * A half-open daily working interval represented as minutes after midnight.
 *
 * @param startMinute inclusive start minute, from {@code 0} through {@code 1439}
 * @param endMinute exclusive end minute, from after {@code startMinute} through {@code 1440}
 */
public record WorkingInterval(int startMinute, int endMinute) {
  /** The number of minutes in a civil day. */
  public static final int MINUTES_PER_DAY = 24 * 60;

  /**
   * Validates the interval, including the explicit midnight end value of 1440.
   *
   * @throws IllegalArgumentException if the start or end is outside the civil day, or the end is
   *     not after the start
   */
  public WorkingInterval {
    if (startMinute < 0 || startMinute >= MINUTES_PER_DAY) {
      throw new IllegalArgumentException("Working interval start must be between 0 and 1439");
    }
    if (endMinute <= startMinute || endMinute > MINUTES_PER_DAY) {
      throw new IllegalArgumentException("Working interval end must be after its start");
    }
  }

  /**
   * Returns whether a minute belongs to this half-open interval.
   *
   * @param minute minute to test
   * @return {@code true} when {@code minute} is at least the start and before the end
   */
  public boolean contains(int minute) {
    return minute >= startMinute && minute < endMinute;
  }
}
