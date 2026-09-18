package nusynapxe.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RevenueReportTest {
  @Test
  void preservesTheLargestSupportedMinorUnitAmount() {
    RevenueReport report = new RevenueReport(List.of(receipt(1, Long.MAX_VALUE)));

    assertEquals(Long.MAX_VALUE, report.totalMinor());
    assertEquals(Long.MAX_VALUE, report.byMethod().get(PaymentMethod.CARD));
    assertEquals(Long.MAX_VALUE, report.byDoctor().get("Dr. Ada"));
  }

  @Test
  void rejectsTotalsThatOverflowTheSupportedRange() {
    RevenueReport report = new RevenueReport(List.of(receipt(1, Long.MAX_VALUE), receipt(2, 1)));

    assertThrows(ArithmeticException.class, report::totalMinor);
    assertThrows(ArithmeticException.class, report::byMethod);
    assertThrows(ArithmeticException.class, report::byDoctor);
  }

  private static Receipt receipt(long id, long amountMinor) {
    return new Receipt(
        id,
        id,
        id,
        id,
        "Pat Lee",
        "Dr. Ada",
        amountMinor,
        PaymentMethod.CARD,
        LocalDate.of(2026, 9, 1),
        id,
        LocalDateTime.of(2026, 9, 1, 12, 30));
  }
}
