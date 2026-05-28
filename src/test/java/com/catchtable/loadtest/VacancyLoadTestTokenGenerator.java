package com.catchtable.loadtest;

import com.catchtable.global.security.JwtTokenProvider;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import com.catchtable.user.entity.UserStatus;
import com.catchtable.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 빈자리 부하 테스트용 JWT 토큰 100개 생성 일회성 태스크.
 *
 * 실행:
 *   ./gradlew test --tests "com.catchtable.loadtest.VacancyLoadTestTokenGenerator.generateTokens" -DrunLoadTokenGen=true
 *
 * 결과:
 *   build/load-test-tokens.txt  (한 줄당 토큰 1개, 총 100줄)
 *   build/load-test-tokens.csv  (콤마 구분 한 줄 - k6 -e TOKENS= 에 바로 주입 가능)
 */
@SpringBootTest
class VacancyLoadTestTokenGenerator {

    private static final int DEFAULT_USER_COUNT = 100;
    private static final String EMAIL_PREFIX = "loadtest+";
    private static final String EMAIL_DOMAIN = "@catchtable.test";
    private static final String GOOGLE_ID_PREFIX = "loadtest-google-";
    private static final String NICKNAME_PREFIX = "loadtest_user_";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void generateTokens() throws IOException {
        // -DrunLoadTokenGen=true 일 때만 실행. CI/일반 test 실행 시 스킵.
        if (!"true".equals(System.getProperty("runLoadTokenGen"))) {
            System.out.println("[SKIP] -DrunLoadTokenGen=true 옵션 없음. 건너뜀.");
            return;
        }

        int userCount = Integer.getInteger("tokenCount", DEFAULT_USER_COUNT);
        List<String> tokens = new ArrayList<>(userCount);

        for (int i = 1; i <= userCount; i++) {
            String googleId = GOOGLE_ID_PREFIX + i;
            User user = userRepository.findByGoogleId(googleId)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .googleId(googleId)
                            .email(EMAIL_PREFIX + i + EMAIL_DOMAIN)
                            .nickname(NICKNAME_PREFIX + i)
                            .profileImage(null)
                            .role(UserRole.USER)
                            .status(UserStatus.ACTIVE)
                            .build()));

            String token = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
            tokens.add(token);
        }

        Path txt = Path.of("build", "load-test-tokens.txt");
        Path csv = Path.of("build", "load-test-tokens.csv");
        Files.createDirectories(txt.getParent());
        Files.write(txt, tokens);
        Files.writeString(csv, String.join(",", tokens));

        System.out.println("[OK] " + userCount + " tokens written:");
        System.out.println("  - " + txt.toAbsolutePath());
        System.out.println("  - " + csv.toAbsolutePath());
    }
}
