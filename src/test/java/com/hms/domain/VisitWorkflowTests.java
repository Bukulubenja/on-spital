package com.hms.domain;

import com.hms.entity.Visit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisitWorkflowTests {

    @Test
    void labOrdersTakePrecedenceOverPrescriptions() {
        assertThat(VisitWorkflow.afterConsultation(true, true)).isEqualTo(Visit.Status.WAITING_LAB);
    }

    @Test
    void routesToPharmacyWhenOnlyPrescriptionsExist() {
        assertThat(VisitWorkflow.afterConsultation(false, true)).isEqualTo(Visit.Status.WAITING_PHARMACY);
    }

    @Test
    void completesWhenNeitherLabOrdersNorPrescriptionsExist() {
        assertThat(VisitWorkflow.afterConsultation(false, false)).isEqualTo(Visit.Status.COMPLETED);
    }

    @Test
    void afterLabRoutesToPharmacyOnlyWhenPrescriptionsExist() {
        assertThat(VisitWorkflow.afterLab(true)).isEqualTo(Visit.Status.WAITING_PHARMACY);
        assertThat(VisitWorkflow.afterLab(false)).isEqualTo(Visit.Status.COMPLETED);
    }
}
