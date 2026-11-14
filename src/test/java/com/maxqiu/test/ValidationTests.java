package com.maxqiu.test;

import com.maxqiu.test.service.ValidationService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ValidationTests {

    private static final String VALID_BODY = """
            {"name":"Native Demo","email":"demo@example.com","age":28,
             "address":{"city":"Shanghai","zipCode":"200000"},
             "tags":["native"],"code":"DEMO-ABC"}
            """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ValidationService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Test MVC and Boot's actual validation configuration; authentication is
        // exercised by test-validation.ps1 against the running application.
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void validBodyIsEchoedWithoutWritingToTheDatabase() throws Exception {
        mvc.perform(post("/validation/body").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Native Demo"))
                .andExpect(jsonPath("$.address.zipCode").value("200000"))
                .andExpect(jsonPath("$.tags[0]").value("native"));
    }

    static Stream<String[]> invalidBodies() {
        return Stream.of(
                new String[]{VALID_BODY.replace("Native Demo", " "), "name"},
                new String[]{VALID_BODY.replace("Native Demo", "x".repeat(41)), "name"},
                new String[]{VALID_BODY.replace("demo@example.com", "invalid"), "email"},
                new String[]{VALID_BODY.replace("\"age\":28", "\"age\":null"), "age"},
                new String[]{VALID_BODY.replace("\"age\":28", "\"age\":17"), "age"},
                new String[]{VALID_BODY.replace("\"age\":28", "\"age\":121"), "age"},
                new String[]{VALID_BODY.replace("{\"city\":\"Shanghai\",\"zipCode\":\"200000\"}", "null"), "address"},
                new String[]{VALID_BODY.replace("Shanghai", " "), "address.city"},
                new String[]{VALID_BODY.replace("200000", "invalid"), "address.zipCode"},
                new String[]{VALID_BODY.replace("[\"native\"]", "[]"), "tags"},
                new String[]{VALID_BODY.replace("[\"native\"]", "[\"\"]"), "tags[0]"},
                new String[]{VALID_BODY.replace("DEMO-ABC", "INVALID"), "code"});
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidBodyReportsTheField(String body, String field) throws Exception {
        mvc.perform(post("/validation/body").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[*].field", hasItem(field)))
                .andExpect(jsonPath("$.violations[0].message").isNotEmpty());
    }

    @Test
    void updateGroupRequiresIdAndAlsoValidatesDefaultConstraints() throws Exception {
        mvc.perform(put("/validation/body").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItem("id")));
        String update = VALID_BODY.replace("\"name\":", "\"id\":1,\"name\":");
        mvc.perform(put("/validation/body").contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        mvc.perform(put("/validation/body").contentType(MediaType.APPLICATION_JSON)
                        .content(update.replace("Native Demo", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItem("name")));
    }

    @ParameterizedTest
    @CsvSource({"1,200", "10,200", "0,400", "11,400"})
    void mvcMethodValidationChecksQueryParameterBoundaries(int count, int expectedStatus) throws Exception {
        var result = mvc.perform(get("/validation/parameters").param("count", String.valueOf(count)))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 400) {
            result.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[*].field", hasItem("count")));
        } else {
            result.andExpect(jsonPath("$.count").value(count));
        }
    }

    @Test
    void serviceProxyValidatesEvenWhenCalledOutsideMvc() {
        assertThatThrownBy(() -> service.echo("x")).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void serviceValidationIsMappedToHttp400() throws Exception {
        mvc.perform(get("/validation/service").param("value", "native"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("native"));
        for (String value : new String[]{"", "x", " ".repeat(3), "x".repeat(21)}) {
            mvc.perform(get("/validation/service").param("value", value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[*].field", hasItem("echo.value")));
        }
    }
}
