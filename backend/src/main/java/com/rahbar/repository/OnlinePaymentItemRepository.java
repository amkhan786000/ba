package com.rahbar.repository;

import com.rahbar.entity.OnlinePaymentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OnlinePaymentItemRepository extends JpaRepository<OnlinePaymentItem, Long> {
    List<OnlinePaymentItem> findByOnlinePaymentId(Long onlinePaymentId);

    List<OnlinePaymentItem> findByOnlinePaymentIdIn(Collection<Long> onlinePaymentIds);

    Optional<OnlinePaymentItem> findFirstByPaymentId(Long paymentId);
}
