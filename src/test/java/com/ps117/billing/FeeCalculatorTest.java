package com.ps117.billing;

import com.ps117.domain.Application;
import com.ps117.enums.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FeeCalculatorTest {
    @Test
    void calculatesNormalFeeAndSeniorDiscount() {
        Application app = new Application();
        app.setApplicationId("A1");
        app.setServiceType(ServiceType.NORMAL);
        app.setCategory(Category.SENIOR_CITIZEN);
        app.setBookletSize(BookletSize.PAGES_36);
        app.setDateOfBirth(LocalDate.of(1960, 1, 1));

        FeeCalculator calculator = new FeeCalculator();
        BigDecimal amount = calculator.calculate(app);

        assertEquals(new BigDecimal("1350.00"), amount);
    }

    @Test
    void addsTatkalSurcharge() {
        Application app = new Application();
        app.setApplicationId("A2");
        app.setServiceType(ServiceType.TATKAL);
        app.setCategory(Category.NORMAL);
        app.setBookletSize(BookletSize.PAGES_36);

        FeeCalculator calculator = new FeeCalculator();
        assertEquals(new BigDecimal("3500.00"), calculator.calculate(app));
    }
}
