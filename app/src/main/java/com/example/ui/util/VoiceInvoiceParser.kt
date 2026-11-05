package com.example.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

sealed class VoiceCommand {
    data class Add(val items: List<ParsedVoiceItem>) : VoiceCommand()
    data class DeleteItem(val query: String, val index: Int? = null) : VoiceCommand()
    data class AddCustomer(val customerName: String) : VoiceCommand()
    data class AddProduct(val productName: String, val price: Double) : VoiceCommand()
    object ClearInvoice : VoiceCommand()
    object Unknown : VoiceCommand()
}

data class ParsedVoiceItem(
    val description: String,
    val quantity: String,
    val unitPrice: String = "",
    val totalAmount: String,
    val originalSegment: String = ""
)

object VoiceInvoiceParser {

    fun parseVoiceCommand(text: String): VoiceCommand {
        val cleanText = text.trim()
        val lower = cleanText.lowercase()

        // 1. Check clear invoice
        if (lower.contains("مسح الفاتورة") || lower.contains("حذف الفاتورة") || lower.contains("إلغاء الفاتورة") || lower.contains("تفريغ الفاتورة") || lower.contains("مسح الكل") || lower.contains("الغاء الفاتوره")) {
            return VoiceCommand.ClearInvoice
        }

        // 2. Check Add Customer: e.g. "أضف عميل اسمه أحمد سالم" or "اضف عميل محمد"
        val customerPrefixes = listOf("أضف عميل", "اضف عميل", "عميل جديد", "سجل عميل", "العميل")
        for (cp in customerPrefixes) {
            if (lower.startsWith(cp)) {
                var cName = cleanText.substring(cp.length).trim()
                if (cName.startsWith("اسمه")) cName = cName.substring(4).trim()
                if (cName.startsWith("اسم:")) cName = cName.substring(4).trim()
                if (cName.isNotBlank()) {
                    return VoiceCommand.AddCustomer(cName)
                }
            }
        }

        // 3. Check Add Product: e.g. "أضف صنف قلم بسعر 500" or "اضف صنف حليب 400"
        val productPrefixes = listOf("أضف صنف", "اضف صنف", "صنف جديد", "سجل صنف")
        for (pp in productPrefixes) {
            if (lower.startsWith(pp)) {
                val remainder = cleanText.substring(pp.length).trim()
                // extract price if mentioned: "بسعر 500" or number at the end
                val words = remainder.split("\\s+".toRegex())
                var price = 0.0
                val nameWords = mutableListOf<String>()
                for (w in words) {
                    val num = w.toEnglishDigits().toDoubleOrNull()
                    if (num != null && num > 0) {
                        price = num
                    } else if (w != "بسعر" && w != "سعر" && w != "ريال" && w != "ر.ي") {
                        nameWords.add(w)
                    }
                }
                val pName = nameWords.joinToString(" ").trim()
                if (pName.isNotBlank()) {
                    return VoiceCommand.AddProduct(pName, price)
                }
            }
        }

        // 4. Check deletion / cancellation of item
        val deletePrefixes = listOf("احذف", "حذف", "الغاء", "إلغاء", "مسح", "شيل", "ارفع", "امسح", "كنسل")
        for (prefix in deletePrefixes) {
            if (lower.startsWith(prefix) || lower.contains(" $prefix ")) {
                var remainder = lower.replace(prefix, "").trim()
                
                // Ordinal dictionary
                val ordinalMap = mapOf(
                    "الاول" to 0, "الأول" to 0, "اول" to 0, "أول" to 0,
                    "الثاني" to 1, "التاني" to 1, "ثاني" to 1, "تاني" to 1,
                    "الثالث" to 2, "التالت" to 2, "ثالث" to 2, "تالت" to 2,
                    "الرابع" to 3, "رابع" to 3,
                    "الخامس" to 4, "خامس" to 4,
                    "السادس" to 5, "سادس" to 5,
                    "السابع" to 6, "سابع" to 6,
                    "الثامن" to 7, "ثامن" to 7,
                    "التاسع" to 8, "تاسع" to 8,
                    "العاشر" to 9, "عاشر" to 9,
                    "الاخير" to -1, "الأخير" to -1, "اخر" to -1, "آخر" to -1
                )

                var targetIndex: Int? = null
                val words = remainder.split("\\s+".toRegex()).filter { it.isNotBlank() }

                for (w in words) {
                    if (ordinalMap.containsKey(w)) {
                        targetIndex = ordinalMap[w]
                        break
                    }
                    val num = w.toEnglishDigits().toDoubleOrNull()
                    if (num != null && num in 1.0..50.0) {
                        targetIndex = num.toInt() - 1
                        break
                    }
                }

                // Strip filler words to get clean item search query
                val fillerWords = setOf("صنف", "الصنف", "عنصر", "العنصر", "واحد", "الواحد", "سطر", "السطر", "رقم", "من", "الفاتورة", "الفاتوره")
                val cleanQueryWords = words.filter { !fillerWords.contains(it) && !ordinalMap.containsKey(it) && it.toEnglishDigits().toDoubleOrNull() == null }
                val query = cleanQueryWords.joinToString(" ").trim()

                return VoiceCommand.DeleteItem(query = query, index = targetIndex)
            }
        }

        // 5. Otherwise parse as add invoice items
        val items = parseVoiceInput(cleanText)
        if (items.isNotEmpty()) {
            return VoiceCommand.Add(items)
        }

        return VoiceCommand.Unknown
    }


