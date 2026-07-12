package com.gadang.common;

import com.gadang.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * 게시글 첨부 이미지 업로드 — POST /api/files/upload
 * 반환: { url: "/files/xxxxxxxx.jpg" }  (정적 파일 서빙 경로)
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @Value("${gadang.upload.dir:uploads}")
    private String uploadDir;

    private static final long MAX_SIZE  = 10 * 1024 * 1024L; // 10 MB
    private static final java.util.Set<String> ALLOWED_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResult> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("파일이 비어 있습니다.");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("파일 크기는 10MB를 초과할 수 없습니다.");
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct)) throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");

        String ext = ext(file.getOriginalFilename());
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

        return ApiResponse.ok(new UploadResult("/files/" + filename));
    }

    private static String ext(String name) {
        if (name == null) return ".jpg";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase() : ".jpg";
    }

    public record UploadResult(String url) {}
}
