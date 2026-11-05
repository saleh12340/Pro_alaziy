package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel

@Composable
fun MoreHubScreen(
    viewModel: InvoiceViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToPrinter: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeuBackground)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Column {
            Text(
                text = "المزيد من الأدوات والخيارات",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = NeuTextPrimary
            )
            Text(
                text = "إدارة السجلات، الطابعات، إعدادات المتجر، والنسخ الاحتياطي",
                fontSize = 12.sp,
                color = NeuTextSecondary
            )
        }

        // Section: السجلات والمخزون
        Text(
            text = "السجلات والمخزون",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = NeuTextPrimary
        )

        MoreHubItem(
            title = "سجل الفواتير العام",
            subtitle = "عرض وتعديل وتصفية الفواتير السابقة وإعادة الطباعة",
            icon = AppIcons.History,
            iconTint = NeuAccentBlue,
            onClick = onNavigateToHistory
        )

        MoreHubItem(
            title = "الأصناف وإدارة المخزون",
            subtitle = "قائمة المنتجات، الأسعار الافتراضية، ومتابعة الكميات المتاحة",
            icon = AppIcons.Category,
            iconTint = NeuAccentBlue,
            onClick = onNavigateToProducts
        )

        // Section: الأجهزة والطباعة
        Text(
            text = "الأجهزة والطباعة",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = NeuTextPrimary
        )

        MoreHubItem(
            title = "إعداد الطابعة الحرارية",
            subtitle = "ربط واختبار طابعات البلوتوث ESC/POS (58 مم و 80 مم)",
            icon = AppIcons.Print,
            iconTint = Color(0xFF475569),
            onClick = onNavigateToPrinter
        )

        // Section: الإعدادات والأمان
        Text(
            text = "الإعدادات والأمان",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = NeuTextPrimary
        )

        MoreHubItem(
            title = "إعدادات المتجر والنسخ الاحتياطي",
            subtitle = "اسم المحل، أرقام التواصل، العملة، تصدير واستيراد البيانات",
            icon = Icons.Default.Settings,
            iconTint = Color(0xFF1E3A8A),
            onClick = onNavigateToSettings
        )

        // App Information Box
        NeuCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 1.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = NeuAccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "فواتيري — إصدار 1.0 (أوفلاين وآمن)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeuTextPrimary
                    )
                    Text(
                        text = "جاهز للامتثال المحاسبي، دعم كامل لـ RTL والطباعة الحرارية",
                        fontSize = 11.sp,
                        color = NeuTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MoreHubItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.RadiusM),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeuTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = NeuTextSecondary,
                    maxLines = 2
                )
            }
            Text(
                text = "‹",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = NeuTextMuted
            )
        }
    }
}
