CREATE TABLE notification_event (
                                    event_id UUID PRIMARY KEY,
                                    event_type VARCHAR(50) NOT NULL,
                                    source_service VARCHAR(50) NOT NULL,
                                    reference_type VARCHAR(30) NOT NULL,
                                    reference_id UUID NOT NULL,
                                    status VARCHAR(20) NOT NULL,
                                    retry_count INTEGER NOT NULL DEFAULT 0,
                                    error_message VARCHAR(1000),
                                    received_at TIMESTAMPTZ NOT NULL,
                                    processed_at TIMESTAMPTZ
);

CREATE TABLE notification (
                              id UUID PRIMARY KEY,
                              event_id UUID NOT NULL,
                              user_id UUID NOT NULL,
                              type VARCHAR(50) NOT NULL,
                              title VARCHAR(200) NOT NULL,
                              content VARCHAR(500) NOT NULL,
                              reference_type VARCHAR(30) NOT NULL,
                              reference_id UUID NOT NULL,
                              is_read BOOLEAN NOT NULL DEFAULT FALSE,
                              created_at TIMESTAMPTZ NOT NULL,
                              read_at TIMESTAMPTZ,
                              CONSTRAINT fk_notification_event
                                  FOREIGN KEY (event_id) REFERENCES notification_event(event_id),
                              CONSTRAINT uk_notification_event_user
                                  UNIQUE (event_id, user_id)
);



CREATE TABLE p_notification_slack (
                                      slack_delivery_id UUID PRIMARY KEY,
                                      event_id UUID NOT NULL,
                                      destination VARCHAR(200) NOT NULL,
                                      slack_message TEXT NOT NULL,
                                      status VARCHAR(20) NOT NULL,
                                      attempt_count INTEGER NOT NULL DEFAULT 0,
                                      retry_count INTEGER NOT NULL DEFAULT 0,
                                      error_code VARCHAR(100),
                                      error_message VARCHAR(1000),
                                      requested_at TIMESTAMPTZ NOT NULL,
                                      next_retry_at TIMESTAMPTZ,
                                      sent_at TIMESTAMPTZ,
                                      updated_at TIMESTAMPTZ NOT NULL,
                                      CONSTRAINT fk_notification_slack_event
                                          FOREIGN KEY (event_id) REFERENCES notification_event(event_id),
                                      CONSTRAINT uk_slack_event_destination
                                          UNIQUE (event_id, destination),
                                      CONSTRAINT ck_slack_attempt_count
                                          CHECK (attempt_count >= 0 AND attempt_count <= 4),
                                      CONSTRAINT ck_slack_retry_count
                                          CHECK (retry_count >= 0 AND retry_count <= 3)
);

CREATE INDEX idx_notification_user_created
    ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread
    ON notification (user_id, is_read, created_at DESC);

CREATE INDEX idx_notification_slack_ready
    ON p_notification_slack (status, next_retry_at, requested_at);