    private val commonIgnoredPrefixWords = setOf(
        "سجل", "اكتب", "ضيف", "أضف", "اضف", "فاتورة", "فاتوره", "حط", "صنف", "البيان",
        "يا", "طيب", "معلم", "ياطيب", "لو", "سمحت", "عندك", "قيد", "حساب", "جديد", "جديدة"
    )

    private val commonCurrencyWords = setOf(
        "ريال", "ريالات", "ريالا", "ر.ي", "فلوس", "يمني", "سعودي", "دولار", "دولارات",
        "جنيه", "جنيهات", "دينار", "دنانير", "درهم", "دراهم", "شيكل", "ليرة"
    )

    // Dual units indicating quantity = 2
    private val dualUnitMap = mapOf(
        "حبتين" to ("حبة" to 2.0),
        "حبتان" to ("حبة" to 2.0),
        "كرتونين" to ("كرتون" to 2.0),
        "كرتونان" to ("كرتون" to 2.0),
        "كيسين" to ("كيس" to 2.0),
        "كيسان" to ("كيس" to 2.0),
        "علبتين" to ("علبة" to 2.0),
        "علبتان" to ("علبة" to 2.0),
        "قطعتين" to ("قطعة" to 2.0),
        "قطعتان" to ("قطعة" to 2.0),
        "باكتين" to ("باكت" to 2.0),
        "باكتان" to ("باكت" to 2.0),
        "بكتين" to ("باكت" to 2.0),
        "كيلوين" to ("كيلو" to 2.0),
        "كيلوان" to ("كيلو" to 2.0),
        "درزنين" to ("درزن" to 2.0),
        "درزنان" to ("درزن" to 2.0),
        "صحنين" to ("صحن" to 2.0),
        "صحنان" to ("صحن" to 2.0),
        "قلمين" to ("قلم" to 2.0),
        "طردين" to ("طرد" to 2.0),
        "دبتين" to ("دبة" to 2.0),
        "قارورتين" to ("قارورة" to 2.0),
        "بطلين" to ("بطل" to 2.0),
        "شدتين" to ("شدة" to 2.0)
    )

    // Single / plural packaging units indicating quantity context
    private val quantityUnitWords = setOf(
        "حبة", "حبه", "حبات",
        "كرتون", "كراتين", "كرواتين",
        "كيس", "أكياس", "اكياس",
        "علبة", "علبه", "علب",
        "قطعة", "قطعه", "قطع",
        "باكت", "بكت", "باكتات",
        "كيلو", "كيلوات", "كيلوغرام", "كيلوجرام",
        "لتر", "لترات",
        "درزن", "درازن",
        "قرص", "أقراص", "اقراص", "ربطة", "باقة",
        "صحن", "صحون", "طرد", "طرود", "دبة", "دباب", "قارورة", "قوارير", "شدة"
    )

