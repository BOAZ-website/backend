package com.boaz.backend.domain.curriculum.service;

import com.boaz.backend.domain.curriculum.dto.request.CurriculumCreateRequest;
import com.boaz.backend.domain.curriculum.dto.request.CurriculumUpdateRequest;
import com.boaz.backend.domain.curriculum.dto.response.CurriculumResponse;
import com.boaz.backend.domain.curriculum.dto.response.CurriculumStepResponse;
import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.boaz.backend.domain.curriculum.repository.CurriculumRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
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

    @Transactional
    public CurriculumResponse createCurriculum(CurriculumCreateRequest request) {
        if (request.getTrack() == Track.ALL) {
            throw new CustomException(ErrorCode.INVALID_PARAMETER);
        }
        if (curriculumRepository.existsByTrack(request.getTrack())) {
            throw new CustomException(ErrorCode.DUPLICATE_TRACK);
        }
        String stepsJson = serializeSteps(request.getCurriculumSteps().stream()
                .sorted(Comparator.comparingInt(s -> s.getStep()))
                .map(s -> toStepMap(s.getStep(), s.getTitle(), s.getDesc()))
                .toList());
        Curriculum curriculum = Curriculum.create(request.getTrack(), stepsJson);
        curriculumRepository.save(curriculum);
        return CurriculumResponse.from(curriculum, parseSteps(stepsJson));
    }

    @Transactional
    public CurriculumResponse updateCurriculum(Long curriculumId, CurriculumUpdateRequest request) {
        Curriculum curriculum = curriculumRepository.findById(curriculumId)
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));
        String stepsJson = serializeSteps(request.getCurriculumSteps().stream()
                .sorted(Comparator.comparingInt(s -> s.getStep()))
                .map(s -> toStepMap(s.getStep(), s.getTitle(), s.getDesc()))
                .toList());
        curriculum.updateSteps(stepsJson);
        return CurriculumResponse.from(curriculum, parseSteps(stepsJson));
    }

    @Transactional
    public void deleteCurriculum(Long curriculumId) {
        Curriculum curriculum = curriculumRepository.findById(curriculumId)
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));
        curriculumRepository.delete(curriculum);
    }

    public List<CurriculumResponse> getCurriculums(Track track) {
        List<Curriculum> curriculums;

        if (track != null) {
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

    private Map<String, Object> toStepMap(int step, String title, String desc) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("step", step);
        map.put("title", title);
        map.put("desc", desc);
        return map;
    }

    private String serializeSteps(List<Map<String, Object>> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
