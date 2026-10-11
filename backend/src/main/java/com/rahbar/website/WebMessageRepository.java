package com.rahbar.website;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebMessageRepository extends JpaRepository<WebMessage, Long>, JpaSpecificationExecutor<WebMessage> {
}
