package com.boaz.backend.global.util;

import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${spring.cloud.aws.region.static:}")
    private String region;

    public void uploadCsv(String bucket, String key, byte[] content) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("text/csv")
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.S3_UPLOAD_FAILED);
        }
    }

    // 이미지 업로드 후 URL 반환
    public String uploadImage(String bucket, String key, MultipartFile image) {
        try {
            String contentType = image.getContentType();
            if (contentType == null) {
                String filename = image.getOriginalFilename();
                String ext = filename != null ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase() : "";
                contentType = switch (ext) {
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    default -> "application/octet-stream";
                };
            }
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
            s3Client.putObject(request, RequestBody.fromBytes(image.getBytes()));
            return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.S3_UPLOAD_FAILED);
        }
    }

    // 이미지 삭제
    public void deleteImage(String bucket, String imageUrl) {
        try {
            // URL에서 key 추출
            URI uri = new URI(imageUrl);
            String key = uri.getPath().substring(1);
            
            s3Client.deleteObject(builder -> builder
                .bucket(bucket)
                .key(key)
                .build());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.S3_DELETE_FAILED);
        }
    }
}