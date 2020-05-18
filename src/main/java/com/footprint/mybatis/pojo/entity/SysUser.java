package com.footprint.mybatis.pojo.entity;

import lombok.Data;

/**
 * SysUser
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 13:10
 */
@Data
public class SysUser implements java.io.Serializable{

    private static final long serialVersionUID = -8409324121632553916L;

    private String id;

    private String username;

    private String nickname;

    private String password;

    private String avatar;

    private String remark;
}
