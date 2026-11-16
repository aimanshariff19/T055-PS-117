package com.ps117;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {
    @Test
    void invalidArgumentsReturnAnErrorCode() throws Exception {
        assertEquals(2, Main.run(new String[]{"--unknown"}));
    }

    @Test
    void helpReturnsSuccessWithoutProcessingInputs() throws Exception {
        assertEquals(0, Main.run(new String[]{"--help"}));
    }
}
