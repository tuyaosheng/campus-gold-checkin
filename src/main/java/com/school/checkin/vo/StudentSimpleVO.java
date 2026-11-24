package com.school.checkin.vo;

import lombok.Data;

@Data
public class StudentSimpleVO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private Integer displayNo;
    private Boolean checkedIn;
    private String checkinTime;
}