    // Number word primitives
    private val unitsMap = mapOf(
        "صفر" to 0.0,
        "واحد" to 1.0, "واحده" to 1.0, "واحدة" to 1.0, "وحده" to 1.0,
        "اثنين" to 2.0, "اثنان" to 2.0, "اثنتين" to 2.0, "اثنتان" to 2.0, "تنين" to 2.0, "اتنين" to 2.0,
        "ثلاثه" to 3.0, "ثلاثة" to 3.0, "ثلاث" to 3.0, "تلاتة" to 3.0, "تلات" to 3.0, "تلاته" to 3.0,
        "اربعه" to 4.0, "اربعة" to 4.0, "أربعة" to 4.0, "أربع" to 4.0, "اربع" to 4.0, "أربعه" to 4.0,
        "خمسه" to 5.0, "خمسة" to 5.0, "خمس" to 5.0,
        "سته" to 6.0, "ستة" to 6.0, "ست" to 6.0,
        "سبعه" to 7.0, "سبعة" to 7.0, "سبع" to 7.0,
        "ثمانيه" to 8.0, "ثمانية" to 8.0, "ثمان" to 8.0, "تمانية" to 8.0, "تمن" to 8.0, "تمانيه" to 8.0,
        "تسعه" to 9.0, "تسعة" to 9.0, "تسع" to 9.0
    )

    private val teensMap = mapOf(
        "عشره" to 10.0, "عشرة" to 10.0, "عشر" to 10.0,
        "احداعش" to 11.0, "حداش" to 11.0, "احد عشر" to 11.0, "أحد عشر" to 11.0,
        "اثناعش" to 12.0, "اثنعش" to 12.0, "اطنعش" to 12.0, "اثنا عشر" to 12.0, "اثني عشر" to 12.0,
        "ثلاثتعش" to 13.0, "تلاتاش" to 13.0, "تلاتطعش" to 13.0, "ثلاثة عشر" to 13.0,
        "اربعتاعش" to 14.0, "اربعطعش" to 14.0, "أربعة عشر" to 14.0, "اربعة عشر" to 14.0,
        "خمستاعش" to 15.0, "خمسطعش" to 15.0, "خمسة عشر" to 15.0,
        "ستتعش" to 16.0, "ستطعش" to 16.0, "ستة عشر" to 16.0,
        "سبعتاعش" to 17.0, "سبعطعش" to 17.0, "سبعة عشر" to 17.0,
        "تمنتاعش" to 18.0, "تمنطعش" to 18.0, "ثمانية عشر" to 18.0,
        "تسعتاعش" to 19.0, "تسعطعش" to 19.0, "تسعة عشر" to 19.0
    )

    private val tensMap = mapOf(
        "عشرين" to 20.0, "عشرون" to 20.0,
        "ثلاثين" to 30.0, "ثلاثون" to 30.0, "تلاتين" to 30.0,
        "اربعين" to 40.0, "أربعين" to 40.0, "أربعون" to 40.0, "اربعون" to 40.0,
        "خمسين" to 50.0, "خمسون" to 50.0,
        "ستين" to 60.0, "ستون" to 60.0,
        "سبعين" to 70.0, "سبعون" to 70.0,
        "ثمانين" to 80.0, "ثمانون" to 80.0, "تمانين" to 80.0, "تمانون" to 80.0,
        "تسعين" to 90.0, "تسعون" to 90.0
    )

