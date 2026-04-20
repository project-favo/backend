package com.favo.backend.Domain.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Activity bildiriminde gösterilecek ürün özeti.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationProductDto {
    private Long id;
    private String name;
    private String imageURL;
}
