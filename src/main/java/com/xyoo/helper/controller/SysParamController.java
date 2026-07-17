package com.xyoo.helper.controller;

import com.xyoo.helper.common.BaseController;
import com.xyoo.helper.common.Result;
import com.xyoo.helper.service.SysParamService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统参数 REST API
 *
 * <pre>
 * GET /api/params/{paramId} — 查询参数值，返回 { "paramId":"xxx", "paramValue":"1", "paramDesc":"..." }
 * </pre>
 */
@RestController
@RequestMapping("/api/params")
public class SysParamController extends BaseController {

    private final SysParamService sysParamService;

    public SysParamController(SysParamService sysParamService) {
        this.sysParamService = sysParamService;
    }

    /**
     * 查询单个系统参数
     */
    @GetMapping("/{paramId}")
    public Result<Map<String, String>> getParam(@PathVariable String paramId) {
        return sysParamService.getByParamId(paramId)
                .map(p -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("paramId", p.getParamId());
                    map.put("paramValue", p.getParamValue());
                    map.put("paramDesc", p.getParamDesc() != null ? p.getParamDesc() : "");
                    return Result.success(map);
                })
                .orElse(Result.error("参数不存在: " + paramId));
    }
}
