package com.school.checkin.vo;

import lombok.Data;

@Data
public class SchoolTopStudentVO {
    private Long studentId;
    private String studentName;
    private String gradeName;
    private String className;
    private Integer totalCheckinCount;
}
