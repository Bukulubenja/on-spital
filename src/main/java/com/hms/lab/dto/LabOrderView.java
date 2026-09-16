package com.hms.lab.dto;

import java.util.List;

public record LabOrderView(String status, List<Item> items) {

    public record Item(Long itemId, String testName, boolean recorded, String resultValue) {
    }
}
