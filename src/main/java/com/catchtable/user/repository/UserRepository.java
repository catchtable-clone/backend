package com.catchtable.user.repository;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGoogleId(String googleId);

    boolean existsByNickname(String nickname);

    default User getById(Long id) {
        return findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    default User getAdminOrThrow(Long userId, ErrorCode forbiddenCode) {
        User user = getById(userId);
        if (user.getRole() != UserRole.ADMIN) {
            throw new CustomException(forbiddenCode);
        }
        return user;
    }
}
