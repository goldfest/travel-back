package com.travelapp.graphimport.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "road_nodes")
@Getter
@Setter
@NoArgsConstructor
public class RoadNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graph_version_id", nullable = false)
    private CityGraphVersion graphVersion;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @ColumnTransformer(read = "ST_AsText(geom)", write = "ST_GeomFromText(?, 4326)")
    @Column(name = "geom", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private String geom;

    public String getGeomWkt() {
        return geom;
    }

    public void setGeomWkt(String geomWkt) {
        this.geom = geomWkt;
    }
}
