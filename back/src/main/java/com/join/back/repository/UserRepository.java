package com.join.back.repository;

import com.join.back.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMaxId(Long maxId);

    Optional<User> findByTelegramId(Long telegramId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
