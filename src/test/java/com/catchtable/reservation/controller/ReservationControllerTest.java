package com.catchtable.reservation.controller;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.global.exception.GlobalExceptionHandler;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.reservation.service.ReservationService;
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
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationController reservationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reservationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // === 예약 생성 ===

    @Test
    @DisplayName("예약 생성 성공 - 201")
    void createReservationSuccess() throws Exception {
        given(reservationService.create(eq(1L), any()))
                .willReturn(new ReservationCreateResponseDto(1L, ReservationStatus.PENDING));

        String requestBody = """
                {
                    "remainId": 1,
                    "member": 4
                }
                """;

        mockMvc.perform(post("/api/v1/reservations")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201));
    }

    @Test
    @DisplayName("예약 생성 실패 - 입력값 검증 400")
    void createReservationValidationFail() throws Exception {
        String requestBody = """
                {
                    "remainId": 1,
                    "member": 0
                }
                """;

        mockMvc.perform(post("/api/v1/reservations")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // === 예약 조회 ===

    @Test
    @DisplayName("예약 상세 조회 실패 - 존재하지 않는 예약 404")
    void getReservationNotFound() throws Exception {
        given(reservationService.getReservationDetail(eq(999L), eq(1L)))
                .willThrow(new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/reservations/999")
                        .header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("예약 상세 조회 실패 - 본인 예약 아님 403")
    void getReservationNotOwner() throws Exception {
        given(reservationService.getReservationDetail(eq(1L), eq(999L)))
                .willThrow(new CustomException(ErrorCode.NOT_RESERVATION_OWNER));

        mockMvc.perform(get("/api/v1/reservations/1")
                        .header("X-User-Id", "999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // === 예약 취소 ===

    @Test
    @DisplayName("예약 취소 실패 - 이미 취소된 예약 400")
    void cancelReservationAlreadyCanceled() throws Exception {
        doThrow(new CustomException(ErrorCode.ALREADY_CANCELED))
                .when(reservationService).cancelReservation(eq(1L), eq(1L));

        mockMvc.perform(delete("/api/v1/reservations/1")
                        .header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
