package com.school.checkin.security;

import com.school.checkin.enums.RoleCode;
import lombok.Data;

@Data
public class LoginUser {
    private Long userId;
    private String username;
    private RoleCode roleCode;
    private String gradeName;
    private Long classId;
    private Boolean mustChangePassword;
}
