package me.danb76.photos.database.repositories;

import me.danb76.photos.database.tables.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PhotosRepository extends CrudRepository<Photo, UUID> {
    Page<Photo> findByCategory(UUID category, Pageable pageable);
}