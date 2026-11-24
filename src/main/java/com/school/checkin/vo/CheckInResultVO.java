package com.school.checkin.vo;

import lombok.Data;

@Data
public class CheckInResultVO {
    private Long recordId;
    private Long studentId;
    private String studentName;
    private String timeSlot;
    private String checkinTime;
}