    private val hundredsMap = mapOf(
        "ميه" to 100.0, "مية" to 100.0, "مئه" to 100.0, "مئة" to 100.0, "مائة" to 100.0, "مائه" to 100.0,
        "مائتين" to 200.0, "مئتين" to 200.0, "مائتان" to 200.0, "مئتان" to 200.0, "ميتين" to 200.0,
        "ثلاثمائة" to 300.0, "ثلاثمئة" to 300.0, "ثلاثميه" to 300.0, "تلاتمية" to 300.0, "تلاتميه" to 300.0,
        "أربعمائة" to 400.0, "اربعمائة" to 400.0, "أربعمئة" to 400.0, "اربعميه" to 400.0, "أربعميه" to 400.0, "اربعمئة" to 400.0,
        "خمسمائة" to 500.0, "خمسمئة" to 500.0, "خمسميه" to 500.0,
        "ستمائة" to 600.0, "ستمئة" to 600.0, "ستميه" to 600.0,
        "سبعمائة" to 700.0, "سبعمئة" to 700.0, "سبعميه" to 700.0,
        "ثمانمائة" to 800.0, "ثمانمئة" to 800.0, "تمنميه" to 800.0, "تمانميه" to 800.0, "ثمانميه" to 800.0,
        "تسعمائة" to 900.0, "تسعمئة" to 900.0, "تسعميه" to 900.0
    )

    private val thousandsMap = mapOf(
        "الف" to 1000.0, "ألف" to 1000.0, "آلاف" to 1000.0, "الاف" to 1000.0,
        "الفين" to 2000.0, "ألفين" to 2000.0, "الفان" to 2000.0, "ألفان" to 2000.0
    )

    private val millionsMap = mapOf(
        "مليون" to 1000000.0, "ملايين" to 1000000.0,
        "مليونين" to 2000000.0, "مليونان" to 2000000.0
    )

    fun isCoreNumberWord(word: String): Boolean {
        val clean = cleanWord(word)
        return clean.toDoubleOrNull() != null ||
                unitsMap.containsKey(clean) ||
                teensMap.containsKey(clean) ||
                tensMap.containsKey(clean) ||
                hundredsMap.containsKey(clean) ||
                thousandsMap.containsKey(clean) ||
                millionsMap.containsKey(clean)
    }

    private fun isNumberWord(word: String): Boolean {
        val clean = cleanWord(word)
        if (isCoreNumberWord(clean)) return true
        if (clean.startsWith("و") && clean.length > 1 && isCoreNumberWord(clean.substring(1))) return true
        if (clean.startsWith("ب") && clean.length > 1 && isCoreNumberWord(clean.substring(1))) return true
        if (clean.startsWith("وب") && clean.length > 2 && isCoreNumberWord(clean.substring(2))) return true
        return false
    }

    private fun cleanWord(word: String): String {
        return word.trim()
            .trim(',', '،', '.', ':', '؟', ';', '؛')
            .replace("ـ", "") // Remove tatweel
            .replace(Regex("[\u064B-\u065F]"), "") // Remove diacritics
            .toEnglishDigits()
    }

    /**
     * Normalizes raw voice text, removing digit-internal commas (e.g. 4,000 or 1,000,000)
     * so that numbers aren't shattered by punctuation.
     */
    fun normalizeVoiceText(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw.trim().toEnglishDigits()

        // 1. Remove thousands commas / Arabic commas inside numbers: e.g. "4,000" or "٤،٠٠٠"
        while (text.contains(Regex("""(\d+)[,،٬](\d{3})\b"""))) {
            text = text.replace(Regex("""(\d+)[,،٬](\d{3})\b"""), "$1$2")
        }
        text = text.replace(Regex("""(?<=\d)[,،٬](?=\d)"""), "")

        // 2. Remove Tatweel and Arabic diacritics
        text = text.replace("ـ", "")
            .replace(Regex("[\u064B-\u065F]"), "")

        // 3. Normalize explicit multi-item transitions
        text = text.replace("\n", " | ")
            .replace(" ثم ", " | ")
            .replace(" وبعده ", " | ")
            .replace(" وبعدين ", " | ")
            .replace(" وكمان ", " | ")
            .replace(" كمان ", " | ")

        return text
    }

