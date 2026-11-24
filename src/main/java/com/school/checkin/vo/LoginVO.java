package com.school.checkin.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private String roleCode;
    private String gradeName;
    private Long classId;
    private String realName;
    private Boolean mustChangePassword;
}
