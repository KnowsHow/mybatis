package com.footprint.mybatis.mapper.user;

import com.footprint.mybatis.pojo.dto.SysUserDto;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

/**
 * SysyUserMapper
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 13:15
 */

public interface SysUserMapper {

    SysUserDto findById(@Param("id") String id);

}
