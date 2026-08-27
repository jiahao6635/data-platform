package com.sigmob.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.OffsetDateTime;

@TableName("user_account")
public class UserAccount {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("display_name")
    private String displayName;

    private String email;

    @TableField("avatar_url")
    private String avatarUrl;

    @EnumValue
    private UserStatus status = UserStatus.ACTIVE;

    @TableField("system_account")
    private Boolean systemAccount = false;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    public enum UserStatus {
        ACTIVE,
        INACTIVE
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Boolean getSystemAccount() {
        return systemAccount;
    }

    public void setSystemAccount(Boolean systemAccount) {
        this.systemAccount = systemAccount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}