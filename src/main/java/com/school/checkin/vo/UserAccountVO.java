package com.school.checkin.vo;

import lombok.Data;

@Data
public class UserAccountVO {
    private Long userId;
    private String realName;
    private String username;
    private String roleCode;
    private String gradeName;
    private Long classId;
    private String className;
    private Integer enabled;
    private Integer mustChangePassword;
}
