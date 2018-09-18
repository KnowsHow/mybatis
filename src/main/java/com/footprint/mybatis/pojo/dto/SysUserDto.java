package com.footprint.mybatis.pojo.dto;

import lombok.Data;

/**
 * SysUserDto
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 13:21
 */
@Data
public class SysUserDto {

    private String id;

    private String username;

    private String nickname;

    private String password;

    private String avatar;

    private String remark;
}
