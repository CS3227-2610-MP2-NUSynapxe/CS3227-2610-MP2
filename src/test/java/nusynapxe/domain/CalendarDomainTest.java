package nusynapxe.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CalendarDomainTest {
  @Test
  void defaultsEnableWeekdaysAndKeepSundayAsFirstDay() {
    DoctorCalendarSettings settings = DoctorCalendarSettings.defaults(7);

    assertEquals(DayOfWeek.SUNDAY, settings.firstDayOfWeek());
    assertEquals(List.of(new WorkingInterval(480, 1080)), settings.intervals(DayOfWeek.MONDAY));
    assertEquals(List.of(), settings.intervals(DayOfWeek.SUNDAY));
  }

  @Test
  void rejectsInvalidAndOverlappingWorkingIntervals() {
    assertThrows(IllegalArgumentException.class, () -> new WorkingInterval(600, 600));
    assertThrows(IllegalArgumentException.class, () -> new WorkingInterval(0, 1441));

    EnumMap<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
    intervals.put(
        DayOfWeek.MONDAY, List.of(new WorkingInterval(480, 720), new WorkingInterval(600, 780)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DoctorCalendarSettings(7, DayOfWeek.MONDAY, intervals));
  }

  @Test
  void normalizesWeeksForTheConfiguredFirstDay() {
    CalendarWeek mondayWeek = CalendarWeek.containing(LocalDate.of(2026, 9, 2), DayOfWeek.MONDAY);
    CalendarWeek sundayWeek = CalendarWeek.containing(LocalDate.of(2026, 9, 2), DayOfWeek.SUNDAY);

    assertEquals(LocalDate.of(2026, 8, 31), mondayWeek.start());
    assertEquals(LocalDate.of(2026, 8, 30), sundayWeek.start());
    assertEquals(LocalDate.of(2026, 9, 7), mondayWeek.next().start());
    assertEquals(7, mondayWeek.dates().size());
  }

  @Test
  void doctorCalendarWeekFreezesTimeOffProjection() {
    DoctorTimeOff interval =
        new DoctorTimeOff(
            11, 7, LocalDateTime.of(2026, 9, 7, 9, 0), LocalDateTime.of(2026, 9, 7, 10, 0));
    List<DoctorTimeOff> timeOff = new ArrayList<>(List.of(interval));

    DoctorCalendarWeek week =
        new DoctorCalendarWeek(
            7, LocalDate.of(2026, 9, 7), DoctorCalendarSettings.defaults(7), List.of(), timeOff);
    timeOff.clear();

    assertEquals(List.of(interval), week.timeOff());
    assertThrows(UnsupportedOperationException.class, () -> week.timeOff().clear());
    assertThrows(
        NullPointerException.class,
        () ->
            new DoctorCalendarWeek(
                7, LocalDate.of(2026, 9, 7), DoctorCalendarSettings.defaults(7), List.of(), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DoctorCalendarWeek(
                7,
                LocalDate.of(2026, 9, 7),
                DoctorCalendarSettings.defaults(8),
                List.of(),
                List.of()));
  }
}
