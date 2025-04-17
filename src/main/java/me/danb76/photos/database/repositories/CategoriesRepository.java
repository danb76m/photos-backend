package me.danb76.photos.database.repositories;

import me.danb76.photos.database.tables.Category;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CategoriesRepository extends CrudRepository<Category, UUID> {
}
