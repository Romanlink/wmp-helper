package com.xyoo.helper.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Comment;

import java.io.Serializable;

/**
 * 系统参数表
 * <p>
 * 存储全局开关、配置项等键值对参数。
 * </p>
 */
@Entity
@Table(name = "sys_param")
public class SysParam implements Serializable {

    @Id
    @Column(name = "param_id", nullable = false, length = 64)
    @Comment("参数标识（唯一键）")
    private String paramId;

    @Column(name = "param_value", nullable = false, length = 256)
    @Comment("参数值")
    private String paramValue;

    @Column(name = "param_desc", length = 512)
    @Comment("参数说明")
    private String paramDesc;

    public SysParam() {}

    public SysParam(String paramId, String paramValue, String paramDesc) {
        this.paramId = paramId;
        this.paramValue = paramValue;
        this.paramDesc = paramDesc;
    }

    public String getParamId() { return paramId; }
    public void setParamId(String paramId) { this.paramId = paramId; }

    public String getParamValue() { return paramValue; }
    public void setParamValue(String paramValue) { this.paramValue = paramValue; }

    public String getParamDesc() { return paramDesc; }
    public void setParamDesc(String paramDesc) { this.paramDesc = paramDesc; }
}
