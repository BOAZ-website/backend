package com.boaz.backend.domain.recruitment.entity;

import com.boaz.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "recruitment")
public class Recruitment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer term;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(columnDefinition = "JSON", nullable = false)
    private String schedule;

    @Column(columnDefinition = "TEXT")
    private String brochureUrl;

    // 주어진 시각이 모집 기간(start_date ~ end_date, 양끝 포함) 안인지 판정한다.
    // 호출부에서 초 단위로 버린 현재 시각을 넘겨야 마지막 초까지 정확히 포함된다.
    public boolean isActive(LocalDateTime now) {
        return !now.isBefore(startDate) && !now.isAfter(endDate);
    }

    public static Recruitment create(Integer term, LocalDateTime startDate, LocalDateTime endDate, String schedule, String brochureUrl) {
        Recruitment recruitment = new Recruitment();
        recruitment.term = term;
        recruitment.startDate = startDate;
        recruitment.endDate = endDate;
        recruitment.schedule = schedule;
        recruitment.brochureUrl = brochureUrl;
        return recruitment;
    }

    public void update(Integer term, LocalDateTime startDate, LocalDateTime endDate, String schedule, JsonNullable<String> brochureUrl) {
        if (term != null) this.term = term;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (schedule != null) this.schedule = schedule;
        if (brochureUrl != null && brochureUrl.isPresent()) this.brochureUrl = brochureUrl.get();
    }
}
