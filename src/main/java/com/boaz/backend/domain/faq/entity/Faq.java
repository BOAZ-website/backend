package com.boaz.backend.domain.faq.entity;

import com.boaz.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "faq", uniqueConstraints = @UniqueConstraint(columnNames = {"category", "order_num"}))
public class Faq extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private Integer orderNum;

    public enum Category {
        // 지원, 활동, 기타
        RECRUITMENT, ACTIVITY, ETC
    }

    public static Faq create(String question, String answer, Category category, Integer orderNum) {
        Faq faq = new Faq();
        faq.question = question;
        faq.answer = answer;
        faq.category = category;
        faq.orderNum = orderNum;
        return faq;
    }

    public void update(String question, String answer, Category category, Integer orderNum) {
        if (question != null) this.question = question;
        if (answer != null) this.answer = answer;
        if (category != null) this.category = category;
        if (orderNum != null) this.orderNum = orderNum;
    }
}
