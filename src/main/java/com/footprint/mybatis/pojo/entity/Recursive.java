package com.footprint.mybatis.pojo.entity;

import lombok.Data;

/**
 * Recursive
 *
 * @author Tinyice
 */
@Data
public class Recursive implements java.io.Serializable {

    private static final long serialVersionUID = -8990936350279565075L;

    private Integer id;

    private Integer pid;

    private String name;
}
