package com.sigmob.dataplatform.repository;

import com.sigmob.dataplatform.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    Optional<UserAccount> findById(String id);

    boolean existsById(String id);
}
