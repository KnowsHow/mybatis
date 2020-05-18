package com.footprint.mybatis.mapper.demo;

import com.footprint.mybatis.pojo.BookVo;
import com.footprint.mybatis.pojo.NestedBook;
import com.footprint.mybatis.pojo.RecursiveDemo;
import com.footprint.mybatis.pojo.entity.Book;

import java.util.List;

/**
 * DemoMapper
 *
 * @author Tinyice
 */
public interface DemoMapper {

    List<RecursiveDemo> selectByPid(Integer pid);

    List<RecursiveDemo> selectByPidNested(Integer pid);

    BookVo selectBookByUid(Integer uid);

    List multiSelect();

    NestedBook selectNestedBook1();
    NestedBook selectNestedBook2();
}
