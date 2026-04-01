package com.favo.backend.Domain.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String reply;
    /** İstemcide yatay liste / kart olarak gösterilecek gerçek ürünler (sunucu seçimi). */
    private List<ChatProductCardDto> products = new ArrayList<>();
}
