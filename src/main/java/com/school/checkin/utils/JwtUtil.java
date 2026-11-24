package com.school.checkin.utils;

import com.school.checkin.common.ErrorCode;
import com.school.checkin.enums.RoleCode;
import com.school.checkin.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours}")
    private long expireHours;

    public String generateToken(Long userId, String username, RoleCode roleCode, String gradeName, Long classId, Boolean mustChangePassword) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireHours * 3600 * 1000);
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("roleCode", roleCode.name());
        claims.put("gradeName", gradeName);
        claims.put("classId", classId);
        claims.put("mustChangePassword", mustChangePassword);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expireAt)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token 无效或已过期");
        }
    }
}
