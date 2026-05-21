package com.cinetrack.watchlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WatchLogRepository extends JpaRepository<WatchLog, Long> {

    List<WatchLog> findByUserId(Long userId);
}
