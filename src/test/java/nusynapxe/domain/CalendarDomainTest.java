package nusynapxe.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
  void calendarWeekValidatesAndFormatsYearCrossingWeek() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarWeek(LocalDate.of(2026, 9, 7), DayOfWeek.SUNDAY));
    assertThrows(NullPointerException.class, () -> new CalendarWeek(null, DayOfWeek.MONDAY));
    assertThrows(
        NullPointerException.class, () -> new CalendarWeek(LocalDate.of(2026, 9, 7), null));

    CalendarWeek crossYearWeek =
        CalendarWeek.containing(LocalDate.of(2026, 12, 31), DayOfWeek.MONDAY);
    assertTrue(crossYearWeek.label().contains("2026"));
    assertTrue(crossYearWeek.label().contains("2027"));
    assertEquals(LocalDate.of(2026, 12, 21), crossYearWeek.previous().start());

    Clock fixedClock = Clock.fixed(Instant.parse("2026-09-02T10:00:00Z"), ZoneOffset.UTC);
    assertEquals(
        LocalDate.of(2026, 8, 31), CalendarWeek.today(fixedClock, DayOfWeek.MONDAY).start());
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

  @Test
  void calendarTimeSegmentValidatesGeometryAndKind() {
    CalendarTimeSegment segment =
        new CalendarTimeSegment(480, 600, CalendarTimeSegment.SegmentKind.WORKING);
    assertEquals(480, segment.startMinute());
    assertEquals(600, segment.endMinute());
    assertEquals(CalendarTimeSegment.SegmentKind.WORKING, segment.kind());

    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarTimeSegment(-1, 600, CalendarTimeSegment.SegmentKind.WORKING));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarTimeSegment(480, 1441, CalendarTimeSegment.SegmentKind.WORKING));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarTimeSegment(600, 600, CalendarTimeSegment.SegmentKind.WORKING));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarTimeSegment(700, 600, CalendarTimeSegment.SegmentKind.WORKING));
    assertThrows(NullPointerException.class, () -> new CalendarTimeSegment(480, 600, null));
  }

  @Test
  void calendarTimeOffBlockValidatesBounds() {
    LocalDate day = LocalDate.of(2026, 9, 7);
    DoctorTimeOff timeOff =
        new DoctorTimeOff(
            11, 7, LocalDateTime.of(2026, 9, 7, 9, 0), LocalDateTime.of(2026, 9, 7, 10, 0));

    CalendarTimeOffBlock block = new CalendarTimeOffBlock(timeOff, day, 540, 600);
    assertEquals(timeOff, block.timeOff());
    assertEquals(day, block.day());

    assertThrows(NullPointerException.class, () -> new CalendarTimeOffBlock(null, day, 540, 600));
    assertThrows(
        NullPointerException.class, () -> new CalendarTimeOffBlock(timeOff, null, 540, 600));
    assertThrows(
        IllegalArgumentException.class, () -> new CalendarTimeOffBlock(timeOff, day, -1, 600));
    assertThrows(
        IllegalArgumentException.class, () -> new CalendarTimeOffBlock(timeOff, day, 540, 1441));
    assertThrows(
        IllegalArgumentException.class, () -> new CalendarTimeOffBlock(timeOff, day, 600, 600));
    assertThrows(
        IllegalArgumentException.class, () -> new CalendarTimeOffBlock(timeOff, day, 700, 600));
  }

  @Test
  void calendarAppointmentBlockValidatesGeometryAndLanes() {
    LocalDate day = LocalDate.of(2026, 9, 7);
    CalendarAppointment appointment =
        new CalendarAppointment(
            1,
            2,
            "Patient",
            LocalDateTime.of(2026, 9, 7, 9, 0),
            LocalDateTime.of(2026, 9, 7, 10, 0),
            AppointmentStatus.ACCEPTED);

    CalendarAppointmentBlock block = new CalendarAppointmentBlock(appointment, day, 540, 600, 0, 1);
    assertNotNull(block);

    assertThrows(
        NullPointerException.class, () -> new CalendarAppointmentBlock(null, day, 540, 600, 0, 1));
    assertThrows(
        NullPointerException.class,
        () -> new CalendarAppointmentBlock(appointment, null, 540, 600, 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarAppointmentBlock(appointment, day, -1, 600, 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarAppointmentBlock(appointment, day, 540, 1441, 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarAppointmentBlock(appointment, day, 600, 600, 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarAppointmentBlock(appointment, day, 540, 600, -1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarAppointmentBlock(appointment, day, 540, 600, 1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarAppointmentBlock(appointment, day, 540, 600, 0, 0));
  }
}
