package com.weaveyourtrip.repository;

import com.weaveyourtrip.model.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Itinerary}. Standard CRUD is enough for
 * MVP — finer queries (by passport, by IP, etc.) land alongside the v1.2 admin
 * dashboard.
 */
@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {
}
