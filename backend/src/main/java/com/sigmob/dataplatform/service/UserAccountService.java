package com.sigmob.dataplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sigmob.dataplatform.auth.AuthModels;
import com.sigmob.dataplatform.entity.UserAccount;
import com.sigmob.dataplatform.mapper.UserAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserAccountMapper userAccountMapper;

    public UserAccountService(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    @Transactional
    public UserAccount createOrUpdate(AuthModels.AuthUser authUser) {
        String userId = authUser.openId();

        UserAccount account = userAccountMapper.selectById(userId);

        if (account == null) {
            log.info("Creating new user account: openId={}, name={}", userId, authUser.name());
            account = new UserAccount();
            account.setId(userId);
            account.setDisplayName(authUser.name());
            account.setEmail(authUser.email());
            account.setAvatarUrl(authUser.avatarUrl());
            userAccountMapper.insert(account);
        } else {
            log.info("Updating existing user account: openId={}, name={}", userId, authUser.name());
            account.setDisplayName(authUser.name());
            account.setEmail(authUser.email());
            account.setAvatarUrl(authUser.avatarUrl());
            userAccountMapper.updateById(account);
        }

        log.debug("User account saved: id={}", account.getId());

        return account;
    }
}