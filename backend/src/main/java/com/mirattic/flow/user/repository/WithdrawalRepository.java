package com.mirattic.flow.user.repository;

import com.mirattic.flow.user.entity.Withdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, String> {
}
