package com.favo.backend.Domain.user;

import com.favo.backend.Domain.interaction.ProductInteraction;
import com.favo.backend.Domain.interaction.ReviewInteraction;
import com.favo.backend.Domain.review.Review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("GENERAL_USER")
@Getter
@Setter
public class GeneralUser extends SystemUser {

    // GeneralUser'ın oluşturduğu review'lar (0..*)
    @OneToMany(mappedBy = "owner", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<Review> reviews = new ArrayList<>();

    // GeneralUser'ın yaptığı review interaction'ları (0..*)
    @OneToMany(mappedBy = "performer", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<ReviewInteraction> reviewInteractions = new ArrayList<>();

    // GeneralUser'ın yaptığı product interaction'ları (0..*)
    @OneToMany(mappedBy = "performer", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<ProductInteraction> productInteractions = new ArrayList<>();

    @Override
    public void deactivate() {
        setIsActive(false);
    }
}
