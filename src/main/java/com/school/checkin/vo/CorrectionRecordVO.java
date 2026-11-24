package com.school.checkin.vo;

import lombok.Data;

@Data
public class CorrectionRecordVO {
    private Long classId;
    private String gradeName;
    private String className;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private Integer checkedIn;
    private String checkinTime;
}
