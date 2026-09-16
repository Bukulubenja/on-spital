package com.hms.reception.dto;

public record CheckInResponse(Long visitId, int queueNumber, String status) {
}
