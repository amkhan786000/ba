package com.rahbar.repository;

import com.rahbar.entity.WebMessage;
import com.rahbar.repository.WebMessageRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebMessageRepository extends JpaRepository<WebMessage, Long>, JpaSpecificationExecutor<WebMessage> {
}
