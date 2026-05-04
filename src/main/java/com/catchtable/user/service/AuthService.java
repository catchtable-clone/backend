package com.catchtable.user.service;

import com.catchtable.bookmark.entity.BookmarkFolder;
import com.catchtable.bookmark.entity.FolderType;
import com.catchtable.bookmark.repository.BookmarkFolderRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.global.security.JwtTokenProvider;
import com.catchtable.user.dto.auth.TokenResponse;
import com.catchtable.user.entity.RefreshToken;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import com.catchtable.user.entity.UserStatus;
import com.catchtable.user.repository.RefreshTokenRepository;
import com.catchtable.user.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BookmarkFolderRepository bookmarkFolderRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Transactional
    public TokenResponse googleLogin(String idTokenString) {
        GoogleIdToken.Payload payload = verifyGoogleToken(idTokenString);

        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> createUser(googleId, email, name, picture));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.USER_WITHDRAWN);
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenStr) {
        if (!jwtTokenProvider.validateToken(refreshTokenStr)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken savedToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        User user = userRepository.getById(savedToken.getUserId());

        refreshTokenRepository.delete(savedToken);

        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.deleteByToken(refreshTokenStr);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.save(RefreshToken.of(user.getId(), refreshToken));

        return new TokenResponse(accessToken, refreshToken, user.getId(), user.getNickname(), user.getProfileImage());
    }

    private User createUser(String googleId, String email, String name, String picture) {
        String nickname = generateUniqueNickname(name);
        User newUser = User.builder()
                .googleId(googleId)
                .email(email)
                .nickname(nickname)
                .profileImage(picture)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        User saved = userRepository.save(newUser);

        bookmarkFolderRepository.save(BookmarkFolder.builder()
                .user(saved)
                .folderName("기본 폴더")
                .folderType(FolderType.DEFAULT)
                .color("#F97316")
                .build());

        return saved;
    }

    private String generateUniqueNickname(String baseName) {
        String nickname = baseName != null ? baseName : "user";
        if (!userRepository.existsByNickname(nickname)) {
            return nickname;
        }
        return nickname + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new CustomException(ErrorCode.INVALID_GOOGLE_TOKEN);
            }
            return idToken.getPayload();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
    }
}
