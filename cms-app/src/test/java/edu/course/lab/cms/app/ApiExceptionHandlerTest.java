package edu.course.lab.cms.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.course.lab.cms.platform.api.ApiExceptionHandler;
import edu.course.lab.cms.platform.api.ApiResponse;
import edu.course.lab.cms.platform.api.PageQuery;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(new ParameterMessageInterpolator());
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void malformedJsonReturnsBadRequestWithoutInternalDetails() throws Exception {
        var response = mvc.perform(post("/api/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad-json"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        var raw = response.getContentAsString(StandardCharsets.UTF_8);
        var body = json.readTree(response.getContentAsByteArray());
        assertEquals("INVALID_REQUEST_BODY", body.path("code").asText());
        assertTrue(body.path("data").isNull());
        assertFalse(raw.contains("JsonParseException"));
    }

    @Test
    void pathVariableTypeMismatchReturnsValidationFailed() throws Exception {
        var response = mvc.perform(get("/api/probe/items/not-a-number"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        var body = json.readTree(response.getContentAsByteArray());
        assertEquals("VALIDATION_FAILED", body.path("code").asText());
        assertEquals("id", body.path("data").get(0).path("field").asText());
    }

    @Test
    void modelAttributeBindFailureWithoutValidReturnsValidationFailed() throws Exception {
        var response = mvc.perform(get("/api/probe/query").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        var body = json.readTree(response.getContentAsByteArray());
        assertEquals("VALIDATION_FAILED", body.path("code").asText());
        assertEquals("page", body.path("data").get(0).path("field").asText());
    }

    @Test
    void methodParameterConstraintAndTypeMismatchReturnSameFieldName() throws Exception {
        var constraintResp = mvc.perform(get("/api/probe/param").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        var constraintBody = json.readTree(constraintResp.getContentAsByteArray());
        assertEquals("VALIDATION_FAILED", constraintBody.path("code").asText());
        assertEquals("limit", constraintBody.path("data").get(0).path("field").asText());

        var mismatchResp = mvc.perform(get("/api/probe/param").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        var mismatchBody = json.readTree(mismatchResp.getContentAsByteArray());
        assertEquals("VALIDATION_FAILED", mismatchBody.path("code").asText());
        assertEquals("limit", mismatchBody.path("data").get(0).path("field").asText());
    }

    @Test
    void explicitRequestParamAndPathVariableNamesTakePrecedenceOverJavaParameterName() throws Exception {
        var renamedParamResp = mvc.perform(get("/api/probe/renamed-param").param("max_items", "0"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        var renamedParamBody = json.readTree(renamedParamResp.getContentAsByteArray());
        assertEquals("VALIDATION_FAILED", renamedParamBody.path("code").asText());
        assertEquals("max_items", renamedParamBody.path("data").get(0).path("field").asText());

        var renamedPathResp = mvc.perform(get("/api/probe/renamed-path/0"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        var renamedPathBody = json.readTree(renamedPathResp.getContentAsByteArray());
        assertEquals("VALIDATION_FAILED", renamedPathBody.path("code").asText());
        assertEquals("item_id", renamedPathBody.path("data").get(0).path("field").asText());
    }

    @Test
    void unexpectedExceptionHidesSensitiveDetails() throws Exception {
        var response = mvc.perform(get("/api/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse();
        var raw = response.getContentAsString(StandardCharsets.UTF_8);
        var body = json.readTree(response.getContentAsByteArray());
        assertEquals("INTERNAL_ERROR", body.path("code").asText());
        assertFalse(raw.contains("secret-db-password"));
    }

    record ProbeBody(String title) {
    }

    @RestController
    @RequestMapping("/api/probe")
    static class ProbeController {
        @PostMapping("/body")
        ApiResponse<String> body(@RequestBody ProbeBody body) {
            return ApiResponse.success(body.title());
        }

        @GetMapping("/items/{id}")
        ApiResponse<Long> item(@PathVariable("id") Long id) {
            return ApiResponse.success(id);
        }

        @GetMapping("/query")
        ApiResponse<Long> query(@ModelAttribute PageQuery query) {
            return ApiResponse.success(query.getPage());
        }

        @GetMapping("/param")
        ApiResponse<Integer> param(@RequestParam("limit") @Min(1) int limit) {
            return ApiResponse.success(limit);
        }

        @GetMapping("/renamed-param")
        ApiResponse<Integer> renamedParam(@RequestParam(name = "max_items") @Min(1) int internalLimit) {
            return ApiResponse.success(internalLimit);
        }

        @GetMapping("/renamed-path/{item_id}")
        ApiResponse<Long> renamedPath(@PathVariable("item_id") @Min(1) long internalId) {
            return ApiResponse.success(internalId);
        }

        @GetMapping("/boom")
        ApiResponse<String> boom() {
            throw new IllegalStateException("secret-db-password=123456");
        }
    }
}
