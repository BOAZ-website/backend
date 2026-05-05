package com.boaz.backend.domain.archive.service;

import com.boaz.backend.domain.archive.dto.request.ArchiveCreateRequest;
import com.boaz.backend.domain.archive.dto.request.ArchiveUpdateRequest;
import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.domain.archive.entity.Archive.Category;
import com.boaz.backend.domain.archive.repository.ArchiveRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.util.S3Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ArchiveAdminService {
    
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;   // 5MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://)([a-zA-Z0-9\\-]+\\.)+[a-zA-Z]{2,}(:\\d+)?(/[^\\s]*)?$");

    private final ArchiveRepository archiveRepository;
    private final S3Service s3Service;

    // 등록
    public void createArchive(Category category, ArchiveCreateRequest request, MultipartFile image) {
        validateCreateRequest(category, request);
        validateImage(image);

        String key = generateS3Key(category, request, image);
        String imageUrl = s3Service.uploadImage(key, image);

        try {
            Archive archive = Archive.builder()
                .term(request.getTerm())
                .category(category)
                .title(request.getTitle())
                .teamName(request.getTeamName())
                .track(request.getTrack())
                .imageUrl(imageUrl)
                .links(request.getLinks())
                .contentDate(request.getContentDate())
                .build();
            archiveRepository.save(archive);
            archiveRepository.flush();
        } catch (Exception e) {
            log.error("DB 저장 실패: category={}, title={}, error={}", category, request.getTitle(), e.getMessage());
            s3Service.deleteImage(imageUrl);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        registerS3RollbackCleanup(imageUrl);
    }

    // 수정
    public void updateArchive(Category category, Long id, ArchiveUpdateRequest request, MultipartFile image) {
        
        Archive archive = archiveRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.ARCHIVE_NOT_FOUND));
        
        if (archive.getCategory() != category) {
            throw new CustomException(ErrorCode.UNSUPPORTED_ARCHIVE_CATEGORY);
        }

        if (request == null && (image == null || image.isEmpty())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (category == Category.ACTIVITY && request == null) {
            throw new CustomException(ErrorCode.MISSING_HALF);
        }

        if (request != null) {
            if (request.getTrack() != null && category != Category.ACTIVITY) {
                request.getTrack().validateNotAll();
            }

            if (request.getContentDate().isPresent() && request.getContentDate().get() != null) {
                validateContentDate(request.getContentDate().get());
            }

            if (request.getLinks() != null) {
                validateLinks(request.getLinks());
            }

            if (category == Category.ACTIVITY && request.getHalf() == null) {
                throw new CustomException(ErrorCode.MISSING_HALF);
            }
            if (request.getHalf() != null) {
                validateHalf(request.getHalf());
            }
        }

        String oldImageUrl = archive.getImageUrl();
        String newImageUrl = null;

        if (image != null && !image.isEmpty()) {
            validateImage(image);
            String key = generateS3KeyForUpdate(category, archive, request, image);
            newImageUrl = s3Service.uploadImage(key, image);
        }

        LocalDate newContentDate = (request != null && request.getContentDate().isPresent())
            ? request.getContentDate().get()
            : archive.getContentDate();

        String newTeamName = (request != null && request.getTeamName().isPresent())
            ? request.getTeamName().get()
            : archive.getTeamName();

        try {
            archive.update(
                request != null ? request.getTerm() : null,
                request != null ? request.getTitle() : null,
                newTeamName,
                request != null ? request.getTrack() : null,
                newImageUrl,
                request != null ? request.getLinks() : null,
                newContentDate
            );
            archiveRepository.flush();

        } catch (Exception e) {
            log.error("DB 업데이트 실패: id={}, error={}", id, e.getMessage());
            if (newImageUrl != null) {
                s3Service.deleteImage(newImageUrl);
            }
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (newImageUrl != null) {
            registerS3RollbackCleanup(newImageUrl);
        }

        if (newImageUrl != null && oldImageUrl != null && !newImageUrl.equals(oldImageUrl)) {
            registerS3DeleteAfterCommit(oldImageUrl);
        }
    }

    // 삭제
    public void deleteArchive(Category category, Long id) {
        Archive archive = archiveRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.ARCHIVE_NOT_FOUND));

        if (archive.getCategory() != category) {
            throw new CustomException(ErrorCode.UNSUPPORTED_ARCHIVE_CATEGORY);
        }

        String imageUrl = archive.getImageUrl();

        archiveRepository.delete(archive);

        if (imageUrl != null) {
            registerS3DeleteAfterCommit(imageUrl);
        }
    }

    // 등록 필수값 검증
    private void validateCreateRequest(Category category, ArchiveCreateRequest request) {
        if (category == Category.ACTIVITY && request.getHalf() == null) {
            throw new CustomException(ErrorCode.MISSING_HALF);
        }
        if (request.getHalf() != null) {
            validateHalf(request.getHalf());
        }
        if (category != Category.ACTIVITY) {
            request.getTrack().validateNotAll();
        }

        if (request.getContentDate() != null) {
            validateContentDate(request.getContentDate());
        }
        validateLinks(request.getLinks());
    }

    // content_date 미래 날짜 검증
    private void validateContentDate(LocalDate contentDate) {
        if (contentDate.isAfter(LocalDate.now())) {
            throw new CustomException(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
        }
    }

    // half 형식 검증
    private void validateHalf(String half) {
        if (!half.matches("^\\d{2}-[12]$")) {
            throw new CustomException(ErrorCode.INVALID_HALF_FORMAT);
        }
    }

    // links URL 검증
    private void validateLinks(String links) {
        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(links);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (!jsonNode.isObject()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        jsonNode.fields().forEachRemaining(entry -> {
            String url = entry.getValue().asText();
            if (!URL_PATTERN.matcher(url).matches()) {
                throw new CustomException(ErrorCode.INVALID_URL_FORMAT);
            }
        });
    }

    // 이미지 검증 (형식, 용량)
    private void validateImage(MultipartFile image) {
        if (image.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        String originalFilename = image.getOriginalFilename();
        if (originalFilename == null) throw new CustomException(ErrorCode.INVALID_FILE_TYPE);

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    // track 한글 폴더명 변환
    private String getTrackFolderName(Track track) {
        return switch (track) {
            case ANALYSIS -> "분석";
            case VISUALIZATION -> "시각화";
            case ENGINEERING -> "엔지니어링";
            case ALL -> "전체";
        };
    }

    // S3 key 생성 (등록)
    private String generateS3Key(Category category, ArchiveCreateRequest request, MultipartFile image) {
        String extension = getExtension(image);
        String title = normalizeTitle(request.getTitle());
        String termFolder = request.getTerm() + "기";
        String trackFolder = getTrackFolderName(request.getTrack());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));

        return switch (category) {
            case PROJECT -> "projects/" + termFolder + "/" + trackFolder + "/" + title + "-" + timestamp + "." + extension;
            case ACTIVITY -> "activities/" + termFolder + "/" + request.getHalf() + "/" + trackFolder + "/" + title + "-" + timestamp + "." + extension;
            case BLOG -> "blogs/" + termFolder + "/" + trackFolder + "/" + title + "-" + timestamp + "." + extension;
        };
    }

    // S3 key 생성 (수정)
    private String generateS3KeyForUpdate(Category category, Archive archive, ArchiveUpdateRequest request, MultipartFile image) {
        String extension = getExtension(image);
        String title = normalizeTitle(request != null && request.getTitle() != null ? request.getTitle() : archive.getTitle());
        Integer term = request != null && request.getTerm() != null ? request.getTerm() : archive.getTerm();
        Track track = request != null && request.getTrack() != null ? request.getTrack() : archive.getTrack();
        String termFolder = term + "기";
        String trackFolder = getTrackFolderName(track);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        String half = request != null ? request.getHalf() : extractHalfFromImageUrl(archive.getImageUrl());

        return switch (category) {
            case PROJECT -> "projects/" + termFolder + "/" + trackFolder + "/" + title + "-" + timestamp + "." + extension;
            case ACTIVITY -> "activities/" + termFolder + "/" + half + "/" + trackFolder + "/" + title + "-" + timestamp + "." + extension;
            case BLOG -> "blogs/" + termFolder + "/" + trackFolder + "/" + title + "-" + timestamp + "." + extension;
        };
    }

    // 기존 imageUrl에서 half(예: "26-1") 추출 - image only 수정 시 사용
    private String extractHalfFromImageUrl(String imageUrl) {
        if (imageUrl == null) return null;
        for (String part : imageUrl.split("/")) {
            if (part.matches("^\\d{2}-[12]$")) return part;
        }
        return null;
    }

    // 파일 확장자 추출
    private String getExtension(MultipartFile image) {
        String originalFilename = image.getOriginalFilename();
        return originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    // 파일명 정규화
    private String normalizeTitle(String title) {
        return title
            .replaceAll("[\\s\\[\\]()]+", "-")  // 공백, 괄호 → -
            .replaceAll("[^a-zA-Z0-9가-힣-]", "-")  // 나머지 특수문자 → -
            .replaceAll("-{2,}", "-")  // 연속된 - → 하나로
            .replaceAll("^-|-$", "");  // 앞뒤 - 제거
    }

    private void registerS3RollbackCleanup(String imageUrl) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        s3Service.deleteImage(imageUrl);
                    } catch (Exception e) {
                        log.error("S3 이미지 삭제 실패 (롤백): imageUrl={}, error={}", imageUrl, e.getMessage());
                    }
                }
            }
        });
    }

    private void registerS3DeleteAfterCommit(String imageUrl) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    s3Service.deleteImage(imageUrl);
                } catch (Exception e) {
                    log.error("S3 이미지 삭제 실패 (커밋 후): imageUrl={}, error={}", imageUrl, e.getMessage());
                }
            }
        });
    }
}
