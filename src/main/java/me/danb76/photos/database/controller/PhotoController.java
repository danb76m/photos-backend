package me.danb76.photos.database.controller;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import me.danb76.photos.database.repositories.PhotosRepository;
import me.danb76.photos.database.tables.Photo;
import me.danb76.photos.service.JobsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping(path = "/photos")
@RestController
public class PhotoController {
    private static final Logger logger = LoggerFactory.getLogger(JobsService.class);

    @Autowired
    private PhotosRepository repository;

    @Autowired
    private MinioClient minioClient;

    @GetMapping("/{categoryId}")
    public ResponseEntity<Map<String, Object>> getPhotosByCategory(
            @PathVariable UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Photo> photoPage = repository.findByCategory(categoryId, pageable);

            if (photoPage.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }

            List<Map<String, Object>> photosResponse = photoPage.getContent().stream()
                    .map(photo -> {
                        Map<String, Object> photoInfo = new HashMap<>();
                        photoInfo.put("id", photo.getId());
                        photoInfo.put("category", photo.getCategory());
                        photoInfo.put("lowRes", photo.getLowRes());
                        photoInfo.put("highRes", photo.getHighRes());
                        photoInfo.put("fullPhoto", photo.getFullPhoto());
                        return photoInfo;
                    })

                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("photos", photosResponse);
            response.put("currentPage", photoPage.getNumber());
            response.put("totalPages", photoPage.getTotalPages());
            response.put("totalItems", photoPage.getTotalElements());

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error retrieving photos by category: {}", e.getMessage(), e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(path="/{categoryId}/{fileName}")
    public ResponseEntity<byte[]> getPhoto(@PathVariable UUID categoryId, @PathVariable String fileName) {
        GetObjectResponse response = null;
        try {
            response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(categoryId.toString())
                    .object(fileName).build());

            try (InputStream inputStream = response) {
                BufferedImage originalImage = ImageIO.read(inputStream);
                if (originalImage == null) {
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                String fileExtension = getFileExtension(fileName).orElse("jpg");
                ImageIO.write(originalImage, fileExtension, outputStream);
                byte[] imageBytes = outputStream.toByteArray();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_JPEG);
                return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);

            } catch (IOException e) {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    System.err.println("Error closing MinIO response: " + e.getMessage());
                }
            }
        }
    }

    @DeleteMapping(path="/delete/{photoId}")
    public ResponseEntity<Map<String, String>> deletePhoto(@PathVariable UUID photoId) {
        Map<String, String> response = new HashMap<>();

        Optional<Photo> photoOptional = repository.findById(photoId);
        if (photoOptional.isEmpty()) return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        Photo photo = photoOptional.get();

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(photo.getCategory().toString())
                            .object(photo.getLowRes())
                    .build());
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(photo.getCategory().toString())
                    .object(photo.getHighRes())
                    .build());
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(photo.getCategory().toString())
                    .object(photo.getFullPhoto())
                    .build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            response.put("minio_remove_error", e.getMessage());
        }

        repository.delete(photo);
        response.put("success", "true");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private Optional<String> getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return Optional.of(fileName.substring(dotIndex + 1).toLowerCase());
        }
        return Optional.empty();
    }

        public PhotosRepository getRepository() {
        return repository;
    }
}
