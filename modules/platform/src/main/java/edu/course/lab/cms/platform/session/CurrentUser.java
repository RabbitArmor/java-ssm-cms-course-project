package edu.course.lab.cms.platform.session;

import java.util.Set;

public record CurrentUser(long userId, Long labTypeId, Set<String> permissions) {
    public CurrentUser {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
