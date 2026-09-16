package com.hms.reception.dto;

import com.hms.entity.QueueTicket;

public record QueueTicketResponse(int queueNumber, String patientName, String doctorUsername, boolean served) {

    public static QueueTicketResponse from(QueueTicket ticket) {
        var visit = ticket.getVisit();
        return new QueueTicketResponse(
                ticket.getQueueNumber(),
                visit.getPatient().getFullName(),
                visit.getDoctor() == null ? null : visit.getDoctor().getUsername(),
                ticket.isServed()
        );
    }
}
