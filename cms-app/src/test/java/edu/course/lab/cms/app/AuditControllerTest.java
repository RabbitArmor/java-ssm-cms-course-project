package edu.course.lab.cms.app;

import edu.course.lab.cms.platform.api.ApiExceptionHandler;
import edu.course.lab.cms.platform.audit.AuditController;
import edu.course.lab.cms.platform.audit.AuditService;
import edu.course.lab.cms.platform.session.CurrentUser;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import java.util.Optional;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditControllerTest {
    @Test
    void missingIdentityModuleRequiresLogin() throws Exception {
        var users = new StaticListableBeanFactory();
        try (var validator = validator()) {
            mvc(users, validator).perform(get("/api/audit/logs")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void loggedInReaderCannotReadAudit() throws Exception {
        try (var validator = validator()) {
            mvc(usersWith(Set.of()), validator).perform(get("/api/audit/logs")).andExpect(status().isForbidden());
        }
    }

    @Test
    void rejectsInvalidPageAndTimeRange() throws Exception {
        try (var validator = validator()) {
            var mvc = mvc(usersWith(Set.of("audit.read")), validator);
            mvc.perform(get("/api/audit/logs").param("size", "101")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/audit/logs").param("from", "2026-11-26T10:00:00")
                    .param("to", "2026-11-26T09:00:00")).andExpect(status().isBadRequest());
        }
    }

    private static StaticListableBeanFactory usersWith(Set<String> permissions) {
        var users = new StaticListableBeanFactory();
        users.addBean("currentUserProvider", (CurrentUserProvider) () ->
                Optional.of(new CurrentUser(1, 1L, permissions)));
        return users;
    }

    private static LocalValidatorFactoryBean validator() {
        var validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(new ParameterMessageInterpolator());
        validator.afterPropertiesSet();
        return validator;
    }

    private static MockMvc mvc(StaticListableBeanFactory users, LocalValidatorFactoryBean validator) {
        return MockMvcBuilders.standaloneSetup(
                        new AuditController(new AuditService(null), users.getBeanProvider(CurrentUserProvider.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }
}
