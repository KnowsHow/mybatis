package com.footprint.mybatis.pojo.entity;

import lombok.Data;

/**
 * SysUser
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 13:10
 */
@Data
public class SysUser {

    private String id;

    private String username;

    private String nickname;

    private String password;

    private String avatar;

    private String remark;
}
