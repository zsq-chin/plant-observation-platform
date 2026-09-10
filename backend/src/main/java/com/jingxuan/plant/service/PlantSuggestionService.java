package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.common.PageResult;
import com.jingxuan.common.PageUtil;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.plant.dto.SpeciesSuggestionCreateRequest;
import com.jingxuan.plant.dto.SuggestionDecisionRequest;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.entity.PlantSpeciesSuggestion;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import com.jingxuan.plant.mapper.PlantSpeciesSuggestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 新物种建议（V4 §68）：学生提交 → 教师确认（绑定/新建物种）。 */
@Service
@RequiredArgsConstructor
public class PlantSuggestionService {

    private final PlantSpeciesSuggestionMapper suggestionMapper;
    private final PlantSpeciesMapper speciesMapper;

    @Transactional(rollbackFor = Exception.class)
    public PlantSpeciesSuggestion create(SpeciesSuggestionCreateRequest req, Long studentId) {
        if (!StringUtils.hasText(req.suggestedCommonName()) && !StringUtils.hasText(req.suggestedScientificName())) {
            throw new BusinessException("请至少填写中文名或学名");
        }
        PlantSpeciesSuggestion suggestion = new PlantSpeciesSuggestion();
        suggestion.setSubmitterId(studentId);
        suggestion.setSuggestedCommonName(trim(req.suggestedCommonName()));
        suggestion.setSuggestedScientificName(trim(req.suggestedScientificName()));
        suggestion.setDescription(trim(req.description()));
        suggestion.setSampleObservationId(req.sampleObservationId());
        suggestion.setStatus("PENDING");
        suggestionMapper.insert(suggestion);
        return suggestion;
    }

    public PageResult<PlantSpeciesSuggestion> listMine(Long studentId, int page, int size) {
        return PageUtil.query(page, size, suggestionMapper, w -> w
                .eq(PlantSpeciesSuggestion::getSubmitterId, studentId)
                .orderByDesc(PlantSpeciesSuggestion::getCreateTime));
    }

    public PageResult<PlantSpeciesSuggestion> listPending(int page, int size) {
        return PageUtil.query(page, size, suggestionMapper, w -> w
                .eq(PlantSpeciesSuggestion::getStatus, "PENDING")
                .orderByDesc(PlantSpeciesSuggestion::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public PlantSpeciesSuggestion decide(Long suggestionId, SuggestionDecisionRequest req, Long reviewerId) {
        PlantSpeciesSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null) {
            throw new BusinessException("建议不存在");
        }
        String status = req.status();
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BusinessException("status 必须为 APPROVED 或 REJECTED");
        }
        if ("APPROVED".equals(status)) {
            Long linked = req.linkedSpeciesId();
            if (linked == null && StringUtils.hasText(suggestion.getSuggestedCommonName())) {
                PlantSpecies created = new PlantSpecies();
                created.setCommonName(suggestion.getSuggestedCommonName());
                created.setScientificName(suggestion.getSuggestedScientificName());
                created.setDescription(suggestion.getDescription());
                created.setEnabled(1);
                created.setCreatedBy(reviewerId);
                created.setSource("学生建议-教师确认");
                speciesMapper.insert(created);
                linked = created.getId();
            }
            if (linked == null) {
                throw new BusinessException("通过建议需要绑定或新建标准物种");
            }
            PlantSpecies species = speciesMapper.selectById(linked);
            if (species == null) {
                throw new BusinessException("绑定的标准物种不存在");
            }
            suggestion.setLinkedSpeciesId(linked);
            suggestion.setStatus("APPROVED");
        } else {
            suggestion.setStatus("REJECTED");
        }
        suggestion.setReviewerId(reviewerId);
        suggestion.setReviewComment(trim(req.comment()));
        suggestionMapper.updateById(suggestion);
        return suggestion;
    }

    private String trim(String value) {
        return value == null ? null : (value.isBlank() ? null : value.trim());
    }
}
