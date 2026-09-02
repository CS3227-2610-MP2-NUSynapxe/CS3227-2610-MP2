package nusynapxe.domain;

/** A classified half-open time-grid segment used to render background shading. */
public record CalendarTimeSegment(int startMinute, int endMinute, SegmentKind kind) {
  /** Visual states for a Calendar time segment. */
  public enum SegmentKind {
    /** A date or time period that has already elapsed. */
    ELAPSED,
    /** A disabled day, break, or time outside a working interval. */
    NON_WORKING,
    /** A future period inside a configured working interval. */
    WORKING
  }

  /** Validates a segment within a civil day. */
  public CalendarTimeSegment {
    if (startMinute < 0
        || endMinute > WorkingInterval.MINUTES_PER_DAY
        || endMinute <= startMinute) {
      throw new IllegalArgumentException("Calendar segment must be inside one day");
    }
    if (kind == null) {
      throw new NullPointerException("kind");
    }
  }
}
