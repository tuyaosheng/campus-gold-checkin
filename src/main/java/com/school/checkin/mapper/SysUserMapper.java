package com.school.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.checkin.entity.SysUser;
import com.school.checkin.vo.UserAccountVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select({
            "SELECT",
            "  u.id AS userId,",
            "  u.real_name AS realName,",
            "  u.username AS username,",
            "  u.role_code AS roleCode,",
            "  u.grade_name AS gradeName,",
            "  u.class_id AS classId,",
            "  c.class_name AS className,",
            "  u.enabled AS enabled,",
            "  u.must_change_password AS mustChangePassword",
            "FROM sys_user u",
            "LEFT JOIN class_info c ON c.id = u.class_id",
            "WHERE u.role_code <> 'ADMIN'",
            "ORDER BY u.role_code ASC, u.grade_name ASC, c.class_name ASC, u.id ASC"
    })
    List<UserAccountVO> selectManageableUsers();

    @Select({
            "SELECT u.real_name",
            "FROM sys_user u",
            "WHERE u.class_id = #{classId}",
            "  AND u.role_code = 'TEACHER'",
            "  AND u.enabled = 1",
            "ORDER BY u.id ASC",
            "LIMIT 1"
    })
    String selectTeacherNameByClassId(@Param("classId") Long classId);
}
