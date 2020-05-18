package com.footprint.mybatis.pojo;

import com.footprint.mybatis.pojo.entity.Book;
import lombok.Data;

/**
 * SysUserDto
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 13:21
 */
@Data
public class SysUserVo implements java.io.Serializable{

    private static final long serialVersionUID = 814240661449516199L;

    private String id;

    private String username;

    private String nickname;

    private String password;

    private String avatar;

    private String remark;

    private Book book;
}
