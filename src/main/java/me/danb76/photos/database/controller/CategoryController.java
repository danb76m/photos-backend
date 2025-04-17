package me.danb76.photos.database.controller;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import me.danb76.photos.database.repositories.CategoriesRepository;
import me.danb76.photos.database.tables.Category;
import me.danb76.photos.service.JobsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping(path = "/categories")
@RestController
public class CategoryController {
    private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);

    @Autowired
    private CategoriesRepository repository;

    @Autowired
    private MinioClient minioClient;

    @PostMapping(path="/create")
    public @ResponseBody ResponseEntity<Map<String, String>> addCategory(@RequestParam String name) {
        Map<String, String> response = new HashMap<>();

        Category category = new Category();
        category.setName(name);

        repository.save(category);

        try {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(category.getId().toString()).build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            response.put("error", e.getMessage());
            logger.error("Error creating category: {}", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        response.put("id", category.getId().toString());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PatchMapping(path = "/name")
    public @ResponseBody ResponseEntity<Map<String, String>> updateName(@RequestParam UUID id, @RequestParam String name) {
        return repository.findById(id)
                .map(category -> {
                    category.setName(name);
                    repository.save(category);
                    Map<String, String> response = new HashMap<>();
                    response.put("success", "true");
                    return new ResponseEntity<>(response, HttpStatus.OK);
                })
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PatchMapping(path = "/colour")
    public @ResponseBody boolean updateColour(@RequestParam UUID id, @RequestParam String colour) {
        if (!colour.matches("#[0-9A-Fa-f]{6}")) {
            return false;
        }

        return repository.findById(id)
                .map(category -> {
                    category.setColour(colour);
                    repository.save(category);
                    return true;
                })
                .orElse(false);
    }

    @DeleteMapping(path = "/delete")
    public @ResponseBody boolean requestDelete(@RequestParam UUID id) {
        return repository.findById(id)
                .map(category -> {
                    repository.delete(category);
                    return true;
                })
                .orElse(false);
    }

    @GetMapping(path="/all")
    public @ResponseBody Iterable<Category> getAllUsers() {
        return repository.findAll();
    }
}
