-- V3__create_user_account_table.sql
-- Create user account table for storing user information from Feishu

CREATE TABLE user_account (
    id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    avatar_url VARCHAR(1024),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    system_account BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_account_email ON user_account(email) WHERE email IS NOT NULL;
CREATE INDEX idx_user_account_status ON user_account(status);

COMMENT ON TABLE user_account IS '用户账户表';
COMMENT ON COLUMN user_account.id IS '飞书 open_id';
COMMENT ON COLUMN user_account.display_name IS '显示名称';
COMMENT ON COLUMN user_account.email IS '用户邮箱（从飞书获取）';
COMMENT ON COLUMN user_account.avatar_url IS '头像 URL';
COMMENT ON COLUMN user_account.status IS '账户状态：ACTIVE, INACTIVE';
COMMENT ON COLUMN user_account.system_account IS '是否为系统账户';
COMMENT ON COLUMN user_account.created_at IS '创建时间';
COMMENT ON COLUMN user_account.updated_at IS '最后更新时间';
