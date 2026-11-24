package com.school.checkin.dto;

import lombok.Data;

@Data
public class UpdateStudentOrderRequest {
    private Long studentId;
    private Integer displayOrderNo;
}
