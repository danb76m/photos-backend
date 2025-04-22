package me.danb76.photos.database.controller;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import me.danb76.photos.database.tables.Photo;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping(path = "/api/upload/")
@RestController
public class UploadController {
    public static final String UPLOADS_BUCKET = "uploads";
    private static final String[] ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg"};

    @Value("${spring.minio.url}")
    private String minioUrl;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private PhotoController photoController;

    @PostMapping(path="/")
    public @ResponseBody ResponseEntity<Map<String, String>> upload(@RequestParam("imageFile") MultipartFile file) {
        Map<String, String> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("error", "File is empty.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        if (!isValidExtension(fileExtension)) {
            response.put("error", "Invalid file type. Only PNG and JPEG are allowed.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            String newFileName = UUID.randomUUID() + "." + fileExtension;
            Path path = Paths.get(newFileName);
            InputStream inputStream = file.getInputStream();

            ObjectWriteResponse res = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(UPLOADS_BUCKET)
                            .object(path.toString())
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());

            response.put("message", "Photo uploaded successfully");
            response.put("bucket", res.bucket());
            response.put("fileName", newFileName);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (MinioException | NoSuchAlgorithmException | InvalidKeyException e) {
            response.put("error", "Error uploading photo: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
            response.put("error", "Error reading file: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean isValidExtension(String extension) {
        for (String allowedExtension : ALLOWED_EXTENSIONS) {
            if (allowedExtension.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }


}
