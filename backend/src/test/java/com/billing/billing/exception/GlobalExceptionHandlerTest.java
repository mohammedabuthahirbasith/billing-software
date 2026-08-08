package com.billing.billing.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

// Pins the two properties the whole error contract rests on: a deliberate business failure keeps its
// own message (the frontend renders it verbatim), and an unexpected one does NOT — its internal text
// stays server-side. A standalone MockMvc setup keeps this a pure handler test with no database.
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessFailureKeepsItsStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Insufficient stock for SKU ABC"))
                .andExpect(jsonPath("$.path").value("/test/conflict"));
    }

    @Test
    void unexpectedFailureIsGenericisedInsteadOfLeakingInternals() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value(not(containsString("column \"sku\""))));
    }

    @Test
    void validationFailureReportsTheOffendingFields() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.sku").exists());
    }

    @Test
    void malformedBodyIsRejectedWithoutEchoingThePayload() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        @GetMapping("/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for SKU ABC");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("null value in column \"sku\" violates not-null constraint");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody Payload payload) {
            // body intentionally empty — the test only exercises the validation failure path
        }

        record Payload(@NotBlank String sku) {}
    }
}
