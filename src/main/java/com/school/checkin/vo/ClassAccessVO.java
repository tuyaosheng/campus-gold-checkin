package com.school.checkin.vo;

import lombok.Data;

@Data
public class ClassAccessVO {
    private Long classId;
    private String gradeName;
    private String className;
    private String teacherName;
    private String teacherUsername;
    private Integer studentCount;
    private String uniqueCode;
}
