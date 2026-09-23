package nusynapxe.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentListRow;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.RevenueReport;
import nusynapxe.domain.RevenueSummary;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;

/** Serializes Receptionist database-backed refreshes away from the JavaFX thread. */
final class ReceptionistDataLoader {
  private static final String QUEUE_WAITING = "Waiting";
  private static final String QUEUE_CHECKED_IN = "Checked in";
  private static final String QUEUE_ALL = "All";
  private static final String ALL_STATUSES = "All statuses";

  private final ClinicServices services;
  private final Session session;
  private final ClinicTaskRunner taskRunner;
  private long scheduleGeneration;
  private long queueGeneration;
  private long checkoutGeneration;
  private long receiptGeneration;
  private long appointmentDetailsGeneration;
  private final SelectorLoadGeneration doctorGenerations = new SelectorLoadGeneration();
  private boolean disposed;

  ReceptionistDataLoader(ClinicServices services, Session session, ClinicTaskRunner taskRunner) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
  }

  void dispose() {
    disposed = true;
    scheduleGeneration++;
    queueGeneration++;
    checkoutGeneration++;
    receiptGeneration++;
    appointmentDetailsGeneration++;
  }

  void invalidateAppointmentDetails() {
    appointmentDetailsGeneration++;
  }

  void loadCheckInDetails(
      long appointmentId, Consumer<AppointmentDetails> onSuccess, Consumer<Throwable> onFailure) {
    loadAppointmentDetails(appointmentId, onSuccess, onFailure);
  }

  void loadCheckoutDetails(
      long appointmentId, Consumer<AppointmentDetails> onSuccess, Consumer<Throwable> onFailure) {
    loadAppointmentDetails(appointmentId, onSuccess, onFailure);
  }

  void run(
      ClinicTaskRunner.ClinicTask<Void> task, Runnable onSuccess, Consumer<Throwable> onFailure) {
    submit(task, ignored -> onSuccess.run(), onFailure);
  }

  void checkIn(long appointmentId, Runnable onSuccess, Consumer<Throwable> onFailure) {
    run(
        () -> {
          services.appointmentService().checkIn(session, appointmentId);
          return null;
        },
        onSuccess,
        onFailure);
  }

  void checkout(
      long appointmentId,
      long amountMinor,
      PaymentMethod paymentMethod,
      Consumer<Optional<Receipt>> onSuccess,
      Consumer<Throwable> onFailure) {
    submit(
        () -> {
          services.billingService().checkout(session, appointmentId, amountMinor, paymentMethod);
          return services.billingService().receiptForAppointment(session, appointmentId);
        },
        onSuccess,
        onFailure);
  }

  void book(
      long patientId,
      long doctorId,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      Consumer<Appointment> onSuccess,
      Consumer<Throwable> onFailure) {
    submit(
        () -> services.appointmentService().book(session, patientId, doctorId, startsAt, endsAt),
        onSuccess,
        onFailure);
  }

  void cancel(long appointmentId, Runnable onSuccess, Consumer<Throwable> onFailure) {
    run(
        () -> {
          services.appointmentService().cancel(session, appointmentId);
          return null;
        },
        onSuccess,
        onFailure);
  }

  void revenueReport(
      LocalDate from,
      LocalDate to,
      String patientQuery,
      Long doctorId,
      PaymentMethod paymentMethod,
      Consumer<RevenueReport> onSuccess,
      Consumer<Throwable> onFailure) {
    submit(
        () ->
            services
                .billingService()
                .revenueReport(session, from, to, patientQuery, doctorId, paymentMethod),
        onSuccess,
        onFailure);
  }

  void dailyRevenue(
      LocalDate date, Consumer<RevenueSummary> onSuccess, Consumer<Throwable> onFailure) {
    submit(() -> services.billingService().dailyRevenue(session, date), onSuccess, onFailure);
  }

  private void loadAppointmentDetails(
      long appointmentId, Consumer<AppointmentDetails> onSuccess, Consumer<Throwable> onFailure) {
    long generation = appointmentDetailsGeneration + 1;
    appointmentDetailsGeneration = generation;
    submit(
        () -> {
          Appointment appointment = services.appointmentService().get(appointmentId);
          Patient patient =
              services.patientService().getAdministrative(session, appointment.patientId());
          String doctorName =
              services.accountService().listDoctors(session).stream()
                  .filter(doctor -> doctor.id() == appointment.doctorId())
                  .map(Account::displayName)
                  .findFirst()
                  .orElse("Doctor unavailable");
          return new AppointmentDetails(appointment, patient, doctorName);
        },
        details -> {
          if (generation == appointmentDetailsGeneration) {
            onSuccess.accept(details);
          }
        },
        failure -> {
          if (generation == appointmentDetailsGeneration) {
            onFailure.accept(failure);
          }
        });
  }

  void refreshDoctors(SearchSuggestionField<Account> doctor, Label feedback) {
    refreshDoctors(doctor, feedback, true);
  }

  void refreshDoctors(SearchSuggestionField<Account> doctor, Label feedback, boolean selectFirst) {
    long generation = doctorGenerations.next(doctor);
    Account previousDoctor = doctor.getValue();
    submit(
        () -> services.accountService().listDoctors(session),
        doctors -> {
          if (disposed || !doctorGenerations.isCurrent(doctor, generation)) {
            return;
          }
          Account currentDoctor = doctor.getValue();
          boolean selectionChanged = !sameAccount(previousDoctor, currentDoctor);
          doctor.setItems(doctors);
          if (selectionChanged) {
            if (currentDoctor != null) {
              selectDoctor(doctor, currentDoctor.id());
            }
            return;
          }
          boolean restored = previousDoctor != null && selectDoctor(doctor, previousDoctor.id());
          if (!restored && selectFirst && !doctor.getItems().isEmpty()) {
            doctor.select(doctor.getItems().getFirst());
          }
        },
        failure -> {
          if (!disposed && doctorGenerations.isCurrent(doctor, generation)) {
            UiComponents.showError(feedback, "Doctors are temporarily unavailable");
          }
        });
  }

  void refreshCheckoutReady(
      TableView<AppointmentListRow> list,
      Label feedback,
      String patientQuery,
      Long doctorId,
      LocalDate date) {
    checkoutGeneration++;
    long generation = checkoutGeneration;
    submit(
        () ->
            services
                .appointmentService()
                .searchAppointmentRows(
                    session, date, doctorId, patientQuery, AppointmentStatus.COMPLETED),
        appointments -> {
          if (generation == checkoutGeneration) {
            list.setItems(FXCollections.observableArrayList(appointments));
          }
        },
        failure -> {
          if (generation == checkoutGeneration) {
            UiComponents.showError(feedback, "Checkout appointments are temporarily unavailable");
          }
        });
  }

  void refreshReceiptHistory(
      TableView<Receipt> history,
      Label preview,
      String patientQuery,
      Long doctorId,
      LocalDate date,
      Label feedback) {
    receiptGeneration++;
    long generation = receiptGeneration;
    submit(
        () -> services.billingService().receiptHistory(session, patientQuery, doctorId, date),
        receipts -> {
          if (generation == receiptGeneration) {
            history.setItems(FXCollections.observableArrayList(receipts));
            preview.setText("");
          }
        },
        failure -> {
          if (generation == receiptGeneration) {
            UiComponents.showError(feedback, "Receipt history is temporarily unavailable");
          }
        });
  }

  void refreshSchedule(
      TableView<AppointmentListRow> appointmentList,
      ReceptionistAppointmentPanel.SelectionState selection,
      Label feedback,
      LocalDate date,
      Long doctorId,
      String patientQuery,
      String status,
      Label summary) {
    scheduleGeneration++;
    long generation = scheduleGeneration;
    submit(
        () ->
            services
                .appointmentService()
                .searchAppointmentRows(
                    session, date, doctorId, patientQuery, selectedAppointmentStatus(status)),
        appointments -> {
          if (generation != scheduleGeneration) {
            return;
          }
          appointmentList.setItems(FXCollections.observableArrayList(appointments));
          selectAppointment(appointmentList, selection.appointmentId);
          summary.setText(scheduleSummary(appointments));
        },
        failure -> {
          if (generation == scheduleGeneration) {
            UiComponents.showError(feedback, "Appointments are temporarily unavailable");
          }
        });
  }

  void refreshQueue(
      TableView<AppointmentListRow> queue,
      Label feedback,
      LocalDate date,
      Long doctorId,
      String patientQuery,
      String status,
      Label summary) {
    queueGeneration++;
    long generation = queueGeneration;
    submit(
        () -> {
          List<AppointmentListRow> appointments = new ArrayList<>();
          boolean includeWaiting =
              status == null || QUEUE_ALL.equals(status) || QUEUE_WAITING.equals(status);
          boolean includeChecked =
              status == null || QUEUE_ALL.equals(status) || QUEUE_CHECKED_IN.equals(status);
          if (includeWaiting) {
            appointments.addAll(
                services
                    .appointmentService()
                    .searchAppointmentRows(
                        session, date, doctorId, patientQuery, AppointmentStatus.ACCEPTED));
          }
          if (includeChecked) {
            appointments.addAll(
                services
                    .appointmentService()
                    .searchAppointmentRows(
                        session, date, doctorId, patientQuery, AppointmentStatus.CHECKED_IN));
          }
          appointments.sort(Comparator.comparing(row -> row.appointment().startsAt()));
          return appointments;
        },
        appointments -> {
          if (generation != queueGeneration) {
            return;
          }
          queue.setItems(FXCollections.observableArrayList(appointments));
          long waiting =
              appointments.stream()
                  .filter(row -> row.appointment().status() == AppointmentStatus.ACCEPTED)
                  .count();
          long checkedIn =
              appointments.stream()
                  .filter(row -> row.appointment().status() == AppointmentStatus.CHECKED_IN)
                  .count();
          summary.setText(
              "Waiting: "
                  + waiting
                  + " | Checked in: "
                  + checkedIn
                  + " | Total: "
                  + appointments.size());
        },
        failure -> {
          if (generation == queueGeneration) {
            UiComponents.showError(feedback, "Check-in queue is temporarily unavailable");
          }
        });
  }

  private <T> void submit(
      ClinicTaskRunner.ClinicTask<T> task,
      java.util.function.Consumer<T> onSuccess,
      java.util.function.Consumer<Throwable> onFailure) {
    Consumer<T> guardedSuccess =
        value -> {
          if (!disposed) {
            onSuccess.accept(value);
          }
        };
    Consumer<Throwable> guardedFailure =
        failure -> {
          if (!disposed) {
            onFailure.accept(failure);
          }
        };
    try {
      taskRunner.submit(task, guardedSuccess, guardedFailure);
    } catch (RejectedExecutionException exception) {
      guardedFailure.accept(exception);
    }
  }

  record AppointmentDetails(Appointment appointment, Patient patient, String doctorName) {
    AppointmentDetails {
      Objects.requireNonNull(appointment, "appointment");
      Objects.requireNonNull(patient, "patient");
      Objects.requireNonNull(doctorName, "doctorName");
    }
  }

  private static AppointmentStatus selectedAppointmentStatus(String value) {
    if (value == null || ALL_STATUSES.equals(value)) {
      return null;
    }
    return AppointmentStatus.valueOf(value.toUpperCase(Locale.ROOT).replace(' ', '_'));
  }

  private static String scheduleSummary(List<AppointmentListRow> appointments) {
    return appointments.size()
        + " appointment(s) | Pending: "
        + count(appointments, AppointmentStatus.PENDING)
        + " | Accepted: "
        + count(appointments, AppointmentStatus.ACCEPTED)
        + " | Declined: "
        + count(appointments, AppointmentStatus.DECLINED)
        + " | Checked in: "
        + count(appointments, AppointmentStatus.CHECKED_IN)
        + " | Completed: "
        + count(appointments, AppointmentStatus.COMPLETED);
  }

  private static long count(List<AppointmentListRow> appointments, AppointmentStatus status) {
    return appointments.stream().filter(row -> row.appointment().status() == status).count();
  }

  private static boolean selectDoctor(SearchSuggestionField<Account> doctor, long doctorId) {
    if (doctorId <= 0) {
      return false;
    }
    return doctor.getItems().stream()
        .filter(account -> account.id() == doctorId)
        .findFirst()
        .map(
            account -> {
              doctor.select(account);
              return true;
            })
        .orElse(false);
  }

  private static boolean sameAccount(Account left, Account right) {
    if (left == null || right == null) {
      return left == null && right == null;
    }
    return left.id() == right.id();
  }

  private static void selectAppointment(TableView<AppointmentListRow> list, long appointmentId) {
    if (appointmentId == 0) {
      list.getSelectionModel().clearSelection();
      return;
    }
    list.getItems().stream()
        .filter(row -> row.appointment().id() == appointmentId)
        .findFirst()
        .ifPresent(list.getSelectionModel()::select);
  }
}
