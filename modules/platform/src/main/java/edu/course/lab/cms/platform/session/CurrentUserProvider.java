package edu.course.lab.cms.platform.session;

import java.util.Optional;

/** 由身份模块从当前请求的服务端会话解析。 */
public interface CurrentUserProvider {
    Optional<CurrentUser> currentUser();
}