    /**
     * Splits multi-item spoken input into individual item segments.
     * Prevents false splits on commas inside numbers or inside compound number phrases (e.g. "خمسة وعشرين").
     */
    fun splitIntoSegments(input: String): List<String> {
        val normalized = normalizeVoiceText(input)
        if (normalized.isBlank()) return emptyList()

        // Step 1: Split by explicit pipe symbols
        val rawChunks = normalized.split("|").map { it.trim() }.filter { it.isNotBlank() }
        val resultSegments = mutableListOf<String>()

        for (chunk in rawChunks) {
            val subSegments = splitChunkByItemBoundaries(chunk)
            resultSegments.addAll(subSegments)
        }

        return if (resultSegments.isNotEmpty()) resultSegments else listOf(normalized)
    }

    /**
     * Inspects tokens in a single chunk to detect multi-item boundaries (like conjunction 'و' or comma).
     */
    private fun splitChunkByItemBoundaries(chunk: String): List<String> {
        val tokens = chunk.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val nonSplittingAndNumbers = setOf(
            "عشرين", "عشرون", "ثلاثين", "ثلاثون", "اربعين", "أربعين", "خمسين", "ستين", "سبعين", "ثمانين", "تسعين",
            "مية", "مئة", "مائة", "مئتين", "خمسمية", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة",
            "الف", "ألف", "آلاف", "الاف", "الفين", "ألفين", "نصف", "نص", "ربع", "واحد", "اثنين", "اثنان"
        )

        val sb = StringBuilder()
        var hasSeenItemContentInCurrentSegment = false

        for (i in tokens.indices) {
            val t = tokens[i]
            val cleanT = cleanWord(t)

            // Check if token is a comma-delimited item separator
            if ((t.endsWith("،") || t.endsWith(",")) && i + 1 < tokens.size) {
                val nextClean = cleanWord(tokens[i + 1])
                val isNextANewItem = isNextItemStart(tokens, i + 1)
                if (hasSeenItemContentInCurrentSegment && isNextANewItem) {
                    sb.append(cleanT).append(" | ")
                    hasSeenItemContentInCurrentSegment = false
                    continue
                }
            }

            // Check if token is conjunction 'و'
            if (t == "و" || (t.startsWith("و") && t.length > 1 && !isNumberWord(t.substring(1)))) {
                val nextWord = if (i + 1 < tokens.size) cleanWord(tokens[i + 1]) else ""
                val isCompoundNumber = nextWord in nonSplittingAndNumbers
                val isNextANewItem = isNextItemStart(tokens, i + 1)

                if (!isCompoundNumber && isNextANewItem && hasSeenItemContentInCurrentSegment) {
                    sb.append(" | ")
                    if (t.startsWith("و") && t.length > 1) {
                        sb.append(t.substring(1)).append(" ")
                    }
                    hasSeenItemContentInCurrentSegment = false
                    continue
                }
            }

            if (isCoreNumberWord(cleanT) || cleanT.length > 1) {
                hasSeenItemContentInCurrentSegment = true
            }

            sb.append(t).append(" ")
        }

        return sb.toString().split("|")
            .map { it.trim().trim(',', '،') }
            .filter { it.isNotBlank() }
    }

    private fun isNextItemStart(tokens: List<String>, startIndex: Int): Boolean {
        if (startIndex !in tokens.indices) return false
        val word = cleanWord(tokens[startIndex])
        if (word.toDoubleOrNull() != null) return true
        if (dualUnitMap.containsKey(word)) return true
        if (unitsMap.containsKey(word)) return true
        if (quantityUnitWords.contains(word)) return true

        for (j in startIndex until minOf(tokens.size, startIndex + 4)) {
            val w = cleanWord(tokens[j])
            if (w.toDoubleOrNull() != null || w in thousandsMap || w in hundredsMap || dualUnitMap.containsKey(w)) {
                return true
            }
        }
        return false
    }

    /**
     * Parses the full spoken text into structured invoice items.
     */
    fun parseVoiceInput(spokenText: String): List<ParsedVoiceItem> {
        val segments = splitIntoSegments(spokenText)
        return segments.mapNotNull { segment ->
            parseSingleSegment(segment)
        }
    }

