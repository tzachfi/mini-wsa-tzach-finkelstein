package com.akamai.miniwsa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocationEmbeddable {

    @Column(name = "geo_country")
    private String country;

    @Column(name = "geo_city")
    private String city;
}
