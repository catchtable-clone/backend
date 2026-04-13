package com.catchtable.store.controller;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.global.exception.GlobalExceptionHandler;
import com.catchtable.store.dto.create.StoreCreateResponse;
import com.catchtable.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StoreControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StoreService storeService;

    @InjectMocks
    private StoreController storeController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(storeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("매장 등록 성공 - 201")
    void createStoreSuccess() throws Exception {
        given(storeService.createStore(eq(1L), any())).willReturn(StoreCreateResponse.from(1L));

        String requestBody = """
                {
                    "storeName": "모수 서울",
                    "category": "WESTERN",
                    "latitude": 37.534,
                    "longitude": 126.993,
                    "address": "서울 용산구 이태원로 246",
                    "district": "YONGSAN",
                    "team": 10,
                    "openTime": "11:00",
                    "closeTime": "22:00"
                }
                """;

        mockMvc.perform(post("/api/v1/stores")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("매장이 등록되었습니다."));
    }

    @Test
    @DisplayName("매장 등록 실패 - 입력값 검증 400")
    void createStoreValidationFail() throws Exception {
        String requestBody = """
                {
                    "category": "WESTERN",
                    "latitude": 37.534,
                    "longitude": 126.993,
                    "address": "서울 용산구 이태원로 246",
                    "district": "YONGSAN",
                    "team": 10,
                    "openTime": "11:00",
                    "closeTime": "22:00"
                }
                """;

        mockMvc.perform(post("/api/v1/stores")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("매장 등록 실패 - 권한 없음 403")
    void createStoreAccessDenied() throws Exception {
        given(storeService.createStore(eq(2L), any()))
                .willThrow(new CustomException(ErrorCode.ADMIN_ONLY_STORE_CREATE));

        String requestBody = """
                {
                    "storeName": "모수 서울",
                    "category": "WESTERN",
                    "latitude": 37.534,
                    "longitude": 126.993,
                    "address": "서울 용산구 이태원로 246",
                    "district": "YONGSAN",
                    "team": 10,
                    "openTime": "11:00",
                    "closeTime": "22:00"
                }
                """;

        mockMvc.perform(post("/api/v1/stores")
                        .header("X-User-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("매장 상세 조회 실패 - 매장 없음 404")
    void getStoreNotFound() throws Exception {
        given(storeService.getStore(999L))
                .willThrow(new CustomException(ErrorCode.STORE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/stores/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
