package com.footprint.mybatis.mapper.user;

import com.footprint.mybatis.pojo.SysUserVo;
import com.footprint.mybatis.pojo.dto.SysUserDto;
import com.footprint.mybatis.pojo.entity.Book;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

/**
 * SysyUserMapper
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 13:15
 */
public interface SysUserMapper {

    SysUserDto findById(String id);

    Book selectBookByUid(String uid);

    SysUserVo selectVo(String id);

    SysUserDto selectOne(@Param("id") String id,@Param("name") String name);

    int updateById(@Param("id") String id,@Param("name") String name);

}
