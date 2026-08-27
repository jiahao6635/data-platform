package com.sigmob.dataplatform.service;

import com.sigmob.dataplatform.auth.AuthModels;
import com.sigmob.dataplatform.entity.UserAccount;
import com.sigmob.dataplatform.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserAccountRepository userAccountRepository;

    public UserAccountService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UserAccount createOrUpdate(AuthModels.AuthUser authUser) {
        String userId = authUser.openId();

        UserAccount account = userAccountRepository.findById(userId)
                .orElse(null);

        if (account == null) {
            log.info("Creating new user account: openId={}, name={}", userId, authUser.name());
            account = new UserAccount();
            account.setId(userId);
            account.setDisplayName(authUser.name());
            account.setEmail(authUser.email());
            account.setAvatarUrl(authUser.avatarUrl());
        } else {
            log.info("Updating existing user account: openId={}, name={}", userId, authUser.name());
            account.setDisplayName(authUser.name());
            account.setEmail(authUser.email());
            account.setAvatarUrl(authUser.avatarUrl());
        }

        UserAccount saved = userAccountRepository.save(account);
        log.debug("User account saved: id={}", saved.getId());

        return saved;
    }
}
