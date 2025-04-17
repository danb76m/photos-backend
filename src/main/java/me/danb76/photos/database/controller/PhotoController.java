package me.danb76.photos.database.controller;

import me.danb76.photos.database.repositories.PhotosRepository;
import me.danb76.photos.database.tables.Photo;
import me.danb76.photos.service.JobsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping(path = "/photos")
@RestController
public class PhotoController {
    private static final Logger logger = LoggerFactory.getLogger(JobsService.class);

    @Autowired
    private PhotosRepository repository;

    @GetMapping("/category/{categoryId}")
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

    public PhotosRepository getRepository() {
        return repository;
    }
}
