package edu.course.lab.cms.platform.audit;

public final class AuditConstants {
    public static final String ACTION_ASSIGN_ROLE = "ASSIGN_ROLE";
    public static final String ACTION_ENABLE_CATEGORY = "ENABLE_CATEGORY";
    public static final String ACTION_DISABLE_CATEGORY = "DISABLE_CATEGORY";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_RETURN = "RETURN";
    public static final String ACTION_PUBLISH = "PUBLISH";
    public static final String ACTION_OFFLINE = "OFFLINE";

    public static final String TARGET_USER = "USER";
    public static final String TARGET_CATEGORY = "CATEGORY";
    public static final String TARGET_ARTICLE = "ARTICLE";

    private AuditConstants() {
    }
}
