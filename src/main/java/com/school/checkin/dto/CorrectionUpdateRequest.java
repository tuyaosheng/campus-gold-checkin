package com.school.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CorrectionUpdateRequest {
    @NotBlank
    private String date;
    @NotBlank
    private String timeSlot;
    private String checkinTime;
    @NotNull
    private Long studentId;
}
