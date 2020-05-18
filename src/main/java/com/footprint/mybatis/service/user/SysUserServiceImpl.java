package com.footprint.mybatis.service.user;

import com.footprint.mybatis.mapper.user.SysUserMapper;
import com.footprint.mybatis.pojo.SysUserVo;
import com.footprint.mybatis.pojo.dto.SysUserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SysUserServiceImpl
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 13:14
 */
@Service
@Transactional(readOnly = true,rollbackFor = Exception.class)
public class SysUserServiceImpl implements SysUserService {


    @Autowired
    private SysUserMapper userMapper;

    @Override
    public SysUserDto findById(String id) {
        return userMapper.findById(id);
    }

    @Override
    public SysUserVo selectVoById(String id) {
        SysUserVo vo= userMapper.selectVo(id);
        return  vo;
    }

    @Override
    public SysUserDto selectOne(String id) {
        return userMapper.selectOne(id,"张三");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateById(String id) {
        userMapper.updateById(id,"张三");
        return userMapper.updateById(id,"张三");
    }
}
