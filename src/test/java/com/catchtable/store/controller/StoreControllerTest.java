package com.catchtable.store.controller;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.global.exception.GlobalExceptionHandler;
import com.catchtable.store.dto.create.StoreCreateResponse;
import com.catchtable.store.dto.status.StoreStatusUpdateResponse;
import com.catchtable.store.dto.update.StoreUpdateResponse;
import com.catchtable.store.service.StoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StoreControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

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
        // given
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

        // when & then
        mockMvc.perform(post("/api/v1/stores")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("매장이 등록되었습니다."));
    }

    @Test
    @DisplayName("매장 등록 실패 - 권한 없음 403")
    void createStoreAccessDenied() throws Exception {
        // given
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

        // when & then
        mockMvc.perform(post("/api/v1/stores")
                        .header("X-User-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(ErrorCode.ADMIN_ONLY_STORE_CREATE.getMessage()));
    }

    @Test
    @DisplayName("매장 등록 실패 - 입력값 검증 실패 400")
    void createStoreValidationFail() throws Exception {
        // given - storeName 누락
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

        // when & then
        mockMvc.perform(post("/api/v1/stores")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("매장 수정 성공 - 200")
    void updateStoreSuccess() throws Exception {
        // given
        StoreUpdateResponse response = new StoreUpdateResponse(
                1L, "모수 부산", null, "WESTERN", "부산 해운대구",
                "YONGSAN", 35.1796, 129.0756, 8, "10:00", "21:00");
        given(storeService.updateStore(eq(1L), eq(1L), any())).willReturn(response);

        String requestBody = """
                {
                    "storeName": "모수 부산",
                    "category": "WESTERN",
                    "latitude": 35.1796,
                    "longitude": 129.0756,
                    "address": "부산 해운대구",
                    "district": "YONGSAN",
                    "team": 8,
                    "openTime": "10:00",
                    "closeTime": "21:00"
                }
                """;

        // when & then
        mockMvc.perform(put("/api/v1/stores/1")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("매장 정보가 수정되었습니다."))
                .andExpect(jsonPath("$.data.storeName").value("모수 부산"));
    }

    @Test
    @DisplayName("매장 상태 변경 성공 - 200")
    void updateStoreStatusSuccess() throws Exception {
        // given
        given(storeService.updateStoreStatus(eq(1L), eq(1L), any()))
                .willReturn(StoreStatusUpdateResponse.from(1L, "REST"));

        String requestBody = """
                {
                    "status": "REST"
                }
                """;

        // when & then
        mockMvc.perform(patch("/api/v1/stores/1")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("매장 상태가 변경되었습니다."))
                .andExpect(jsonPath("$.data.status").value("REST"));
    }

    @Test
    @DisplayName("매장 상세 조회 실패 - 매장 없음 404")
    void getStoreNotFound() throws Exception {
        // given
        given(storeService.getStore(999L))
                .willThrow(new CustomException(ErrorCode.STORE_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/stores/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(ErrorCode.STORE_NOT_FOUND.getMessage()));
    }
}
