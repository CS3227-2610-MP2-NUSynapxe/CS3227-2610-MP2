package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SelectorLoadGenerationTest {
  @Test
  void keepsFreshnessIndependentForDifferentSelectors() {
    SelectorLoadGeneration generations = new SelectorLoadGeneration();
    Object booking = new Object();
    Object checkout = new Object();

    long staleBookingGeneration = generations.next(booking);
    long checkoutGeneration = generations.next(checkout);
    long currentBookingGeneration = generations.next(booking);

    assertFalse(generations.isCurrent(booking, staleBookingGeneration));
    assertTrue(generations.isCurrent(booking, currentBookingGeneration));
    assertTrue(generations.isCurrent(checkout, checkoutGeneration));
  }

  @Test
  void invalidatesAnOlderLoadForTheSameSelector() {
    SelectorLoadGeneration generations = new SelectorLoadGeneration();
    Object selector = new Object();

    long first = generations.next(selector);
    long second = generations.next(selector);

    assertFalse(generations.isCurrent(selector, first));
    assertTrue(generations.isCurrent(selector, second));
  }
}
