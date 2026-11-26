package edu.course.lab.cms.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.course.lab.cms.platform.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void configureMvc() {
        var health = new HealthController(() -> {
            throw new DataAccessResourceFailureException("private connection details");
        });
        mvc = MockMvcBuilders.standaloneSetup(health)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void livenessWorksWithoutDatabase() throws Exception {
        var response = mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        var body = json.readTree(response.getContentAsByteArray());
        assertEquals("OK", body.path("code").asText());
        assertEquals("UP", body.path("data").path("status").asText());
    }

    @Test
    void readinessHidesDatabaseDetails() throws Exception {
        var response = mvc.perform(get("/api/health/db"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse();
        var raw = response.getContentAsString();
        var body = json.readTree(response.getContentAsByteArray());
        assertEquals("DB_UNAVAILABLE", body.path("code").asText());
        assertEquals("数据库暂不可用", body.path("message").asText());
        assertEquals(true, body.path("data").isNull());
        assertEquals(false, raw.contains("private connection details"));
    }
}
