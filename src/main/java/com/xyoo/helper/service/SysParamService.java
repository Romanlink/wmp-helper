package com.xyoo.helper.service;

import com.xyoo.helper.entity.SysParam;
import com.xyoo.helper.repository.SysParamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 系统参数服务
 */
@Service
public class SysParamService {

    private final SysParamRepository sysParamRepository;

    public SysParamService(SysParamRepository sysParamRepository) {
        this.sysParamRepository = sysParamRepository;
    }

    /**
     * 根据 paramId 查询参数值
     *
     * @param paramId 参数标识
     * @return 参数实体（不存在则返回空）
     */
    @Transactional(readOnly = true)
    public Optional<SysParam> getByParamId(String paramId) {
        return sysParamRepository.findById(paramId);
    }

    /**
     * 获取参数值（字符串），不存在则返回默认值
     */
    @Transactional(readOnly = true)
    public String getValue(String paramId, String defaultValue) {
        return sysParamRepository.findById(paramId)
                .map(SysParam::getParamValue)
                .orElse(defaultValue);
    }

    /**
     * 保存或更新参数
     */
    @Transactional
    public SysParam save(SysParam param) {
        return sysParamRepository.save(param);
    }

    /**
     * 初始化系统参数（仅在不存在时插入）
     */
    @Transactional
    public void initIfAbsent(String paramId, String paramValue, String paramDesc) {
        if (!sysParamRepository.existsById(paramId)) {
            sysParamRepository.save(new SysParam(paramId, paramValue, paramDesc));
        }
    }
}
