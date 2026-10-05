-- Secure invitation lifecycle metadata. Existing PENDING rows were created before
-- actionable invitation URLs existed, so revoke them rather than allowing legacy
-- token-only credentials to become valid after this deployment.
ALTER TABLE platform_user_invitations
    ADD COLUMN IF NOT EXISTS email_delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS sms_delivery_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED',
    ADD COLUMN IF NOT EXISTS last_delivery_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivery_error VARCHAR(512),
    ADD COLUMN IF NOT EXISTS resend_count INTEGER NOT NULL DEFAULT 0;

UPDATE platform_user_invitations
SET status = 'REVOKED'
WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_user_invitations_email_status
    ON platform_user_invitations(email, status);

-- Prevent two active bearer credentials from being issued to the same email,
-- including under concurrent administrator requests.
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_invitations_one_pending_email
    ON platform_user_invitations(email)
    WHERE status = 'PENDING';
