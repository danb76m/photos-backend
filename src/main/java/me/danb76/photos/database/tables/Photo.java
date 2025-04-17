package me.danb76.photos.database.tables;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class Photo {
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private UUID id;

    private UUID category;

    private String lowRes;

    private String highRes;

    private String fullPhoto;

    public UUID getId() {
        return id;
    }

    public UUID getCategory() {
        return category;
    }

    public void setCategory(UUID category) {
        this.category = category;
    }

    public String getLowRes() {
        return lowRes;
    }

    public void setLowRes(String lowRes) {
        this.lowRes = lowRes;
    }

    public String getHighRes() {
        return highRes;
    }

    public void setHighRes(String highRes) {
        this.highRes = highRes;
    }

    public String getFullPhoto() {
        return fullPhoto;
    }

    public void setFullPhoto(String fullPhoto) {
        this.fullPhoto = fullPhoto;
    }
}
