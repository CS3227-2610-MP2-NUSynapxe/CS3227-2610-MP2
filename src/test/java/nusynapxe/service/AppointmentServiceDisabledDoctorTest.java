package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import nusynapxe.domain.Account;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AppointmentServiceDisabledDoctorTest {
  private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 9, 0);
  private static final LocalDateTime END = START.plusMinutes(30);

  @TempDir private Path temporaryDirectory;

  @Test
  void rejectsNewAppointmentsForDisabledDoctorsButKeepsSchedulesReadable() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository accounts = new AccountRepository(database);
      Account doctor =
          accounts.create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
      Account receptionist =
          accounts.create(
              "reception", "Reception", Role.RECEPTIONIST, new byte[] {3}, new byte[] {4});
      Patient patient =
          new PatientRepository(database)
              .create(
                  new Patient(
                      0,
                      "Grace",
                      "Hopper",
                      "1906-12-09",
                      "555-0100",
                      "grace@example.test",
                      "Address"));
      AppointmentService service =
          new AppointmentService(
              new AppointmentRepository(database), accounts, new PatientRepository(database));
      Session doctorSession = new Session(doctor.id(), doctor.username(), doctor.role());
      Session receptionistSession =
          new Session(receptionist.id(), receptionist.username(), receptionist.role());
      accounts.setEnabled(doctor.id(), false);

      assertThrows(
          ValidationException.class,
          () -> service.book(receptionistSession, patient.id(), doctor.id(), START, END));
      assertThrows(
          ValidationException.class,
          () -> service.book(doctorSession, patient.id(), doctor.id(), START, END));
      assertEquals(List.of(), service.schedule(receptionistSession, doctor.id()));
    }
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("appointments.db"));
    database.open();
    return database;
  }
}
