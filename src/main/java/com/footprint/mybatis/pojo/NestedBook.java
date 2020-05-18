package com.footprint.mybatis.pojo;

import com.footprint.mybatis.pojo.entity.SysUser;
import lombok.Data;

/**
 * Book
 *
 * @author Tinyice
 */
@Data
public class NestedBook implements java.io.Serializable {

    private static final long serialVersionUID = -33570868395441815L;

    private Integer id;

    private Integer uid;

    private String name;

    private Float price;

    private SysUser author;
}
