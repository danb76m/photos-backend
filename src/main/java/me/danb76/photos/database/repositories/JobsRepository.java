package me.danb76.photos.database.repositories;

import me.danb76.photos.database.tables.Category;
import me.danb76.photos.database.tables.Job;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JobsRepository extends CrudRepository<Job, UUID> {
}
