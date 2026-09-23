package nusynapxe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
  private static final int SOURCE_LINE_LIMIT = 500;
  private static final String DOMAIN = "nusynapxe.domain..";
  private static final String PERSISTENCE = "nusynapxe.persistence..";
  private static final String SERVICE = "nusynapxe.service..";
  private static final String UI = "nusynapxe.ui..";
  private static final String TOOLS = "nusynapxe.tools..";
  private static final String APPLICATION_ROUTER = "nusynapxe.ui.ApplicationRouter";

  private static final JavaClasses MAIN_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("nusynapxe");

  private static final ArchRule DOMAIN_BOUNDARY =
      noClasses()
          .that()
          .resideInAnyPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(UI, SERVICE, PERSISTENCE, TOOLS);

  private static final ArchRule PERSISTENCE_BOUNDARY =
      noClasses()
          .that()
          .resideInAnyPackage(PERSISTENCE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(UI, SERVICE, TOOLS);

  private static final ArchRule SERVICE_BOUNDARY =
      noClasses()
          .that()
          .resideInAnyPackage(SERVICE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(UI, TOOLS);

  private static final ArchRule UI_BOUNDARY =
      noClasses()
          .that()
          .resideInAnyPackage(UI)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(TOOLS);

  private static final ArchRule UI_PERSISTENCE_COMPOSITION_ROOT =
      noClasses()
          .that()
          .resideInAnyPackage(UI)
          .and()
          .doNotHaveFullyQualifiedName(APPLICATION_ROUTER)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(PERSISTENCE);

  private static final ArchRule CORE_SLICES_ARE_CYCLE_FREE =
      slices().matching("nusynapxe.(*)..").should().beFreeOfCycles();

  @Test
  void domainRemainsIndependentFromOuterLayers() {
    DOMAIN_BOUNDARY.check(MAIN_CLASSES);
  }

  @Test
  void persistenceRemainsIndependentFromOuterLayers() {
    PERSISTENCE_BOUNDARY.check(MAIN_CLASSES);
  }

  @Test
  void serviceRemainsIndependentFromUiAndTools() {
    SERVICE_BOUNDARY.check(MAIN_CLASSES);
  }

  @Test
  void uiUsesServicesInsteadOfPersistenceExceptAtTheCompositionRoot() {
    UI_BOUNDARY.check(MAIN_CLASSES);
    UI_PERSISTENCE_COMPOSITION_ROOT.check(MAIN_CLASSES);
  }

  @Test
  void corePackagesRemainFreeOfCycles() {
    CORE_SLICES_ARE_CYCLE_FREE.check(MAIN_CLASSES);
  }

  @Test
  void primaryViewsRemainCompositionShells() throws Exception {
    assertLineLimit("src/main/java/nusynapxe/ui/ReceptionistView.java", SOURCE_LINE_LIMIT);
    assertLineLimit("src/main/java/nusynapxe/ui/DoctorView.java", SOURCE_LINE_LIMIT);
    assertLineLimit("src/main/java/nusynapxe/ui/ReceptionistWorkspace.java", SOURCE_LINE_LIMIT);
    assertLineLimit("src/main/java/nusynapxe/ui/DoctorWorkspace.java", SOURCE_LINE_LIMIT);
  }

  @Test
  void uiSourceFilesRemainWithinTheFiveHundredLineLimit() throws Exception {
    List<String> oversized = new ArrayList<>();
    try (Stream<Path> files = Files.walk(Path.of("src/main/java/nusynapxe/ui"))) {
      files
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  long lineCount = Files.readAllLines(path).size();
                  if (lineCount > SOURCE_LINE_LIMIT) {
                    oversized.add(path + " (" + lineCount + " lines)");
                  }
                } catch (IOException exception) {
                  throw new IllegalStateException("Could not inspect " + path, exception);
                }
              });
    }
    assertTrue(
        oversized.isEmpty(),
        () -> "UI source files exceed the 500-line limit: " + String.join(", ", oversized));
  }

  @Test
  void blockingAuthenticationAndAdminEntryPointsUseTheClinicTaskRunner() throws Exception {
    for (String file :
        new String[] {
          "src/main/java/nusynapxe/ui/ApplicationRouter.java",
          "src/main/java/nusynapxe/ui/LoginView.java",
          "src/main/java/nusynapxe/ui/SetupView.java",
          "src/main/java/nusynapxe/ui/SystemAdminView.java"
        }) {
      String source = Files.readString(Path.of(file));
      assertTrue(source.contains("taskRunner.submit"), () -> file + " must submit blocking work");
    }
  }

  @Test
  void appointmentTableCellFactoriesRenderPreloadedRowsOnly() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/nusynapxe/ui/ReceptionistAppointmentView.java"));
    assertTrue(source.contains("appointmentTable"));
    for (String forbiddenCall :
        new String[] {"getAdministrative(", "listDoctors(", "receiptHistory("}) {
      assertFalse(
          source.contains(forbiddenCall),
          () -> "Appointment table must not perform " + forbiddenCall + " from a cell factory");
    }
  }

  private static void assertLineLimit(String file, int maximumLines) throws Exception {
    long lineCount = Files.readAllLines(Path.of(file)).size();
    assertTrue(
        lineCount <= maximumLines,
        () -> file + " has " + lineCount + " lines; expected at most " + maximumLines);
  }
}
