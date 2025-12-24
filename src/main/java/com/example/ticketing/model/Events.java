package com.example.ticketing.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Events {
    private String eventId;
    private String eventName;
    private Instant eventTime;
    private Instant saleStartTime;
    private Instant saleEndTime;
    private Instant saleOpenAt;
    private String venue;
    private String location;
    private String status;
    // PHONG_TOA | DANG_BAN  | KET_THUC


    public Events(String eventId, String eventName, Instant eventTime, Instant saleStartTime, Instant saleEndTime, Instant saleOpenAt, String status) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventTime = eventTime;
        this.saleStartTime = saleStartTime;
        this.saleEndTime = saleEndTime;
        this.saleOpenAt = saleOpenAt;
        this.status = status;
    }

    /** Đã vào giai đoạn xếp hàng chưa */
    public boolean isQueueOpen() {
        return "WAITING".equals(status) || "SELLING".equals(status);
    }

    /** Đã được phép bán chưa */
    public boolean isSelling() {
        return "SELLING".equals(status);
    }

    /** Đã kết thúc bán */
    public boolean isEnded() {
        return "ENDED".equals(status);
    }

    /** Event còn hiệu lực */
    public boolean isActive() {
        return !"ENDED".equals(status);
    }
}
