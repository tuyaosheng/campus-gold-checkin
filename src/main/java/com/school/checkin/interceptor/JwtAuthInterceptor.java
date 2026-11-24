package com.school.checkin.interceptor;

import com.school.checkin.enums.RoleCode;
import com.school.checkin.security.LoginUser;
import com.school.checkin.security.UserContext;
import com.school.checkin.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public JwtAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Claims claims = jwtUtil.parseToken(token);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(((Number) claims.get("userId")).longValue());
        loginUser.setUsername(String.valueOf(claims.get("username")));
        loginUser.setRoleCode(RoleCode.valueOf(String.valueOf(claims.get("roleCode"))));
        Object gradeNameObj = claims.get("gradeName");
        if (gradeNameObj != null && !"null".equals(String.valueOf(gradeNameObj))) {
            loginUser.setGradeName(String.valueOf(gradeNameObj));
        }
        Object classIdObj = claims.get("classId");
        if (classIdObj != null && !"null".equals(String.valueOf(classIdObj))) {
            loginUser.setClassId(((Number) classIdObj).longValue());
        }
        Object mustChangePassword = claims.get("mustChangePassword");
        loginUser.setMustChangePassword(Boolean.TRUE.equals(mustChangePassword) || "true".equalsIgnoreCase(String.valueOf(mustChangePassword)));
        if (Boolean.TRUE.equals(loginUser.getMustChangePassword())
                && !request.getRequestURI().equals("/api/admin/password/change")
                && !request.getRequestURI().equals("/api/admin/login")) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"首次登录后必须先修改密码\",\"data\":null}");
            return false;
        }
        UserContext.set(loginUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
