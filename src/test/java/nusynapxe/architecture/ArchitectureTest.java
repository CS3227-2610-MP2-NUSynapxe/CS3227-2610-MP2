package nusynapxe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
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
    assertLineLimit("src/main/java/nusynapxe/ui/ReceptionistView.java", 500);
    assertLineLimit("src/main/java/nusynapxe/ui/DoctorView.java", 450);
  }

  private static void assertLineLimit(String file, int maximumLines) throws Exception {
    long lineCount = Files.lines(Path.of(file)).count();
    org.junit.jupiter.api.Assertions.assertTrue(
        lineCount <= maximumLines,
        () -> file + " has " + lineCount + " lines; expected at most " + maximumLines);
  }
}
