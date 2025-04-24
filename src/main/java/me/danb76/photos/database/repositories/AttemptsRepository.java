package me.danb76.photos.database.repositories;

import me.danb76.photos.database.tables.Category;
import me.danb76.photos.database.tables.LoginAttempts;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttemptsRepository extends CrudRepository<LoginAttempts, UUID> {
    @Query("SELECT COUNT(la) FROM LoginAttempts la WHERE la.ip_addr = :ipAddr AND la.success = false AND la.timestamp > :timestamp")
    long countFailedAttempts(@Param("ipAddr") String ipAddr, @Param("timestamp") long timestamp);
}
