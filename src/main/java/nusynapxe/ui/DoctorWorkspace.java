package nusynapxe.ui;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds the Doctor schedule and clinical consultation workspace. */
final class DoctorWorkspace {
  private static final String APPOINTMENT_REQUIRED = "Select an appointment first";
  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
  private static final String FIELD_SEPARATOR = " | ";
  private static final String ACTIVE_NAVIGATION_STYLE = "active-navigation";
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

  private DoctorWorkspace() {
    throw new AssertionError("Utility class");
  }

  /**
   * Creates the Doctor workspace.
   *
   * @param services application services used by the workspace
   * @param session authenticated Doctor session
   * @param onLogout callback invoked when the Doctor logs out
   * @return root node for the Doctor workspace
   * @throws NullPointerException if an argument is {@code null}
   */
  public static Parent create(ClinicServices services, Session session, Runnable onLogout) {
    return create(
        services,
        session,
        onLogout,
        Clock.system(CalendarService.CLINIC_ZONE),
        ClinicTaskRunner.immediate());
  }

  static Parent create(ClinicServices services, Session session, Runnable onLogout, Clock clock) {
    return create(services, session, onLogout, clock, ClinicTaskRunner.immediate());
  }

  static Parent create(
      ClinicServices services,
      Session session,
      Runnable onLogout,
      Clock clock,
      ClinicTaskRunner taskRunner) {
    Clock clinicClock =
        Objects.requireNonNull(clock, "clock").withZone(CalendarService.CLINIC_ZONE);
    SelectionState selection = new SelectionState();

    Button accept = UiComponents.primaryButton("Accept", "doctor-accept");
    Button decline = UiComponents.dangerButton("Decline", "doctor-decline");
    Button checkIn = UiComponents.primaryButton("Check in", "doctor-check-in");
    Button reschedule = UiComponents.secondaryButton("Reschedule", "doctor-reschedule");

    TextField diagnosis = field("doctor-diagnosis", "Diagnosis");
    TextArea consultationNotes = textArea("doctor-consultation-notes", "Consultation notes");
    TextArea followUpNotes = textArea("doctor-follow-up", "Follow-up notes");
    Button saveConsultation =
        UiComponents.primaryButton("Save consultation", "doctor-consultation-save");

    TextField medication = field("doctor-medication", "Medication");
    TextField dosage = field("doctor-dosage", "Dosage");
    TextField frequency = field("doctor-frequency", "Frequency");
    TextField duration = field("doctor-duration", "Duration");
    TextField instructions = field("doctor-instructions", "Instructions");
    Button addPrescription =
        UiComponents.primaryButton("Add prescription", "doctor-prescription-submit");
    ListView<Prescription> prescriptions = new ListView<>();
    prescriptions.setId("doctor-prescription-list");
    prescriptions.setPlaceholder(
        UiComponents.emptyState("doctor-prescription-empty", "No prescriptions have been added."));
    prescriptions.setCellFactory(
        view ->
            new ListCell<>() {
              @Override
              protected void updateItem(Prescription prescription, boolean empty) {
                super.updateItem(prescription, empty);
                setText(
                    empty || prescription == null
                        ? null
                        : prescription.medication()
                            + FIELD_SEPARATOR
                            + prescription.dosage()
                            + FIELD_SEPARATOR
                            + prescription.frequency()
                            + FIELD_SEPARATOR
                            + prescription.duration()
                            + FIELD_SEPARATOR
                            + prescription.instructions());
              }
            });
    Button complete = UiComponents.primaryButton("Mark consultation completed", "doctor-complete");
    Label feedback = UiComponents.feedback("doctor-feedback");
    Label selectionSummary = new Label();
    selectionSummary.setId("doctor-selected-appointment");
    selectionSummary.getStyleClass().add("selection-summary");
    selectionSummary.setWrapText(true);
    ClinicalHistoryView clinicalHistoryView =
        ClinicalHistoryView.create(services, session, feedback, taskRunner);
    Runnable[] showHistoryHolder = new Runnable[1];
    Button viewPatientHistory =
        UiComponents.secondaryButton("View consultation history", "doctor-view-patient-history");
    viewPatientHistory.setOnAction(
        event -> {
          if (selection.patientId == 0) {
            UiComponents.showError(feedback, APPOINTMENT_REQUIRED);
            return;
          }
          clinicalHistoryView.showPatient(selection.patientId);
          showHistoryHolder[0].run();
        });
    Label noSelection =
        UiComponents.emptyState(
            "doctor-no-selection",
            "Select an appointment from the schedule to edit its consultation.");
    DoctorDashboardDayView[] dashboardHolder = new DoctorDashboardDayView[1];
    SelectionNodes[] nodesHolder = new SelectionNodes[1];

    accept.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            run(
                feedback,
                taskRunner,
                () -> {
                  services.appointmentService().accept(session, selection.appointmentId);
                  return null;
                },
                ignored -> {
                  feedback.setText("Appointment accepted");
                  dashboardHolder[0].refresh();
                });
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });

    decline.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            run(
                feedback,
                taskRunner,
                () -> {
                  services.appointmentService().decline(session, selection.appointmentId);
                  return null;
                },
                ignored -> {
                  feedback.setText("Appointment declined");
                  dashboardHolder[0].refresh();
                });
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });

    checkIn.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            run(
                feedback,
                taskRunner,
                () -> {
                  services.appointmentService().checkIn(session, selection.appointmentId);
                  return null;
                },
                ignored -> {
                  feedback.setText("Patient checked in");
                  dashboardHolder[0].refresh();
                });
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });

    reschedule.setOnAction(
        event -> {
          if (selection.appointmentId == 0) {
            UiComponents.showError(feedback, APPOINTMENT_REQUIRED);
            return;
          }
          AppointmentDialog.showDoctorEdit(
              services,
              session,
              selection.appointmentId,
              feedback,
              dashboardHolder[0]::refresh,
              taskRunner);
        });

    saveConsultation.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            long appointmentId = selection.appointmentId;
            String diagnosisValue = diagnosis.getText();
            String consultationValue = consultationNotes.getText();
            String followUpValue = followUpNotes.getText();
            run(
                feedback,
                taskRunner,
                () -> {
                  services
                      .clinicalService()
                      .saveConsultation(
                          session, appointmentId, diagnosisValue, consultationValue, followUpValue);
                  return services.appointmentService().get(appointmentId);
                },
                appointment -> {
                  feedback.setText("Consultation saved");
                  DoctorConsultationView.loadClinicalAsync(
                      services,
                      session,
                      appointment,
                      diagnosis,
                      consultationNotes,
                      followUpNotes,
                      prescriptions,
                      feedback,
                      taskRunner,
                      clinicalLoaded -> {
                        if (!clinicalLoaded) {
                          setClinicalEditable(nodesHolder[0], false);
                        }
                      });
                });
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });

    addPrescription.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            long appointmentId = selection.appointmentId;
            String medicationValue = medication.getText();
            String dosageValue = dosage.getText();
            String frequencyValue = frequency.getText();
            String durationValue = duration.getText();
            String instructionsValue = instructions.getText();
            run(
                feedback,
                taskRunner,
                () -> {
                  services
                      .clinicalService()
                      .addPrescription(
                          session,
                          appointmentId,
                          medicationValue,
                          dosageValue,
                          frequencyValue,
                          durationValue,
                          instructionsValue);
                  return services.appointmentService().get(appointmentId);
                },
                appointment -> {
                  feedback.setText("Prescription added");
                  clear(medication, dosage, frequency, duration, instructions);
                  DoctorConsultationView.loadClinicalAsync(
                      services,
                      session,
                      appointment,
                      diagnosis,
                      consultationNotes,
                      followUpNotes,
                      prescriptions,
                      feedback,
                      taskRunner,
                      clinicalLoaded -> {
                        if (!clinicalLoaded) {
                          setClinicalEditable(nodesHolder[0], false);
                        }
                      });
                });
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });

    complete.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            run(
                feedback,
                taskRunner,
                () -> {
                  services.appointmentService().complete(session, selection.appointmentId);
                  return null;
                },
                ignored -> {
                  feedback.setText("Appointment marked completed");
                  dashboardHolder[0].refresh();
                });
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });

    DoctorCalendarView[] calendarHolder = new DoctorCalendarView[1];
    PatientDirectoryView[] patientDirectoryHolder = new PatientDirectoryView[1];
    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(
        event -> {
          clinicalHistoryView.dispose();
          if (patientDirectoryHolder[0] != null) {
            patientDirectoryHolder[0].dispose();
          }
          if (calendarHolder[0] != null) {
            calendarHolder[0].dispose();
          }
          if (dashboardHolder[0] != null) {
            dashboardHolder[0].dispose();
          }
          onLogout.run();
        });
    HBox header = UiComponents.workspaceHeader("DOCTOR workspace", session.username(), logout);

    VBox prescriptionForm =
        new VBox(
            10,
            UiComponents.fieldGroup("Medication", medication),
            UiComponents.fieldGroup("Dosage", dosage),
            UiComponents.fieldGroup("Frequency", frequency),
            UiComponents.fieldGroup("Duration", duration),
            UiComponents.fieldGroup("Instructions", instructions));

    HBox consultationActions = UiComponents.actionBar(saveConsultation);
    VBox consultationCard =
        UiComponents.card(
            "doctor-consultation-card",
            UiComponents.sectionHeading("Consultation"),
            UiComponents.fieldGroup("Diagnosis", diagnosis),
            UiComponents.fieldGroup("Consultation notes", consultationNotes),
            UiComponents.fieldGroup("Follow-up notes", followUpNotes),
            consultationActions);
    HBox prescriptionActions = UiComponents.actionBar(addPrescription);
    VBox prescriptionCard =
        UiComponents.card(
            "doctor-prescription-card",
            UiComponents.sectionHeading("Prescriptions"),
            prescriptionForm,
            prescriptionActions,
            prescriptions);
    HBox completionActions = UiComponents.actionBar(complete);
    VBox completionCard =
        UiComponents.card(
            "doctor-completion-card",
            UiComponents.sectionHeading("Complete visit"),
            UiComponents.supportingText(
                "Complete the consultation when the clinical record is ready."),
            completionActions);
    VBox patientDetailsCard = UiComponents.card("doctor-patient-details-card");
    VBox appointmentActionsCard = UiComponents.card("doctor-appointment-actions-card");
    Label terminalStatusMessage = UiComponents.supportingText("");
    terminalStatusMessage.setId("doctor-status-message");
    VBox terminalStatusCard =
        UiComponents.card(
            "doctor-terminal-status-card",
            UiComponents.sectionHeading("Appointment status"),
            terminalStatusMessage);
    Label clinicalReadOnly =
        UiComponents.supportingText(
            "This consultation is complete and can only be viewed from the Dashboard.");
    clinicalReadOnly.setId("doctor-clinical-read-only");
    VBox selectedContent = new VBox(16);
    selectedContent.setId("doctor-selected-content");
    SelectionNodes nodes =
        new SelectionNodes(
            selectionSummary,
            noSelection,
            selectedContent,
            patientDetailsCard,
            appointmentActionsCard,
            terminalStatusCard,
            terminalStatusMessage,
            clinicalReadOnly,
            consultationCard,
            prescriptionCard,
            completionCard,
            consultationActions,
            prescriptionForm,
            prescriptionActions,
            completionActions,
            accept,
            decline,
            checkIn,
            reschedule,
            saveConsultation,
            addPrescription,
            complete,
            diagnosis,
            consultationNotes,
            followUpNotes,
            prescriptions,
            viewPatientHistory);
    nodesHolder[0] = nodes;
    updateSelectionState(selection, nodes, null, null, clinicClock);

    dashboardHolder[0] =
        new DoctorDashboardDayView(
            services,
            session,
            feedback,
            calendarAppointment ->
                selectDashboardAppointment(
                    services,
                    session,
                    calendarAppointment,
                    selection,
                    nodes,
                    feedback,
                    clinicClock,
                    taskRunner),
            clinicClock,
            taskRunner);
    VBox scheduleCard =
        UiComponents.card(
            "doctor-schedule-card",
            UiComponents.pageTitle("Dashboard"),
            UiComponents.supportingText("Select an appointment to open its clinical context."),
            dashboardHolder[0].view());
    VBox detailContent = new VBox(16, noSelection, selectionSummary, selectedContent);
    detailContent.setId("doctor-detail-pane");
    ScrollPane detailScroll = new ScrollPane(detailContent);
    detailScroll.setId("doctor-detail-scroll");
    detailScroll.setFitToWidth(true);
    SplitPane masterDetail = new SplitPane(scheduleCard, detailScroll);
    masterDetail.setId("doctor-master-detail");
    masterDetail.setDividerPositions(0.36);

    PatientDirectoryView patientDirectory =
        PatientDirectoryView.create(
            services,
            session,
            "doctor",
            feedback,
            ignoredPatientId -> {
              dashboardHolder[0].refresh();
              clinicalHistoryView.refreshPatients();
            },
            () -> showHistoryHolder[0].run(),
            clinicClock,
            taskRunner);
    patientDirectoryHolder[0] = patientDirectory;
    setVisibleManaged(clinicalHistoryView.view(), false);
    StackPane patientPages = new StackPane(patientDirectory.view(), clinicalHistoryView.view());
    patientPages.setId("doctor-patient-pages");
    patientPages.setMaxHeight(Double.MAX_VALUE);
    ScrollPane patientsPage = new ScrollPane(patientPages);
    patientsPage.setId("doctor-patients-page");
    patientsPage.setFitToWidth(true);
    patientsPage.setFitToHeight(true);

    StackPane pages = new StackPane(masterDetail);
    pages.setId("doctor-page-content");
    Button dashboardNavigation = UiComponents.secondaryButton("Dashboard", "doctor-nav-dashboard");
    Button patientsNavigation = UiComponents.secondaryButton("Patients", "doctor-nav-patients");
    Button calendarNavigation = UiComponents.secondaryButton("Calendar", "doctor-nav-calendar");
    dashboardNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
    Label navigationTitle = new Label("Navigation");
    navigationTitle.setId("doctor-navigation-title");
    navigationTitle.getStyleClass().add("navigation-title");
    navigationTitle.setMaxWidth(Double.MAX_VALUE);
    VBox navigation =
        new VBox(0, navigationTitle, dashboardNavigation, patientsNavigation, calendarNavigation);
    navigation.setId("doctor-navigation");
    DoctorCalendarSettingsView[] settingsHolder = new DoctorCalendarSettingsView[1];
    Runnable showDirectory =
        () -> {
          setVisibleManaged(patientDirectory.view(), true);
          setVisibleManaged(clinicalHistoryView.view(), false);
          patientDirectory.refresh();
          clinicalHistoryView.refreshPatients();
        };
    Runnable showClinicalHistory =
        () -> {
          dashboardHolder[0].hide();
          calendarHolder[0].hide();
          setVisibleManaged(patientDirectory.view(), false);
          setVisibleManaged(clinicalHistoryView.view(), true);
          pages.getChildren().setAll(patientsPage);
          patientsNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          dashboardNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          calendarNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        };
    showHistoryHolder[0] = showClinicalHistory;
    Runnable showCalendar =
        () -> {
          dashboardHolder[0].hide();
          calendarHolder[0].show();
          pages.getChildren().setAll(calendarHolder[0].view());
          calendarNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          dashboardNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          patientsNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        };
    Runnable showSettings =
        () -> {
          dashboardHolder[0].hide();
          calendarHolder[0].hide();
          settingsHolder[0].reload();
          pages.getChildren().setAll(settingsHolder[0].view());
          calendarNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          dashboardNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          patientsNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        };
    calendarHolder[0] =
        new DoctorCalendarView(services, session, showSettings, feedback, clinicClock, taskRunner);
    settingsHolder[0] =
        new DoctorCalendarSettingsView(services, session, showCalendar, showCalendar, feedback);
    dashboardNavigation.setOnAction(
        event -> {
          calendarHolder[0].hide();
          dashboardHolder[0].show();
          pages.getChildren().setAll(masterDetail);
          dashboardNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          patientsNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          calendarNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        });
    patientsNavigation.setOnAction(
        event -> {
          calendarHolder[0].hide();
          dashboardHolder[0].hide();
          showDirectory.run();
          pages.getChildren().setAll(patientsPage);
          patientsNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          dashboardNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          calendarNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        });
    calendarNavigation.setOnAction(event -> showCalendar.run());

    BorderPane root = new BorderPane();
    root.setId("doctor-workspace");
    root.getStyleClass().add("workspace-shell");
    root.setPadding(new Insets(24));
    root.setTop(header);
    root.setLeft(navigation);
    BorderPane.setMargin(navigation, new Insets(0, 16, 0, 0));
    root.setCenter(pages);
    dashboardHolder[0].show();
    return UiComponents.notificationOverlay(root, feedback);
  }

  private static TextField field(String id, String prompt) {
    TextField field = new TextField();
    field.setId(id);
    field.setPromptText(prompt);
    return field;
  }

  private static TextArea textArea(String id, String prompt) {
    TextArea area = new TextArea();
    area.setId(id);
    area.setPromptText(prompt);
    area.setPrefRowCount(3);
    return area;
  }

  private static void updateSelectionState(
      SelectionState selection,
      SelectionNodes nodes,
      Appointment appointment,
      Patient patient,
      Clock clock) {
    boolean selected = appointment != null && patient != null && selection.appointmentId != 0;
    nodes.summary().setVisible(selected);
    nodes.summary().setManaged(selected);
    nodes.noSelection().setVisible(!selected);
    nodes.noSelection().setManaged(!selected);
    nodes.selectedContent().getChildren().clear();
    setVisibleManaged(nodes.patientDetailsCard(), selected);
    setVisibleManaged(nodes.appointmentActionsCard(), false);
    setVisibleManaged(nodes.terminalStatusCard(), false);
    setActionVisibility(nodes, false);

    if (!selected) {
      nodes.patientDetailsCard().getChildren().clear();
      DoctorConsultationView.clearClinical(
          nodes.diagnosis(),
          nodes.consultationNotes(),
          nodes.followUpNotes(),
          nodes.prescriptions());
      setClinicalEditable(nodes, false);
      nodes.summary().getStyleClass().removeIf(style -> style.startsWith("status-"));
      return;
    }

    String readableStatus = displayStatus(appointment.status().name());
    nodes
        .summary()
        .setText(
            patientDisplayName(patient)
                + "  •  "
                + appointment.startsAt().format(DATE_TIME_FORMAT)
                + "  •  "
                + readableStatus);
    nodes
        .summary()
        .setAccessibleText(
            "Appointment for " + patientDisplayName(patient) + ", " + readableStatus);
    updateSummaryStatus(nodes.summary(), appointment.status());

    setClinicalEditable(nodes, false);
    GridPane patientDetails = PatientDirectoryView.patientDetailsGrid(patient);
    patientDetails.setId("doctor-patient-details");
    nodes
        .patientDetailsCard()
        .getChildren()
        .setAll(
            UiComponents.sectionHeading("Patient details"),
            patientDetails,
            UiComponents.actionBar(nodes.viewPatientHistory()));
    nodes.selectedContent().getChildren().add(nodes.patientDetailsCard());

    switch (appointment.status()) {
      case PENDING -> {
        setActionVisibility(nodes, true);
        nodes.checkIn().setVisible(false);
        nodes.checkIn().setManaged(false);
        nodes.accept().setDisable(false);
        nodes.decline().setDisable(false);
        nodes.reschedule().setDisable(false);
        nodes
            .appointmentActionsCard()
            .getChildren()
            .setAll(
                UiComponents.sectionHeading("Appointment actions"),
                UiComponents.actionBar(nodes.accept(), nodes.decline(), nodes.reschedule()));
        setVisibleManaged(nodes.appointmentActionsCard(), true);
        nodes.selectedContent().getChildren().add(nodes.appointmentActionsCard());
      }
      case ACCEPTED -> {
        setActionVisibility(nodes, true);
        nodes.accept().setVisible(false);
        nodes.accept().setManaged(false);
        nodes.decline().setDisable(false);
        nodes.reschedule().setDisable(false);
        boolean checkInEligible = !LocalDateTime.now(clock).isBefore(appointment.startsAt());
        nodes.checkIn().setDisable(!checkInEligible);
        nodes
            .appointmentActionsCard()
            .getChildren()
            .setAll(
                UiComponents.sectionHeading("Appointment actions"),
                UiComponents.actionBar(nodes.decline(), nodes.reschedule(), nodes.checkIn()));
        setVisibleManaged(nodes.appointmentActionsCard(), true);
        nodes.selectedContent().getChildren().add(nodes.appointmentActionsCard());
      }
      case CHECKED_IN -> {
        setClinicalEditable(nodes, true);
        nodes
            .selectedContent()
            .getChildren()
            .addAll(nodes.consultationCard(), nodes.prescriptionCard(), nodes.completionCard());
      }
      case COMPLETED, CHECKED_OUT -> {
        setClinicalEditable(nodes, false);
        nodes
            .selectedContent()
            .getChildren()
            .addAll(nodes.clinicalReadOnly(), nodes.consultationCard(), nodes.prescriptionCard());
      }
      case DECLINED, CANCELLED -> {
        nodes
            .terminalStatusMessage()
            .setText(
                "No Doctor actions are available for this " + readableStatus + " appointment.");
        setVisibleManaged(nodes.terminalStatusCard(), true);
        nodes.selectedContent().getChildren().add(nodes.terminalStatusCard());
      }
    }
  }

  private static String displayStatus(String status) {
    return UiComponents.humanizeStatus(status);
  }

  private static String patientDisplayName(Patient patient) {
    String firstName = patient.firstName() == null ? "" : patient.firstName().trim();
    String lastName = patient.lastName() == null ? "" : patient.lastName().trim();
    String name = (firstName + " " + lastName).trim();
    return name.isBlank() ? "Patient" : name;
  }

  private static void updateSummaryStatus(Label summary, AppointmentStatus status) {
    summary.getStyleClass().removeIf(style -> style.startsWith("status-"));
    summary
        .getStyleClass()
        .add("status-" + status.name().toLowerCase(Locale.ROOT).replace('_', '-'));
  }

  private static void setActionVisibility(SelectionNodes nodes, boolean visible) {
    setVisibleManaged(nodes.accept(), visible);
    setVisibleManaged(nodes.decline(), visible);
    setVisibleManaged(nodes.checkIn(), visible);
    setVisibleManaged(nodes.reschedule(), visible);
  }

  private static void setClinicalEditable(SelectionNodes nodes, boolean editable) {
    DoctorPatientFormView.setEditable(nodes.diagnosis(), editable);
    DoctorPatientFormView.setEditable(nodes.consultationNotes(), editable);
    DoctorPatientFormView.setEditable(nodes.followUpNotes(), editable);
    setVisibleManaged(nodes.consultationActions(), editable);
    setVisibleManaged(nodes.prescriptionForm(), editable);
    setVisibleManaged(nodes.prescriptionActions(), editable);
    setVisibleManaged(nodes.completionActions(), editable);
    setVisibleManaged(nodes.saveConsultation(), editable);
    setVisibleManaged(nodes.addPrescription(), editable);
    setVisibleManaged(nodes.complete(), editable);
  }

  private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
    node.setVisible(visible);
    node.setManaged(visible);
  }

  private static void selectDashboardAppointment(
      ClinicServices services,
      Session session,
      CalendarAppointment calendarAppointment,
      SelectionState selection,
      SelectionNodes nodes,
      Label feedback,
      Clock clock,
      ClinicTaskRunner taskRunner) {
    selection.generation++;
    long generation = selection.generation;
    if (calendarAppointment == null) {
      applyDashboardSelection(
          services, session, selection, nodes, feedback, clock, taskRunner, generation, null, null);
      return;
    }
    taskRunner.submit(
        () -> {
          Appointment appointment =
              services.appointmentService().get(calendarAppointment.appointmentId());
          if (appointment.doctorId() != session.accountId()) {
            throw new AuthorizationException("You are not allowed to view this appointment");
          }
          Patient patient =
              services.patientService().getAdministrative(session, appointment.patientId());
          return new DashboardSelection(appointment, patient);
        },
        resolved -> {
          if (generation == selection.generation) {
            applyDashboardSelection(
                services,
                session,
                selection,
                nodes,
                feedback,
                clock,
                taskRunner,
                generation,
                resolved.appointment(),
                resolved.patient());
          }
        },
        failure -> {
          if (generation != selection.generation) {
            return;
          }
          UiComponents.showError(
              feedback, userMessage(failure, "Appointment is temporarily unavailable"));
          applyDashboardSelection(
              services,
              session,
              selection,
              nodes,
              feedback,
              clock,
              taskRunner,
              generation,
              null,
              null);
        });
  }

  private static void applyDashboardSelection(
      ClinicServices services,
      Session session,
      SelectionState selection,
      SelectionNodes nodes,
      Label feedback,
      Clock clock,
      ClinicTaskRunner taskRunner,
      long generation,
      Appointment appointment,
      Patient patient) {
    if (generation != selection.generation) {
      return;
    }
    selection.appointmentId = appointment == null || patient == null ? 0 : appointment.id();
    selection.patientId = patient == null ? 0 : patient.id();
    updateSelectionState(selection, nodes, appointment, patient, clock);
    DoctorConsultationView.loadClinicalAsync(
        services,
        session,
        appointment,
        nodes.diagnosis(),
        nodes.consultationNotes(),
        nodes.followUpNotes(),
        nodes.prescriptions(),
        feedback,
        taskRunner,
        clinicalLoaded -> {
          if (!clinicalLoaded
              && appointment != null
              && appointment.status() == AppointmentStatus.CHECKED_IN) {
            setClinicalEditable(nodes, false);
          }
        });
  }

  private static void requireSelection(long id, String message) {
    if (id == 0) {
      throw new ValidationException(message);
    }
  }

  private static void clear(TextField... fields) {
    for (TextField field : fields) {
      field.clear();
    }
  }

  private static <T> void run(
      Label feedback,
      ClinicTaskRunner taskRunner,
      ClinicTaskRunner.ClinicTask<T> operation,
      java.util.function.Consumer<T> onSuccess) {
    try {
      taskRunner.submit(
          operation,
          onSuccess,
          failure ->
              UiComponents.showError(
                  feedback,
                  failure instanceof ValidationException
                          || failure instanceof AuthorizationException
                      ? failure.getMessage()
                      : "The requested operation is temporarily unavailable"));
    } catch (java.util.concurrent.RejectedExecutionException exception) {
      UiComponents.showError(feedback, "The requested operation is temporarily unavailable");
    }
  }

  private static String userMessage(Throwable exception, String fallback) {
    return exception.getMessage() == null ? fallback : exception.getMessage();
  }

  private record SelectionNodes(
      Label summary,
      Label noSelection,
      VBox selectedContent,
      VBox patientDetailsCard,
      VBox appointmentActionsCard,
      VBox terminalStatusCard,
      Label terminalStatusMessage,
      Label clinicalReadOnly,
      VBox consultationCard,
      VBox prescriptionCard,
      VBox completionCard,
      HBox consultationActions,
      VBox prescriptionForm,
      HBox prescriptionActions,
      HBox completionActions,
      Button accept,
      Button decline,
      Button checkIn,
      Button reschedule,
      Button saveConsultation,
      Button addPrescription,
      Button complete,
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions,
      Button viewPatientHistory) {
    // Record components define the complete selected-pane node bundle.
  }

  private static final class SelectionState {
    private long appointmentId;
    private long patientId;
    private long generation;
  }

  private record DashboardSelection(Appointment appointment, Patient patient) {
    private DashboardSelection {
      Objects.requireNonNull(appointment, "appointment");
      Objects.requireNonNull(patient, "patient");
    }
  }
}
