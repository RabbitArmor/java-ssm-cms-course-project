CREATE TABLE IF NOT EXISTS cms_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT NOT NULL,
    result ENUM('SUCCESS', 'FAILURE') NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_operation_log_target (target_type, target_id, occurred_at),
    KEY idx_operation_log_actor (actor_id, occurred_at),
    KEY idx_operation_log_action (action, occurred_at),
    KEY idx_operation_log_time (occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
