package edu.course.lab.cms.platform.audit;

import edu.course.lab.cms.platform.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;

public class AuditFilter extends PageQuery {
    public static final DateTimeFormatter UTC_TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 4, SignStyle.NEVER)
            .appendPattern("-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 6, true)
            .optionalEnd()
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    @Positive
    private Long actorId;

    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String action;

    private LocalDateTime from;

    private LocalDateTime to;

    public static LocalDateTime parseUtcTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("UTC timestamp must not be empty");
        }
        try {
            return LocalDateTime.parse(raw, UTC_TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid UTC local date-time (expected yyyy-MM-dd'T'HH:mm:ss with up to 6 fractional digits)",
                    exception);
        }
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public void setFrom(LocalDateTime from) {
        this.from = from;
    }

    public LocalDateTime getTo() {
        return to;
    }

    public void setTo(LocalDateTime to) {
        this.to = to;
    }
}
