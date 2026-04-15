package com.boaz.backend.domain.admin.dto.request;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import lombok.Getter;
import org.openapitools.jackson.nullable.JsonNullable;

@Getter
public class AdminUpdateRequest {

    private JsonNullable<Admin.Role> role = JsonNullable.undefined();
    private JsonNullable<String> name = JsonNullable.undefined();
    private JsonNullable<Track> track = JsonNullable.undefined();
    private JsonNullable<Integer> term = JsonNullable.undefined();
    private JsonNullable<Admin.TeamName> teamName = JsonNullable.undefined();
}