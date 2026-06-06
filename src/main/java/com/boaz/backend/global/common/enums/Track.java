package com.boaz.backend.global.common.enums;

import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;

public enum Track {
    // 전부문, 분석, 시각화, 엔지니어링
    ALL, ANALYSIS, VISUALIZATION, ENGINEERING;

    public void validateNotAll() {
        if (this == ALL) {
            throw new CustomException(ErrorCode.INVALID_TRACK_SELECTION);
        }
    }
}