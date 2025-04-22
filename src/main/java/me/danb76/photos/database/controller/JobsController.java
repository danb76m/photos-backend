package me.danb76.photos.database.controller;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import me.danb76.photos.database.repositories.CategoriesRepository;
import me.danb76.photos.database.repositories.JobsRepository;
import me.danb76.photos.database.tables.Category;
import me.danb76.photos.database.tables.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping(path = "/api/jobs/")
@RestController
public class JobsController {
    @Autowired
    private JobsRepository jobsRepository;

    @Autowired
    private CategoriesRepository categoriesRepository;

    @Autowired
    private MinioClient client;

    @PostMapping(path="/upload")
    public @ResponseBody ResponseEntity<Map<String, String>> postJob(@RequestParam String category, @RequestParam String fileName) {
        Map<String, String> response = new HashMap<>();
        UUID cat;

        try {
            cat = UUID.fromString(category);
        }catch (IllegalArgumentException ex) {
            response.put("error", "Category does not exist. (UUID invalid)");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        if (categoriesRepository.findById(cat).isEmpty()) {
            response.put("error", "Category does not exist. (Not found in database)");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(UploadController.UPLOADS_BUCKET)
                    .object(fileName).build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        Job job = new Job();
        job.setCategory(cat);
        job.setFileName(fileName);

        jobsRepository.save(job);

        response.put("success", job.getId().toString());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping(path="/all")
    public @ResponseBody Iterable<Job> getAllJobs() {
        return jobsRepository.findAll();
    }

}
