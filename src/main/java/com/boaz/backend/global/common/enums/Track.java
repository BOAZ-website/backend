package com.boaz.backend.global.common.enums;

import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;

public enum Track {
    ALL("전부문"), ANALYSIS("분석"), VISUALIZATION("시각화"), ENGINEERING("엔지니어링");

    private final String displayName;

    Track(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void validateNotAll() {
        if (this == ALL) {
            throw new CustomException(ErrorCode.INVALID_TRACK_SELECTION);
        }
    }
}