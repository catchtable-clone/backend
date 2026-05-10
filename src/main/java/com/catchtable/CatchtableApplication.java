package com.catchtable;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableJpaAuditing
@SpringBootApplication
public class CatchtableApplication {

    public static void main(String[] args) {
        // 로컬 실행 시 .env를 시스템 프로퍼티로 로드 (Spring Boot가 placeholder로 읽음)
        // CI/프로덕션은 workflow env 또는 docker compose env_file로 주입되므로 .env 없어도 OK
        Dotenv.configure()
                .ignoreIfMissing()
                .load()
                .entries()
                .forEach(e -> System.setProperty(e.getKey(), e.getValue()));

        SpringApplication.run(CatchtableApplication.class, args);
    }

}
