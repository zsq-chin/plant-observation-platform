package com.jingxuan.plant.service;

import com.jingxuan.entity.SysUser;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 花名设置规则（V4 下一步开发计划 §5.1）。 */
class PlantProfileServiceTest {

    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final PlantProfileService service = new PlantProfileService(sysUserMapper);

    private SysUser user;

    @BeforeEach
    void setUp() {
        user = new SysUser();
        user.setId(7L);
        user.setRealName("张三");
        when(sysUserMapper.selectById(7L)).thenReturn(user);
    }

    @Test
    void updateStoresTrimmedNickname() {
        assertEquals("青禾", service.updateDisplayName(7L, "  青禾 "));
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertEquals("青禾", captor.getValue().getDisplayName());
    }

    @Test
    void rejectsBlankNickname() {
        assertThrows(BusinessException.class, () -> service.updateDisplayName(7L, "   "));
        assertThrows(BusinessException.class, () -> service.updateDisplayName(7L, null));
        verify(sysUserMapper, never()).updateById(org.mockito.ArgumentMatchers.<SysUser>any());
    }

    @Test
    void rejectsBadLength() {
        assertThrows(BusinessException.class, () -> service.updateDisplayName(7L, "禾"));
        assertThrows(BusinessException.class, () -> service.updateDisplayName(7L, "一二三四五六七八九十一二三四五六七"));
    }

    @Test
    void rejectsWhitespaceInsideNickname() {
        assertThrows(BusinessException.class, () -> service.updateDisplayName(7L, "青 禾"));
    }

    @Test
    void currentNicknameIsReturned() {
        user.setDisplayName("木槿");
        assertEquals("木槿", service.currentDisplayName(7L));
    }
}
