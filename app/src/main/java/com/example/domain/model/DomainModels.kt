package com.example.domain.model

data class FinancialReportSummary(
    val periodTitle: String,
    val totalSales: Double,
    val totalPaid: Double,
    val totalRemainingDebt: Double,
    val invoiceCount: Int,
    val receiptsCount: Int,
    val netCashFlow: Double
)

data class MonthlySalesPoint(
    val monthName: String,
    val amount: Double
)

data class CustomerFinancialOverview(
    val customerName: String,
    val totalInvoiced: Double,
    val totalReceiptsPaid: Double,
    val netBalanceDue: Double,
    val lastTransactionDate: String
)

data class LowStockProduct(
    val productId: Long,
    val name: String,
    val currentStock: Double,
    val minStockAlert: Double,
    val unitPrice: Double
)
