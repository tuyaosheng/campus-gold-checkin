package com.school.checkin.vo;

import lombok.Data;

@Data
public class StudentOrderBindingVO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private Integer displayOrderNo;
    private Integer defaultOrderNo;
    private Integer effectiveOrderNo;
}
