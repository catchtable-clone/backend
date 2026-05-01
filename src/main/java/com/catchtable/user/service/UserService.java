package com.catchtable.user.service;

import com.catchtable.user.dto.read.UserResponseDto;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 현재 로그인한 사용자의 정보 조회 (X-User-Id 헤더 기반)
     */
    @Transactional(readOnly = true)
    public UserResponseDto getMe(Long userId) {
        User user = userRepository.getById(userId);
        return UserResponseDto.from(user);
    }
}