    data class ExtractedNumber(
        val value: Double,
        val tokenIndices: Set<Int>,
        val isUnitPrice: Boolean = false,
        val isExplicitTotal: Boolean = false,
        val isExplicitQuantity: Boolean = false,
        val associatedUnit: String? = null
    )

    /**
     * Parses a single spoken segment:
     * - Pattern A: [Total] [Item Description] [Quantity] (e.g. "4000 كرتون عصير 2" or "1000 تونة 2")
     * - Pattern B: [Quantity] [Item Description] [Total] (e.g. "2 كرتون عصير 4000" or "2 تونة 1000")
     * - Pattern C: [Item] [Total] (e.g. "500 بيبسي" or "بيبسي 500" -> quantity defaults to 1)
     * - Pattern D: [Dual Unit] [Item] [Total] (e.g. "حبتين تونة 1000" or "1000 كرتونين عصير")
     */
    fun parseSingleSegment(segment: String): ParsedVoiceItem? {
        val raw = segment.trim().toEnglishDigits()
        if (raw.isBlank()) return null

        val rawTokens = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return null

        // 1. Filter out introductory conversational prefix words
        val tokens = mutableListOf<String>()
        var foundContent = false
        for (token in rawTokens) {
            val clean = cleanWord(token)
            if (!foundContent && clean in commonIgnoredPrefixWords) {
                continue
            }
            foundContent = true
            tokens.add(token)
        }

        if (tokens.isEmpty()) return null

        // 2. Scan for dual units (حبتين, كرتونين, كيسين, إلخ) which declare quantity = 2
        var dualExtracted: ExtractedNumber? = null
        val dualIndices = mutableSetOf<Int>()
        for (i in tokens.indices) {
            val clean = cleanWord(tokens[i])
            val dualInfo = dualUnitMap[clean]
            if (dualInfo != null) {
                dualExtracted = ExtractedNumber(
                    value = dualInfo.second,
                    tokenIndices = setOf(i),
                    isExplicitQuantity = true,
                    associatedUnit = dualInfo.first
                )
                dualIndices.add(i)
                break
            }
        }

        // 3. Extract all composite number phrases from tokens
        val extractedNumbers = extractAllNumberPhrases(tokens, dualIndices)
        val allNumbers = mutableListOf<ExtractedNumber>()
        if (dualExtracted != null) {
            allNumbers.add(dualExtracted)
        }
        allNumbers.addAll(extractedNumbers)

        // 4. Assign Quantity, Price, and Total based on merchant speech patterns
        var quantity = 1.0
        var unitPrice = 0.0
        var totalAmount = 0.0
        val usedNumberTokenIndices = mutableSetOf<Int>()

        if (allNumbers.size >= 2) {
            val unitPriceCandidate = allNumbers.find { it.isUnitPrice }
            val explicitTotalCandidate = allNumbers.find { it.isExplicitTotal }

            if (unitPriceCandidate != null) {
                val other = allNumbers.first { it != unitPriceCandidate }
                unitPrice = unitPriceCandidate.value
                quantity = other.value
                totalAmount = unitPrice * quantity
                usedNumberTokenIndices.addAll(unitPriceCandidate.tokenIndices)
                usedNumberTokenIndices.addAll(other.tokenIndices)
            } else if (explicitTotalCandidate != null) {
                val other = allNumbers.first { it != explicitTotalCandidate }
                totalAmount = explicitTotalCandidate.value
                quantity = other.value
                unitPrice = if (quantity > 0) totalAmount / quantity else totalAmount
                usedNumberTokenIndices.addAll(explicitTotalCandidate.tokenIndices)
                usedNumberTokenIndices.addAll(other.tokenIndices)
            } else {
                // Universal retail rule: in almost all sales, Total is larger than Quantity!
                // e.g. "4000 كرتون عصير 2" -> Total=4000, Qty=2
                // e.g. "2 كرتون عصير 4000" -> Total=4000, Qty=2
                // e.g. "1000 تونة 2" -> Total=1000, Qty=2
                // e.g. "50 علكة 5" -> Total=50, Qty=5
                val first = allNumbers.first()
                val last = allNumbers.last()

                if (first.value != last.value) {
                    totalAmount = maxOf(first.value, last.value)
                    quantity = minOf(first.value, last.value)
                    unitPrice = if (quantity > 0) totalAmount / quantity else totalAmount
                } else {
                    totalAmount = first.value
                    quantity = last.value
                    unitPrice = 1.0
                }
                usedNumberTokenIndices.addAll(first.tokenIndices)
                usedNumberTokenIndices.addAll(last.tokenIndices)
            }
        } else if (allNumbers.size == 1) {
            val single = allNumbers.first()
            usedNumberTokenIndices.addAll(single.tokenIndices)

            if (single.isUnitPrice) {
                unitPrice = single.value
                quantity = 1.0
                totalAmount = unitPrice * quantity
            } else if (single.isExplicitQuantity) {
                quantity = single.value
                totalAmount = 0.0
            } else {
                // By default in merchant speech, single number is the item's price/total
                // e.g. "500 بيبسي" or "بيبسي 500" or "4000 كرتون عصير"
                totalAmount = single.value
                quantity = 1.0
                unitPrice = totalAmount
            }
        }

        // 5. Extract Item Description from remaining words
        val descriptionWords = mutableListOf<String>()
        for (i in tokens.indices) {
            if (i in usedNumberTokenIndices) continue
            val word = cleanWord(tokens[i])
            if (word in commonCurrencyWords) continue
            if (word in setOf(
                    "بسعر", "سعر", "سعرها", "الحبة", "الحبه", "الواحدة", "الواحده", "ب", "للحبة", "للقطعة",
                    "إجمالي", "اجمالي", "بإجمالي", "مجموع", "جملة", "عدد", "كمية", "بكمية"
                )
            ) continue
            descriptionWords.add(cleanWord(tokens[i]))
        }

        var desc = descriptionWords.joinToString(" ").trim()
        if (desc.isBlank()) {
            desc = if (dualExtracted?.associatedUnit != null) {
                dualExtracted.associatedUnit
            } else {
                "صنف"
            }
        }

        // Format clean numbers
        val qtyStr = formatCleanNumber(quantity).ifBlank { "1" }
        val upStr = if (unitPrice > 0.0) formatCleanNumber(unitPrice) else ""
        val totStr = if (totalAmount > 0.0) formatCleanNumber(totalAmount) else ""

        return ParsedVoiceItem(
            description = desc,
            quantity = qtyStr,
            unitPrice = upStr,
            totalAmount = totStr,
            originalSegment = segment
        )
    }

