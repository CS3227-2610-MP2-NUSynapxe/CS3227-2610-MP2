package nusynapxe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import javafx.application.Application;
import org.junit.jupiter.api.Test;

final class NUSynapxeLauncherTest {
  @Test
  void executableJarLauncherIsNotAJavaFxApplication() {
    assertFalse(Application.class.isAssignableFrom(NUSynapxeLauncher.class));
  }

  @Test
  void executableJarLauncherExposesAStaticJavaMainMethod() throws NoSuchMethodException {
    int modifiers = NUSynapxeLauncher.class.getMethod("main", String[].class).getModifiers();

    assertTrue(Modifier.isPublic(modifiers));
    assertTrue(Modifier.isStatic(modifiers));
  }
}
