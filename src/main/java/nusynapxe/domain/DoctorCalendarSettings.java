package nusynapxe.domain;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Doctor-owned preferences that control Calendar ordering and visual working-hour shading. */
public record DoctorCalendarSettings(
    long doctorId,
    DayOfWeek firstDayOfWeek,
    Map<DayOfWeek, List<WorkingInterval>> workingIntervals) {
  /** Creates validated, immutable calendar settings. */
  public DoctorCalendarSettings {
    if (doctorId <= 0) {
      throw new IllegalArgumentException("Doctor identifier must be positive");
    }
    Objects.requireNonNull(firstDayOfWeek, "firstDayOfWeek");
    Objects.requireNonNull(workingIntervals, "workingIntervals");
    Map<DayOfWeek, List<WorkingInterval>> normalized = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) {
      List<WorkingInterval> intervals = workingIntervals.getOrDefault(day, List.of());
      if (intervals == null) {
        throw new IllegalArgumentException("Working intervals cannot be null");
      }
      List<WorkingInterval> sorted = new ArrayList<>(intervals);
      if (sorted.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("Working intervals cannot contain null values");
      }
      sorted.sort(java.util.Comparator.comparingInt(WorkingInterval::startMinute));
      for (int index = 1; index < sorted.size(); index++) {
        WorkingInterval previous = sorted.get(index - 1);
        WorkingInterval current = sorted.get(index);
        if (current.startMinute() < previous.endMinute()) {
          throw new IllegalArgumentException("Working intervals cannot overlap");
        }
      }
      normalized.put(day, List.copyOf(sorted));
    }
    for (DayOfWeek day : workingIntervals.keySet()) {
      if (day == null) {
        throw new IllegalArgumentException("Working interval day cannot be null");
      }
    }
    workingIntervals = Collections.unmodifiableMap(normalized);
  }

  /** Returns the intervals configured for one day, or an empty list for a disabled day. */
  public List<WorkingInterval> intervals(DayOfWeek day) {
    Objects.requireNonNull(day, "day");
    return workingIntervals.getOrDefault(day, List.of());
  }

  /** Returns whether the day has at least one enabled working interval. */
  public boolean isEnabled(DayOfWeek day) {
    return !intervals(day).isEmpty();
  }

  /** Returns the deterministic initial profile used for a Doctor without saved settings. */
  public static DoctorCalendarSettings defaults(long doctorId) {
    Map<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) {
      intervals.put(
          day,
          day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
              ? List.of()
              : List.of(new WorkingInterval(8 * 60, 18 * 60)));
    }
    return new DoctorCalendarSettings(doctorId, DayOfWeek.SUNDAY, intervals);
  }
}
