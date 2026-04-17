package com.boaz.backend.domain.curriculum.service;

import com.boaz.backend.domain.curriculum.dto.response.CurriculumResponse;
import com.boaz.backend.domain.curriculum.dto.response.CurriculumStepResponse;
import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.boaz.backend.domain.curriculum.repository.CurriculumRepository;
import com.boaz.backend.global.common.enums.Track;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;
    private final ObjectMapper objectMapper;

    public List<CurriculumResponse> getCurriculums(Track track) {
        List<Curriculum> curriculums;

        if (track != null) {
            track.validateNotAll();
            
            curriculums = curriculumRepository.findByTrack(track)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            curriculums = curriculumRepository.findAll();
        }

        return curriculums.stream()
                .map(c -> CurriculumResponse.from(c, parseSteps(c.getCurriculumSteps())))
                .toList();
    }

    private List<CurriculumStepResponse> parseSteps(String json) {
        if (json == null) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(m -> new CurriculumStepResponse(
                            (int) m.get("step"),
                            (String) m.get("title"),
                            (String) m.get("desc")
                    ))
                    .sorted(Comparator.comparingInt(CurriculumStepResponse::getStep))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
