package org.group10.notification.repository;

import org.group10.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<Notification> findByUserIdAndReadAtIsNull(Long userId);
}
