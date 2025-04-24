package me.danb76.photos.service;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import me.danb76.photos.database.controller.PhotoController;
import me.danb76.photos.database.controller.UploadController;
import me.danb76.photos.database.repositories.JobsRepository;
import me.danb76.photos.database.tables.Category;
import me.danb76.photos.database.tables.Job;
import me.danb76.photos.database.tables.Photo;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.UUID;

@Service
public class JobsService {
    private static final Logger logger = LoggerFactory.getLogger(JobsService.class);
    private static final String THUMBNAIL_PREFIX_480 = "480_";
    private static final String THUMBNAIL_PREFIX_1080 = "1080_";
    private static final String ORIGINAL_PREFIX = "original_";
    private static final long JOB_EXPIRY_TIME_MS = 15 * 60 * 1000; // 15 minutes

    @Autowired
    private JobsRepository repository;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private PhotoController photoController;

    @Async
    @Scheduled(fixedRate = 60000)
    public void task() {
        logger.info("Looking for jobs to process.");
        Iterator<Job> iterator = repository.findAll().iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            logger.debug("Processing job: {}", job);
            iterator.remove();

            if (job.isProcessing()) {
                logger.info("Processing file: {} for category: {}", job.getFileName(), job.getCategory());
                try {
                    GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(UploadController.UPLOADS_BUCKET)
                            .object(job.getFileName()).build());

                    try (InputStream inputStream = response) {
                        BufferedImage originalImage = ImageIO.read(inputStream);
                        if (originalImage == null) {
                            logger.warn("Failed to read image file: {}", job.getFileName());
                            updateJobStatus(job, false, false, "Failed to read image file.");
                            continue;
                        }

                        String lowRes = generateImageThumbnail(job.getCategory(), originalImage, THUMBNAIL_PREFIX_480 + job.getFileName(), 480);
                        String highRes = generateImageThumbnail(job.getCategory(), originalImage, THUMBNAIL_PREFIX_1080 + job.getFileName(), 1080);
                        String fullPhoto = ORIGINAL_PREFIX + job.getFileName();

                        minioClient.copyObject(CopyObjectArgs.builder()
                                .source(CopySource.builder()
                                        .bucket(UploadController.UPLOADS_BUCKET)
                                        .object(job.getFileName()).build())
                                .bucket(job.getCategory().toString())
                                .object(fullPhoto).build());

                        Photo photo = new Photo();
                        photo.setLowRes(lowRes);
                        photo.setHighRes(highRes);
                        photo.setFullPhoto(fullPhoto);
                        photo.setCategory(job.getCategory());

                        photoController.getRepository().save(photo);

                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(UploadController.UPLOADS_BUCKET)
                                .object(job.getFileName()).build());

                        updateJobStatus(job, false, true, "Generated all thumbnails and copied original.");
                        logger.info("Successfully processed file: {} for category: {}", job.getFileName(), job.getCategory());

                        // TODO post to photos repository.
                        logger.debug("TODO: Post processed photo metadata to photos repository for file: {}", job.getFileName());

                    } catch (IOException e) {
                        logger.error("IO Exception while processing file: {}", job.getFileName(), e);
                        updateJobStatus(job, false, false, "IO Error: " + e.getMessage());
                    }
                } catch (ErrorResponseException e) {
                    logger.error("MinIO Error Response Exception while getting file: {}", job.getFileName(), e);
                    updateJobStatus(job, false, false, "MinIO Error: " + e.getMessage());
                } catch (InsufficientDataException e) {
                    logger.error("MinIO Insufficient Data Exception while getting file: {}", job.getFileName(), e);
                    updateJobStatus(job, false, false, "MinIO Error: " + e.getMessage());
                } catch (InternalException e) {
                    logger.error("MinIO Internal Exception while getting file: {}", job.getFileName(), e);
                    updateJobStatus(job, false, false, "MinIO Error: " + e.getMessage());
                } catch (InvalidKeyException e) {
                    logger.error("MinIO Invalid Key Exception while getting file: {}", job.getFileName(), e);
                    updateJobStatus(job, false, false, "MinIO Error: " + e.getMessage());
                } catch (InvalidResponseException e) {
                    logger.error("MinIO Invalid Response Exception while getting file: {}", job.getFileName(), e);
                    updateJobStatus(job, false, false, "MinIO Error: " + e.getMessage());
                } catch (NoSuchAlgorithmException e) {
                    logger.error("No Such Algorithm Exception while getting file: {}", job.getFileName(), e);
                    updateJobStatus(job, false, false, "Security Error: " + e.getMessage());
                } catch (ServerException e) {
                    logger.error("MinIO Server Exception while getting file: {}", job.getFileName(), e);
                    updateJobStatus(job, false, false, "MinIO Error: " + e.getMessage());
                } catch (XmlParserException e) {
                    logger.error("MinIO XML Parser Exception while getting file: {}", job.getFileName(), e);
                    updateJobStatus(job, false, false, "MinIO Error: " + e.getMessage());
                } catch (IOException e) {
                    logger.error("MinIO IO Exception while getting file: {}", job.getFileName(), e);
                    updateJobStatus(job, false, false, "MinIO Error: " + e.getMessage());
                }
            } else {
                // Delete after 15 minutes.
                // MinIO handles deleting from upload bucket.
                // TODO notify user.
                long jobAge = System.currentTimeMillis() - job.getTimestamp();
                if (jobAge > JOB_EXPIRY_TIME_MS) {
                    logger.warn("Deleting expired job: {} for file: {}", job, job.getFileName());
                    repository.delete(job);
                    logger.debug("TODO: Notify user about the failed/expired upload: {}", job.getFileName());
                } else {
                    logger.debug("Job for file {} is not yet expired ({}ms remaining).", job.getFileName(), JOB_EXPIRY_TIME_MS - jobAge);
                }
            }
        }
        logger.info("Finished processing jobs for this cycle.");
    }

    private void updateJobStatus(Job job, boolean processing, boolean success, String reason) {
        job.setProcessing(processing);
        job.setSuccess(success);
        job.setReason(reason);
        repository.save(job);
        logger.debug("Updated job status for file {}: Processing={}, Success={}, Reason='{}'", job.getFileName(), processing, success, reason);
    }

    private String generateImageThumbnail(UUID categoryId, BufferedImage originalImage, String outputFileName, int maxHeight) {
        logger.debug("Generating thumbnail: {} for category: {} with max height: {}", outputFileName, categoryId, maxHeight);
        try {
            BufferedImage scaledImage;
                int originalWidth = originalImage.getWidth();
                int originalHeight = originalImage.getHeight();
                int newWidth = originalWidth;

                if (originalHeight > maxHeight) {
                    newWidth = (int) Math.round((double) maxHeight / originalHeight * originalWidth);
                }
                scaledImage = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_HEIGHT, newWidth, maxHeight);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(scaledImage, "jpg", outputStream);
            byte[] thumbnailBytes = outputStream.toByteArray();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(categoryId.toString())
                    .object(outputFileName)
                    .stream(new ByteArrayInputStream(thumbnailBytes), thumbnailBytes.length, -1)
                    .contentType("image/jpeg")
                    .build());

            outputStream.close();

            logger.debug("Successfully generated and uploaded thumbnail: {} to category: {}", outputFileName, categoryId);

        } catch (IOException e) {
            logger.error("IO Exception while generating thumbnail: {}", outputFileName, e);
        } catch (ServerException e) {
            logger.error("MinIO Server Exception while uploading thumbnail: {}", outputFileName, e);
        } catch (InsufficientDataException e) {
            logger.error("MinIO Insufficient Data Exception while uploading thumbnail: {}", outputFileName, e);
        } catch (ErrorResponseException e) {
            logger.error("MinIO Error Response Exception while uploading thumbnail: {}", outputFileName, e);
        } catch (NoSuchAlgorithmException e) {
            logger.error("No Such Algorithm Exception while uploading thumbnail: {}", outputFileName, e);
        } catch (InvalidKeyException e) {
            logger.error("MinIO Invalid Key Exception while uploading thumbnail: {}", outputFileName, e);
        } catch (InvalidResponseException e) {
            logger.error("MinIO Invalid Response Exception while uploading thumbnail: {}", outputFileName, e);
        } catch (XmlParserException e) {
            logger.error("MinIO XML Parser Exception while uploading thumbnail: {}", outputFileName, e);
        } catch (InternalException e) {
            logger.error("MinIO Internal Exception while uploading thumbnail: {}", outputFileName, e);
        }

        return outputFileName;
    }
}