package com.school.checkin.vo;

import lombok.Data;

@Data
public class StudentQualityRecordVO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String gradeName;
    private String className;
    private String timeSlot;
    private String checkinTime;
}
