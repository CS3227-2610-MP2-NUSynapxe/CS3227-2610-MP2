package nusynapxe.domain;

/** A half-open daily working interval represented as minutes after midnight. */
public record WorkingInterval(int startMinute, int endMinute) {
  /** The number of minutes in a civil day. */
  public static final int MINUTES_PER_DAY = 24 * 60;

  /** Validates the interval, including the explicit midnight end value of 1440. */
  public WorkingInterval {
    if (startMinute < 0 || startMinute >= MINUTES_PER_DAY) {
      throw new IllegalArgumentException("Working interval start must be between 0 and 1439");
    }
    if (endMinute <= startMinute || endMinute > MINUTES_PER_DAY) {
      throw new IllegalArgumentException("Working interval end must be after its start");
    }
  }

  /** Returns whether a minute belongs to this half-open interval. */
  public boolean contains(int minute) {
    return minute >= startMinute && minute < endMinute;
  }
}
