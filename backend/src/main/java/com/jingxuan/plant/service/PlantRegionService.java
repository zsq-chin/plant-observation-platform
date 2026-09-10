package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.plant.entity.SysRegion;
import com.jingxuan.plant.mapper.SysRegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 行政区划查询（种子数据来自 V10 迁移，管理员维护见 Admin 接口）。 */
@Service
@RequiredArgsConstructor
public class PlantRegionService {

    private final SysRegionMapper sysRegionMapper;

    public List<SysRegion> listProvinces() {
        return sysRegionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getRegionLevel, "PROVINCE")
                .eq(SysRegion::getEnabled, 1)
                .orderByAsc(SysRegion::getSortOrder));
    }

    public List<SysRegion> listChildren(String parentCode) {
        return sysRegionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getParentCode, parentCode)
                .eq(SysRegion::getEnabled, 1)
                .orderByAsc(SysRegion::getSortOrder));
    }

    public SysRegion getByCode(String regionCode) {
        return sysRegionMapper.selectOne(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getRegionCode, regionCode)
                .last("LIMIT 1"));
    }

    /** 由区划代码反查名称，找不到返回 null（调用方容忍）。 */
    public String resolveName(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return null;
        }
        SysRegion region = getByCode(regionCode);
        return region != null ? region.getRegionName() : null;
    }
}