    /**
     * Extracts continuous Arabic number phrases (e.g., "خمسة آلاف وخمسمائة", "25 ألف", "4000", "بألفين").
     */
    private fun extractAllNumberPhrases(tokens: List<String>, skipIndices: Set<Int>): List<ExtractedNumber> {
        val result = mutableListOf<ExtractedNumber>()
        var i = 0

        while (i < tokens.size) {
            if (i in skipIndices) {
                i++
                continue
            }

            val rawToken = tokens[i]
            var clean = cleanWord(rawToken)

            // Check price preposition prefix (e.g. "وب4000", "بخمسة", "بألفين", "ب500")
            var hasPricePrefix = false
            if (clean.startsWith("وب") && clean.length > 2 && isCoreNumberWord(clean.substring(2))) {
                hasPricePrefix = true
                clean = clean.substring(2)
            } else if (clean.startsWith("ب") && clean.length > 1 && isCoreNumberWord(clean.substring(1))) {
                hasPricePrefix = true
                clean = clean.substring(1)
            } else if (clean.startsWith("و") && clean.length > 1 && isCoreNumberWord(clean.substring(1))) {
                clean = clean.substring(1)
            }

            val prevWord = if (i > 0) cleanWord(tokens[i - 1]) else ""
            val nextWord = if (i + 1 < tokens.size) cleanWord(tokens[i + 1]) else ""

            val isUnitPrice = prevWord in setOf("بسعر", "سعر", "سعرها") ||
                    (i >= 2 && cleanWord(tokens[i - 2]) in setOf("الحبة", "الواحدة", "للحبة") && prevWord == "ب")

            val isExplicitTotal = prevWord in setOf("إجمالي", "اجمالي", "بإجمالي", "مجموع", "جملة", "ب") ||
                    nextWord in commonCurrencyWords || hasPricePrefix

            val isExplicitQuantity = prevWord in setOf("عدد", "كمية", "بكمية") ||
                    nextWord in quantityUnitWords

            if (isCoreNumberWord(clean)) {
                val phraseIndices = mutableSetOf(i)
                val phraseWords = mutableListOf(clean)
                var j = i + 1

                while (j < tokens.size) {
                    if (j in skipIndices) break
                    val nextRaw = tokens[j]
                    var nextClean = cleanWord(nextRaw)

                    // Case A: standalone "و" followed by number word
                    if (nextClean == "و" && j + 1 < tokens.size) {
                        var afterNext = cleanWord(tokens[j + 1])
                        if (afterNext.startsWith("ب") && afterNext.length > 1 && isCoreNumberWord(afterNext.substring(1))) {
                            afterNext = afterNext.substring(1)
                        }
                        if (isCoreNumberWord(afterNext)) {
                            phraseIndices.add(j)
                            phraseIndices.add(j + 1)
                            phraseWords.add("و")
                            phraseWords.add(afterNext)
                            j += 2
                            continue
                        }
                    }

                    // Case B: "و" attached to number word (e.g., "وخمسمائة", "وعشرين")
                    if (nextClean.startsWith("و") && nextClean.length > 1 && isCoreNumberWord(nextClean.substring(1))) {
                        phraseIndices.add(j)
                        phraseWords.add("و")
                        phraseWords.add(nextClean.substring(1))
                        j++
                        continue
                    }

                    // Case C: direct next number word (e.g. "خمسة آلاف", "25 ألف")
                    if (isCoreNumberWord(nextClean)) {
                        phraseIndices.add(j)
                        phraseWords.add(nextClean)
                        j++
                    } else {
                        break
                    }
                }

                val evalValue = evaluateNumberPhrase(phraseWords)
                if (evalValue > 0.0) {
                    result.add(
                        ExtractedNumber(
                            value = evalValue,
                            tokenIndices = phraseIndices,
                            isUnitPrice = isUnitPrice,
                            isExplicitTotal = isExplicitTotal,
                            isExplicitQuantity = isExplicitQuantity
                        )
                    )
                    i = j
                    continue
                }
            }
            i++
        }

        return result
    }

