package edu.course.lab.cms.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.course.lab.cms.platform.api.ApiExceptionHandler;
import edu.course.lab.cms.platform.api.PageResult;
import edu.course.lab.cms.platform.audit.AuditConstants;
import edu.course.lab.cms.platform.audit.AuditController;
import edu.course.lab.cms.platform.audit.AuditEntry;
import edu.course.lab.cms.platform.audit.AuditFilter;
import edu.course.lab.cms.platform.audit.AuditService;
import edu.course.lab.cms.platform.session.CurrentUser;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditControllerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void missingIdentityModuleRequiresLogin() throws Exception {
        var users = new StaticListableBeanFactory();
        try (var validator = validator()) {
            var response = mvc(users, validator, stubService()).perform(get("/api/audit/logs"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse();
            assertEquals("UNAUTHENTICATED", json.readTree(response.getContentAsByteArray()).path("code").asText());
        }
    }

    @Test
    void anonymousSessionRequiresLogin() throws Exception {
        var users = new StaticListableBeanFactory();
        users.addBean("currentUserProvider", (CurrentUserProvider) Optional::empty);
        try (var validator = validator()) {
            var response = mvc(users, validator, stubService()).perform(get("/api/audit/logs"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse();
            assertEquals("UNAUTHENTICATED", json.readTree(response.getContentAsByteArray()).path("code").asText());
        }
    }

    @Test
    void loggedInReaderCannotReadAudit() throws Exception {
        try (var validator = validator()) {
            var response = mvc(usersWith(Set.of()), validator, stubService()).perform(get("/api/audit/logs"))
                    .andExpect(status().isForbidden())
                    .andReturn().getResponse();
            assertEquals("FORBIDDEN", json.readTree(response.getContentAsByteArray()).path("code").asText());
        }
    }

    @Test
    void authorizedUserReceivesPagedAuditLogsWithExactParsedUtcBoundaries() throws Exception {
        var expected = new AuditEntry(10L, 2L, AuditConstants.ACTION_PUBLISH,
                AuditConstants.TARGET_ARTICLE, 3L, "SUCCESS", LocalDateTime.of(2026, 11, 26, 9, 0, 0));
        var captured = new java.util.concurrent.atomic.AtomicReference<AuditFilter>();
        var service = new AuditService(null) {
            @Override
            public PageResult<AuditEntry> list(AuditFilter filter) {
                captured.set(filter);
                return new PageResult<>(List.of(expected), filter.getPage(), filter.getSize(), 1L);
            }
        };
        try (var validator = validator()) {
            var mvc = mvc(usersWith(Set.of("audit.read")), validator, service);

            // 无小数秒
            var response = mvc.perform(get("/api/audit/logs")
                            .param("page", "1")
                            .param("size", "20")
                            .param("actorId", "2")
                            .param("action", AuditConstants.ACTION_PUBLISH)
                            .param("from", "2026-11-26T00:00:00")
                            .param("to", "2026-11-27T00:00:00"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse();
            var body = json.readTree(response.getContentAsByteArray());
            assertEquals("OK", body.path("code").asText());
            assertEquals(1L, body.path("data").path("total").asLong());
            assertEquals("2026-11-26T09:00:00", body.path("data").path("items").get(0).path("occurredAt").asText());
            assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0, 0), captured.get().getFrom());
            assertEquals(LocalDateTime.of(2026, 11, 27, 0, 0, 0), captured.get().getTo());

            // 合法小数秒（1 位与 6 位微秒精度）
            mvc.perform(get("/api/audit/logs")
                            .param("from", "2026-11-26T10:00:00.1")
                            .param("to", "2026-11-26T10:00:00.123456"))
                    .andExpect(status().isOk());
            assertEquals(LocalDateTime.of(2026, 11, 26, 10, 0, 0, 100_000_000), captured.get().getFrom());
            assertEquals(LocalDateTime.of(2026, 11, 26, 10, 0, 0, 123_456_000), captured.get().getTo());
        }
    }

    @Test
    void rejectsInvalidPageFiltersAndTimeRange() throws Exception {
        var listCalls = new java.util.concurrent.atomic.AtomicInteger();
        var service = new AuditService(null) {
            @Override
            public PageResult<AuditEntry> list(AuditFilter filter) {
                listCalls.incrementAndGet();
                return super.list(filter);
            }
        };
        try (var validator = validator()) {
            var mvc = mvc(usersWith(Set.of("audit.read")), validator, service);

            var badSize = mvc.perform(get("/api/audit/logs").param("size", "101"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(badSize.getContentAsByteArray()).path("code").asText());

            var badPage = mvc.perform(get("/api/audit/logs").param("page", "0"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(badPage.getContentAsByteArray()).path("code").asText());

            var badActor = mvc.perform(get("/api/audit/logs").param("actorId", "0"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(badActor.getContentAsByteArray()).path("code").asText());

            var badAction = mvc.perform(get("/api/audit/logs").param("action", "publish"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(badAction.getContentAsByteArray()).path("code").asText());

            for (String invalidTimestamp : List.of(
                    "+10000-01-01T00:00:00",
                    "-0001-01-01T00:00:00",
                    "+2026-01-01T00:00:00",
                    "2026-11-26T10:00:00.100000.1",
                    "2026-11-26T10:00:00.1234567",
                    "2026-11-26T10:00:00.",
                    "2026-11-26T10:00:00Z",
                    "2026-11-26T08:00:00+08:00")) {
                var badFrom = mvc.perform(get("/api/audit/logs").param("from", invalidTimestamp))
                        .andExpect(status().isBadRequest()).andReturn().getResponse();
                var fromBody = json.readTree(badFrom.getContentAsByteArray());
                assertEquals("VALIDATION_FAILED", fromBody.path("code").asText());
                assertEquals("from", fromBody.path("data").get(0).path("field").asText());

                var badTo = mvc.perform(get("/api/audit/logs").param("to", invalidTimestamp))
                        .andExpect(status().isBadRequest()).andReturn().getResponse();
                var toBody = json.readTree(badTo.getContentAsByteArray());
                assertEquals("VALIDATION_FAILED", toBody.path("code").asText());
                assertEquals("to", toBody.path("data").get(0).path("field").asText());
            }

            var reversedRange = mvc.perform(get("/api/audit/logs")
                            .param("from", "2026-11-26T10:00:00")
                            .param("to", "2026-11-26T09:00:00"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("INVALID_TIME_RANGE", json.readTree(reversedRange.getContentAsByteArray()).path("code").asText());

            var equalRange = mvc.perform(get("/api/audit/logs")
                            .param("from", "2026-11-26T10:00:00")
                            .param("to", "2026-11-26T10:00:00"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("INVALID_TIME_RANGE", json.readTree(equalRange.getContentAsByteArray()).path("code").asText());

            assertEquals(0, listCalls.get());
        }
    }

    private static AuditService stubService() {
        return new AuditService(null);
    }

    private static StaticListableBeanFactory usersWith(Set<String> permissions) {
        var users = new StaticListableBeanFactory();
        users.addBean("currentUserProvider", (CurrentUserProvider) () ->
                Optional.of(new CurrentUser(1L, 1L, permissions)));
        return users;
    }

    private static LocalValidatorFactoryBean validator() {
        var validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(new ParameterMessageInterpolator());
        validator.afterPropertiesSet();
        return validator;
    }

    private static MockMvc mvc(StaticListableBeanFactory users, LocalValidatorFactoryBean validator,
                               AuditService service) {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return MockMvcBuilders.standaloneSetup(
                        new AuditController(service, users.getBeanProvider(CurrentUserProvider.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setValidator(validator)
                .build();
    }
}
