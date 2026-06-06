package com.boaz.backend.domain.archive.controller;

import com.boaz.backend.domain.archive.dto.request.ArchiveCreateRequest;
import com.boaz.backend.domain.archive.dto.request.ArchiveUpdateRequest;
import com.boaz.backend.domain.archive.entity.Archive.Category;
import com.boaz.backend.domain.archive.service.ArchiveAdminService;
import com.boaz.backend.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "[Admin] Archiving", description = "Admin 전용 아카이빙 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/archiving")
@RequiredArgsConstructor
public class ArchiveAdminController {

    private final ArchiveAdminService archiveAdminService;

    // 프로젝트 등록
    @Operation(summary = "프로젝트 등록", description = "프로젝트 데이터와 이미지를 등록합니다.")
    @RequestBody(content = @Content(
        mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
        schemaProperties = {
            @SchemaProperty(name = "data", schema = @Schema(implementation = ArchiveCreateRequest.class)),
            @SchemaProperty(name = "image", schema = @Schema(type = "string", format = "binary"))
        },
        encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
    ))
    @PostMapping(value = "/projects", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Void>> createProject(
        @Valid @RequestPart("data") ArchiveCreateRequest request,
        @RequestPart("image") MultipartFile image
    ) {
        archiveAdminService.createArchive(Category.PROJECT, request, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(null));
    }

    // 활동사진 등록
    @Operation(summary = "활동사진 등록", description = "활동사진 데이터와 이미지를 등록합니다.")
    @RequestBody(content = @Content(
        mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
        schemaProperties = {
            @SchemaProperty(name = "data", schema = @Schema(implementation = ArchiveCreateRequest.class)),
            @SchemaProperty(name = "image", schema = @Schema(type = "string", format = "binary"))
        },
        encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
    ))
    @PostMapping(value = "/activities", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Void>> createActivity(
        @Valid @RequestPart("data") ArchiveCreateRequest request,
        @RequestPart("image") MultipartFile image
    ) {
        archiveAdminService.createArchive(Category.ACTIVITY, request, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(null));
    }

    // 기술블로그 등록
    @Operation(summary = "기술블로그 등록", description = "기술블로그 데이터와 이미지를 등록합니다.")
    @RequestBody(content = @Content(
        mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
        schemaProperties = {
            @SchemaProperty(name = "data", schema = @Schema(implementation = ArchiveCreateRequest.class)),
            @SchemaProperty(name = "image", schema = @Schema(type = "string", format = "binary"))
        },
        encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
    ))
    @PostMapping(value = "/blogs", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Void>> createBlog(
        @Valid @RequestPart("data") ArchiveCreateRequest request,
        @RequestPart("image") MultipartFile image
    ) {
        archiveAdminService.createArchive(Category.BLOG, request, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(null));
    }

    // 프로젝트 수정
    @Operation(summary = "프로젝트 수정", description = "프로젝트 데이터를 수정합니다.")
    @RequestBody(content = @Content(
        mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
        schemaProperties = {
            @SchemaProperty(name = "data", schema = @Schema(implementation = ArchiveUpdateRequest.class)),
            @SchemaProperty(name = "image", schema = @Schema(type = "string", format = "binary"))
        },
        encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
    ))
    @PatchMapping(value = "/projects/{id}", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Void>> updateProject(
        @PathVariable Long id,
        @Valid @RequestPart(value = "data", required = false) ArchiveUpdateRequest request,
        @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        archiveAdminService.updateArchive(Category.PROJECT, id, request, image);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 활동사진 수정
    @Operation(summary = "활동사진 수정", description = "활동사진 데이터를 수정합니다.")
    @RequestBody(content = @Content(
        mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
        schemaProperties = {
            @SchemaProperty(name = "data", schema = @Schema(implementation = ArchiveUpdateRequest.class)),
            @SchemaProperty(name = "image", schema = @Schema(type = "string", format = "binary"))
        },
        encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
    ))
    @PatchMapping(value = "/activities/{id}", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Void>> updateActivity(
        @PathVariable Long id,
        @Valid @RequestPart(value = "data", required = false) ArchiveUpdateRequest request,
        @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        archiveAdminService.updateArchive(Category.ACTIVITY, id, request, image);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 기술블로그 수정
    @Operation(summary = "기술블로그 수정", description = "기술블로그 데이터를 수정합니다.")
    @RequestBody(content = @Content(
        mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
        schemaProperties = {
            @SchemaProperty(name = "data", schema = @Schema(implementation = ArchiveUpdateRequest.class)),
            @SchemaProperty(name = "image", schema = @Schema(type = "string", format = "binary"))
        },
        encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
    ))
    @PatchMapping(value = "/blogs/{id}", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Void>> updateBlog(
        @PathVariable Long id,
        @Valid @RequestPart(value = "data", required = false) ArchiveUpdateRequest request,
        @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        archiveAdminService.updateArchive(Category.BLOG, id, request, image);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 프로젝트 삭제
    @Operation(summary = "프로젝트 삭제", description = "프로젝트 데이터를 삭제합니다.")
    @DeleteMapping("/projects/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
        @PathVariable Long id
    ) {
        archiveAdminService.deleteArchive(Category.PROJECT, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 활동사진 삭제
    @Operation(summary = "활동사진 삭제", description = "활동사진 데이터를 삭제합니다.")
    @DeleteMapping("/activities/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteActivity(
        @PathVariable Long id
    ) {
        archiveAdminService.deleteArchive(Category.ACTIVITY, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 기술블로그 삭제
    @Operation(summary = "기술블로그 삭제", description = "기술블로그 데이터를 삭제합니다.")
    @DeleteMapping("/blogs/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBlog(
        @PathVariable Long id
    ) {
        archiveAdminService.deleteArchive(Category.BLOG, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
