package nusynapxe.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

final class DomainEqualityTest {
  @Test
  void domainRecordsImplementEqualityAndHashCode() {
    LocalDateTime now = LocalDateTime.of(2026, 9, 7, 9, 0);
    LocalDate today = LocalDate.of(2026, 9, 7);

    CalendarTimeSegment segment1 =
        new CalendarTimeSegment(480, 600, CalendarTimeSegment.SegmentKind.WORKING);
    CalendarTimeSegment segment2 =
        new CalendarTimeSegment(480, 600, CalendarTimeSegment.SegmentKind.WORKING);
    CalendarTimeSegment segmentDiff =
        new CalendarTimeSegment(480, 600, CalendarTimeSegment.SegmentKind.NON_WORKING);
    assertEquals(segment1, segment2);
    assertEquals(segment1.hashCode(), segment2.hashCode());
    assertNotEquals(segment1, segmentDiff);
    assertNotEquals(segment1, null);
    assertNotEquals(segment1, "other");

    DoctorTimeOff timeOff1 = new DoctorTimeOff(1, 2, now, now.plusHours(1));
    DoctorTimeOff timeOff2 = new DoctorTimeOff(1, 2, now, now.plusHours(1));
    DoctorTimeOff timeOffDiff = new DoctorTimeOff(2, 2, now, now.plusHours(1));
    assertEquals(timeOff1, timeOff2);
    assertEquals(timeOff1.hashCode(), timeOff2.hashCode());
    assertNotEquals(timeOff1, timeOffDiff);

    CalendarTimeOffBlock block1 = new CalendarTimeOffBlock(timeOff1, today, 540, 600);
    CalendarTimeOffBlock block2 = new CalendarTimeOffBlock(timeOff2, today, 540, 600);
    CalendarTimeOffBlock blockDiff = new CalendarTimeOffBlock(timeOff1, today, 540, 660);
    assertEquals(block1, block2);
    assertEquals(block1.hashCode(), block2.hashCode());
    assertNotEquals(block1, blockDiff);
    assertNotEquals(block1, null);

    CalendarAppointment apt1 =
        new CalendarAppointment(
            1, 2, "Patient A", now, now.plusMinutes(30), AppointmentStatus.ACCEPTED);
    CalendarAppointment apt2 =
        new CalendarAppointment(
            1, 2, "Patient A", now, now.plusMinutes(30), AppointmentStatus.ACCEPTED);
    CalendarAppointment aptDiff =
        new CalendarAppointment(
            2, 2, "Patient B", now, now.plusMinutes(30), AppointmentStatus.ACCEPTED);
    assertEquals(apt1, apt2);
    assertEquals(apt1.hashCode(), apt2.hashCode());
    assertNotEquals(apt1, aptDiff);
    assertNotEquals(apt1, null);

    CalendarAppointmentBlock aptBlock1 = new CalendarAppointmentBlock(apt1, today, 540, 600, 0, 1);
    CalendarAppointmentBlock aptBlock2 = new CalendarAppointmentBlock(apt2, today, 540, 600, 0, 1);
    CalendarAppointmentBlock aptBlockDiff =
        new CalendarAppointmentBlock(apt1, today, 540, 600, 1, 2);
    assertEquals(aptBlock1, aptBlock2);
    assertEquals(aptBlock1.hashCode(), aptBlock2.hashCode());
    assertNotEquals(aptBlock1, aptBlockDiff);
    assertNotEquals(aptBlock1, null);

    PatientDeletionBlockers.BlockingRelation rel1 =
        new PatientDeletionBlockers.BlockingRelation("Appointments", 3);
    PatientDeletionBlockers.BlockingRelation rel2 =
        new PatientDeletionBlockers.BlockingRelation("Appointments", 3);
    PatientDeletionBlockers.BlockingRelation relDiff =
        new PatientDeletionBlockers.BlockingRelation("Receipts", 1);
    assertEquals(rel1, rel2);
    assertEquals(rel1.hashCode(), rel2.hashCode());
    assertNotEquals(rel1, relDiff);
    assertNotEquals(rel1, null);

    Appointment appointment =
        new Appointment(1, 2, 3, now, now.plusMinutes(30), AppointmentStatus.PENDING);
    AppointmentListRow row1 = new AppointmentListRow(appointment, "Patient A", "Doctor A");
    AppointmentListRow row2 = new AppointmentListRow(appointment, "Patient A", "Doctor A");
    AppointmentListRow rowFallback = new AppointmentListRow(appointment, null, "  ");
    assertEquals(row1, row2);
    assertEquals(row1.hashCode(), row2.hashCode());
    assertEquals("Patient unavailable", rowFallback.patientDisplayName());
    assertEquals("Doctor unavailable", rowFallback.doctorDisplayName());
    assertThrows(NullPointerException.class, () -> new AppointmentListRow(null, "P", "D"));

    Receipt receipt1 =
        new Receipt(1, 2, 3, 4, "Patient", "Doctor", 1000L, PaymentMethod.CASH, today, 1L, now);
    Receipt receipt2 =
        new Receipt(1, 2, 3, 4, "Patient", "Doctor", 1000L, PaymentMethod.CASH, today, 1L, now);
    Receipt receiptDiff =
        new Receipt(2, 2, 3, 4, "Patient", "Doctor", 1000L, PaymentMethod.CASH, today, 2L, now);
    assertEquals(receipt1, receipt2);
    assertEquals(receipt1.hashCode(), receipt2.hashCode());
    assertNotEquals(receipt1, receiptDiff);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Receipt(
                1, 2, 3, 4, "Patient", "Doctor", 1000L, PaymentMethod.CASH, today, 0L, now));
  }
}
