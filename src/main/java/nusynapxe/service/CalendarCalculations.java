package nusynapxe.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarAppointmentBlock;
import nusynapxe.domain.CalendarTimeSegment;
import nusynapxe.domain.CalendarTimeSegment.SegmentKind;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.WorkingInterval;

/** Pure calculations shared by the Doctor Calendar renderer and its tests. */
public final class CalendarCalculations {
  private static final String DAY_ARGUMENT = "day";

  private CalendarCalculations() {
    throw new AssertionError("Utility class");
  }

  /**
   * Classifies the 30-minute segments in one displayed day.
   *
   * @param day displayed Singapore-local date
   * @param settings Doctor-owned working-hour settings
   * @param now current local clinic timestamp
   * @return immutable adjacent segments with equal classifications merged
   * @throws NullPointerException if an argument is {@code null}
   */
  public static List<CalendarTimeSegment> segmentsForDay(
      LocalDate day, DoctorCalendarSettings settings, LocalDateTime now) {
    Objects.requireNonNull(day, DAY_ARGUMENT);
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(now, "now");
    List<CalendarTimeSegment> segments = new ArrayList<>();
    for (int start = 0; start < WorkingInterval.MINUTES_PER_DAY; start += 30) {
      int end = Math.min(start + 30, WorkingInterval.MINUTES_PER_DAY);
      SegmentKind kind = classify(day, start, settings, now);
      if (!segments.isEmpty() && segments.get(segments.size() - 1).kind() == kind) {
        CalendarTimeSegment previous = segments.remove(segments.size() - 1);
        segments.add(new CalendarTimeSegment(previous.startMinute(), end, kind));
      } else {
        segments.add(new CalendarTimeSegment(start, end, kind));
      }
    }
    return List.copyOf(segments);
  }

  /**
   * Returns the background state at a minute in a displayed day.
   *
   * @param day displayed Singapore-local date
   * @param minute minute to classify
   * @param settings Doctor-owned working-hour settings
   * @param now current local clinic timestamp
   * @return elapsed, non-working, or working visual state
   * @throws NullPointerException if an object argument is {@code null}
   */
  public static SegmentKind classify(
      LocalDate day, int minute, DoctorCalendarSettings settings, LocalDateTime now) {
    Objects.requireNonNull(day, DAY_ARGUMENT);
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(now, "now");
    if (day.isBefore(now.toLocalDate())
        || (day.isEqual(now.toLocalDate()) && minute < minuteOf(now.toLocalTime()))) {
      return SegmentKind.ELAPSED;
    }
    if (!settings.isEnabled(day.getDayOfWeek())
        || settings.intervals(day.getDayOfWeek()).stream()
            .noneMatch(interval -> interval.contains(minute))) {
      return SegmentKind.NON_WORKING;
    }
    return SegmentKind.WORKING;
  }

  /**
   * Returns the current minute offset when the current date is displayed.
   *
   * @param day displayed Singapore-local date
   * @param now current local clinic timestamp
   * @return current minute offset, or {@code -1} when {@code day} is not today
   * @throws NullPointerException if an argument is {@code null}
   */
  public static int currentMinute(LocalDate day, LocalDateTime now) {
    Objects.requireNonNull(day, DAY_ARGUMENT);
    Objects.requireNonNull(now, "now");
    if (!day.isEqual(now.toLocalDate())) {
      return -1;
    }
    return minuteOf(now.toLocalTime());
  }

  /**
   * Creates non-overlapping daily blocks for each appointment visible in a week.
   *
   * @param day displayed Singapore-local date
   * @param appointments appointments whose visible portions should be laid out
   * @return immutable appointment blocks assigned to overlap lanes
   * @throws NullPointerException if an argument is {@code null}
   */
  public static List<CalendarAppointmentBlock> blocksForDay(
      LocalDate day, List<CalendarAppointment> appointments) {
    Objects.requireNonNull(day, DAY_ARGUMENT);
    Objects.requireNonNull(appointments, "appointments");
    LocalDateTime dayStart = day.atStartOfDay();
    LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
    List<BlockSeed> seeds = new ArrayList<>();
    for (CalendarAppointment appointment : appointments) {
      if (!appointment.startsAt().isBefore(dayEnd) || !appointment.endsAt().isAfter(dayStart)) {
        continue;
      }
      int start = minuteOffset(dayStart, appointment.startsAt(), 0);
      int end = minuteOffset(dayStart, appointment.endsAt(), WorkingInterval.MINUTES_PER_DAY);
      start = Math.max(0, Math.min(start, WorkingInterval.MINUTES_PER_DAY - 1));
      end = Math.max(start + 1, Math.min(end, WorkingInterval.MINUTES_PER_DAY));
      seeds.add(new BlockSeed(appointment, start, end));
    }
    seeds.sort(
        Comparator.comparingInt(BlockSeed::startMinute)
            .thenComparingInt(BlockSeed::endMinute)
            .thenComparingLong(seed -> seed.appointment().appointmentId()));
    List<List<BlockSeed>> lanes = new ArrayList<>();
    List<Integer> assignedLanes = new ArrayList<>();
    for (BlockSeed seed : seeds) {
      int lane = firstAvailableLane(lanes, seed);
      while (lanes.size() <= lane) {
        lanes.add(new ArrayList<>());
      }
      lanes.get(lane).add(seed);
      assignedLanes.add(lane);
    }
    List<CalendarAppointmentBlock> blocks = new ArrayList<>();
    for (int index = 0; index < seeds.size(); index++) {
      BlockSeed seed = seeds.get(index);
      blocks.add(
          new CalendarAppointmentBlock(
              seed.appointment(),
              day,
              seed.startMinute(),
              seed.endMinute(),
              assignedLanes.get(index),
              lanes.size()));
    }
    return List.copyOf(blocks);
  }

  private static int firstAvailableLane(List<List<BlockSeed>> lanes, BlockSeed seed) {
    for (int index = 0; index < lanes.size(); index++) {
      List<BlockSeed> lane = lanes.get(index);
      if (lane.get(lane.size() - 1).endMinute() <= seed.startMinute()) {
        return index;
      }
    }
    return lanes.size();
  }

  private static int minuteOffset(LocalDateTime dayStart, LocalDateTime timestamp, int fallback) {
    if (!timestamp.isAfter(dayStart)) {
      return fallback == 0 ? 0 : fallback;
    }
    long minutes = java.time.Duration.between(dayStart, timestamp).toMinutes();
    return Math.toIntExact(Math.min(minutes, WorkingInterval.MINUTES_PER_DAY));
  }

  private static int minuteOf(LocalTime time) {
    return time.getHour() * 60 + time.getMinute();
  }

  private record BlockSeed(CalendarAppointment appointment, int startMinute, int endMinute) {
    // Immutable intermediate layout value.
  }
}
