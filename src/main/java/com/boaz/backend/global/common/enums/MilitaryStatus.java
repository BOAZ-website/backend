package com.boaz.backend.global.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;

public enum MilitaryStatus {
    @Schema(description = "필 또는 면제")
    COMPLETED_OR_EXEMPT,
    @Schema(description = "미필")
    NOT_COMPLETED
}
