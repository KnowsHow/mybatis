package com.footprint.mybatis.service.user;

import com.footprint.mybatis.pojo.SysUserVo;
import com.footprint.mybatis.pojo.dto.SysUserDto;

/**
 * SysUserService
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 13:14
 */
public interface SysUserService {

    SysUserDto findById(String id);

    SysUserDto selectOne(String id);
    int updateById(String id);

    SysUserVo selectVoById(String id);
}
