package com.jingxuan.plant.service;

import com.jingxuan.entity.SysUser;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 学生公开资料：公开展示花名（V4 下一步开发计划 §5.1）。 */
@Service
@RequiredArgsConstructor
public class PlantProfileService {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 16;

    private final SysUserMapper sysUserMapper;

    /** 当前花名（可为空，空时公开端按隐私策略回退）。 */
    public String currentDisplayName(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getDisplayName();
    }

    /** 设置花名：2-16 个字符，不含空格与控制字符。 */
    @Transactional(rollbackFor = Exception.class)
    public String updateDisplayName(Long userId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new BusinessException("花名不能为空");
        }
        if (name.length() < MIN_LENGTH || name.length() > MAX_LENGTH) {
            throw new BusinessException("花名长度需为 " + MIN_LENGTH + "-" + MAX_LENGTH + " 个字符");
        }
        for (char ch : name.toCharArray()) {
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)) {
                throw new BusinessException("花名不能包含空格或特殊字符");
            }
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setDisplayName(name);
        sysUserMapper.updateById(user);
        return name;
    }
}
