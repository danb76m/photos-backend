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

import java.io.File;
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
    private static final String[] ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "CR2"};

    @Value("${spring.minio.url}")
    private String minioUrl;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private PhotoController photoController;

    @PostMapping(path = "/")
    public @ResponseBody ResponseEntity<Map<String, String>> upload(@RequestParam("imageFile") MultipartFile file) {
        Map<String, String> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("error", "File is empty.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());

        if (!isValidExtension(fileExtension)) {
            response.put("error", "Invalid file type. Only PNG, JPEG, and CR2 are allowed.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        File cr2Temp = null;
        File jpegFile = null;
        File uploadFile = null;

        try {
            String baseFileName = UUID.randomUUID().toString();
            String newFileName;

            if (fileExtension.equalsIgnoreCase("cr2")) {
                // Save CR2 to temp file
                cr2Temp = File.createTempFile(baseFileName, ".cr2");
                file.transferTo(cr2Temp);

                // Convert to JPEG
                jpegFile = File.createTempFile(baseFileName, ".jpg");
                ProcessBuilder pb = new ProcessBuilder(
                        "bash", "-c",
                        String.format("dcraw -c %s | convert - %s", cr2Temp.getAbsolutePath(), jpegFile.getAbsolutePath())
                );
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    response.put("error", "Failed to convert CR2 to JPEG.");
                    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
                }

                uploadFile = jpegFile;
                newFileName = baseFileName + ".jpg";
                fileExtension = "jpg";
            } else {
                // Save original image file to temp
                uploadFile = File.createTempFile(baseFileName, "." + fileExtension);
                file.transferTo(uploadFile);
                newFileName = baseFileName + "." + fileExtension;
            }

            try (InputStream inputStream = java.nio.file.Files.newInputStream(uploadFile.toPath())) {
                ObjectWriteResponse res = minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(UPLOADS_BUCKET)
                                .object(newFileName)
                                .stream(inputStream, uploadFile.length(), -1)
                                .contentType("image/" + fileExtension.toLowerCase())
                                .build());

                response.put("message", "Photo uploaded successfully");
                response.put("bucket", res.bucket());
                response.put("fileName", newFileName);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }

        } catch (MinioException | NoSuchAlgorithmException | InvalidKeyException e) {
            response.put("error", "Error uploading photo: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IOException | InterruptedException e) {
            response.put("error", "File processing error: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            // Cleanup temp files
            if (cr2Temp != null && cr2Temp.exists()) {
                cr2Temp.delete();
            }
            if (jpegFile != null && jpegFile.exists()) {
                jpegFile.delete();
            }
            if (uploadFile != null && uploadFile.exists()) {
                uploadFile.delete();
            }
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
