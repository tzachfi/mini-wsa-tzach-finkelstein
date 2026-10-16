package com.akamai.miniwsa.repository;

import com.akamai.miniwsa.model.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long>, SecurityEventRepositoryCustom {}
