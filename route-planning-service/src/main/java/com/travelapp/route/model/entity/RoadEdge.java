package com.travelapp.route.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "road_edges")
@Getter
@Setter
@NoArgsConstructor
public class RoadEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private RoadNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private RoadNode toNode;

    @Column(name = "length_m", nullable = false)
    private Double lengthM;

    @Column(name = "walk_allowed", nullable = false)
    private Boolean walkAllowed = true;

    @Column(name = "car_allowed", nullable = false)
    private Boolean carAllowed = false;

    @Column(name = "mixed_allowed", nullable = false)
    private Boolean mixedAllowed = true;

    @Column(name = "public_transport_allowed", nullable = false)
    private Boolean publicTransportAllowed = true;

    @Column(name = "walk_time_sec")
    private Integer walkTimeSec;

    @Column(name = "car_time_sec")
    private Integer carTimeSec;

    @Column(name = "mixed_time_sec")
    private Integer mixedTimeSec;

    @Column(name = "public_transport_time_sec")
    private Integer publicTransportTimeSec;

    @Column(name = "bidirectional", nullable = false)
    private Boolean bidirectional = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "polyline_json", columnDefinition = "jsonb", nullable = false)
    private String polylineJson = "[]";

    @Column(name = "source", nullable = false, length = 30)
    private String source = "OSM";
}