    /**
     * Evaluates a sequence of number words into a numeric Double value.
     * Correctly handles multipliers (ألف / مليون) and additions (وخمسمائة).
     */
    private fun evaluateNumberPhrase(words: List<String>): Double {
        var total = 0.0
        var currentGroup = 0.0

        var idx = 0
        while (idx < words.size) {
            val word = cleanWord(words[idx])
            if (word == "و") {
                idx++
                continue
            }

            val numeric = word.toDoubleOrNull()
            if (numeric != null) {
                currentGroup += numeric
                idx++
                continue
            }

            val mil = millionsMap[word]
            if (mil != null) {
                val factor = if (currentGroup > 0.0) currentGroup else 1.0
                total += factor * mil
                currentGroup = 0.0
                idx++
                continue
            }

            val th = thousandsMap[word]
            if (th != null) {
                val factor = if (currentGroup > 0.0) currentGroup else 1.0
                total += factor * th
                currentGroup = 0.0
                idx++
                continue
            }

            val h = hundredsMap[word]
            if (h != null) {
                currentGroup += h
                idx++
                continue
            }

            val t = tensMap[word]
            if (t != null) {
                currentGroup += t
                idx++
                continue
            }

            val tn = teensMap[word]
            if (tn != null) {
                currentGroup += tn
                idx++
                continue
            }

            val u = unitsMap[word]
            if (u != null) {
                currentGroup += u
                idx++
                continue
            }

            idx++
        }

        return total + currentGroup
    }
}
