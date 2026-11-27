package com.boaz.backend.global.security.authz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    @ParameterizedTest(name = "{0}")
    @DisplayName("TC-001 모든 권한에 한글 이름이 있다")
    @EnumSource(Permission.class)
    void everyPermissionHasDisplayName(Permission permission) {
        assertThat(permission.getDisplayName()).isNotBlank();
    }

    @Test
    @DisplayName("TC-002 한글 이름은 권한끼리 겹치지 않는다")
    void displayNamesAreUnique() {
        assertThat(Arrays.stream(Permission.values()).map(Permission::getDisplayName))
                .doesNotHaveDuplicates();
    }
}
