package com.saleh.enezi.manager;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnalyticsManagerTest {
    @Test public void stockValuationUsesRecordedCostOnly() {
        assertEquals(2500.0, AnalyticsManager.calculateStockValuation(10, 250), 0.001);
        assertEquals(0.0, AnalyticsManager.calculateStockValuation(10, 0), 0.001);
        assertEquals(0.0, AnalyticsManager.calculateStockValuation(-2, 250), 0.001);
    }

    @Test public void missingCostIsNotTreatedAsFreeGoods() {
        assertEquals(0.0, AnalyticsManager.calculateKnownCostProfit(5, 1000, 0), 0.001);
        assertEquals(250.0, AnalyticsManager.calculateKnownCostProfit(5, 1000, 150), 0.001);
    }

    @Test public void invalidCostIsNotRecorded() {
        assertFalse(AnalyticsManager.hasRecordedCost(0));
        assertFalse(AnalyticsManager.hasRecordedCost(-1));
        assertFalse(AnalyticsManager.hasRecordedCost(Double.NaN));
        assertTrue(AnalyticsManager.hasRecordedCost(0.01));
    }
}