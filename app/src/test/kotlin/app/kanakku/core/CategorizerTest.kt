package app.kanakku.core

import app.kanakku.core.category.Categories
import app.kanakku.core.category.Categorizer
import app.kanakku.core.category.Classified
import app.kanakku.core.category.LearnedRule
import app.kanakku.core.category.Merchants
import app.kanakku.core.model.TxnType
import app.kanakku.core.sms.SmsParser
import app.kanakku.core.sms.SmsVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CategorizerTest {

    private fun classify(sender: String, body: String, rules: (String) -> LearnedRule? = { null }): Classified {
        val v = SmsParser.parse(sender, body, LocalDate.of(2026, 11, 16))
        check(v is SmsVerdict.Transaction) { "not a transaction: $v" }
        return Categorizer.classify(v.txn, rules = rules)
    }

    private fun card(merchant: String) = classify(
        "VM-SBICRD-S",
        "Rs.450.00 spent on your SBI Credit Card ending 1234 at $merchant on 12/11/26. Trxn. not done by you? Report at https://sbicard.com/Dispute",
    )

    private fun upi(payee: String) = classify(
        "JD-SBIUPI",
        "Dear UPI user A/C X1234 debited by 450.0 on date 12Nov26 trf to $payee Refno 631234567890. If not u? call 1800111109. -SBI",
    )

    private fun vpa(id: String) = classify(
        "AD-FEDBNK",
        "Rs 450.00 debited via UPI on 12-11-2026 14:22:33 to VPA $id.Ref No 631234567890.Small txns?Use UPI Lite!-Federal Bank",
    )

    private fun expect(c: Classified, category: String, type: TxnType = TxnType.EXPENSE, name: String? = null) {
        assertEquals("category of ${c.merchantName}", category, c.categoryId)
        assertEquals("type of ${c.merchantName}", type, c.type)
        if (name != null) assertEquals(name, c.merchantName)
    }

    @Test fun brands() {
        expect(card("SWIGGY"), Categories.FOOD, name = "Swiggy")
        expect(card("SWIGGY INSTAMART"), Categories.GROCERIES, name = "Swiggy Instamart")
        expect(card("ZOMATO LIMITED"), Categories.FOOD, name = "Zomato")
        expect(card("AMAZON PAY INDIA PRIVA"), Categories.SHOPPING, name = "Amazon")
        expect(card("AMAZON PRIME"), Categories.SUBSCRIPTIONS)
        expect(card("NETFLIX COM"), Categories.SUBSCRIPTIONS, name = "Netflix")
        expect(card("PAYTM*NETFLIX"), Categories.SUBSCRIPTIONS)
        expect(card("FLIPKART INTERNET PVT"), Categories.SHOPPING, name = "Flipkart")
        expect(card("UBER INDIA SYSTEMS PVT"), Categories.TRANSPORT, name = "Uber")
        expect(card("IRCTC"), Categories.TRANSPORT)
        expect(card("IOCL PETROL PUMP"), Categories.FUEL, name = "Indian Oil")
        expect(card("APPLE.COM/BILL"), Categories.SUBSCRIPTIONS, name = "Apple")
        expect(card("BOOKMYSHOW"), Categories.ENTERTAINMENT)
        expect(card("ZERODHA BROKING LTD"), Categories.INVESTMENTS, name = "Zerodha")
        expect(card("LIC OF INDIA"), Categories.INSURANCE, name = "LIC")
    }

    @Test fun keralaBrands() {
        expect(upi("LULU HYPERMARKET"), Categories.GROCERIES, name = "Lulu Hypermarket")
        expect(upi("KSEB"), Categories.BILLS, name = "KSEB")
        expect(upi("KERALA WATER AUTHORITY"), Categories.BILLS)
        expect(upi("KSRTC"), Categories.TRANSPORT, name = "KSRTC")
        expect(upi("KOCHI METRO RAIL LTD"), Categories.TRANSPORT, name = "Kochi Metro")
        expect(upi("SUPPLYCO"), Categories.GROCERIES)
        expect(upi("MARGIN FREE MARKET"), Categories.GROCERIES)
        expect(upi("MILMA"), Categories.GROCERIES)
        expect(upi("CHICKING"), Categories.FOOD)
        expect(upi("INDIAN COFFEE HOUSE"), Categories.FOOD)
        expect(upi("OXYGEN THE DIGITAL EXPERT"), Categories.SHOPPING, name = "Oxygen")
        expect(upi("KSFE LTD"), Categories.INVESTMENTS, name = "KSFE")
        expect(upi("ASIANET SATELLITE COMM"), Categories.BILLS)
        expect(upi("BEVCO"), Categories.ENTERTAINMENT)
        expect(upi("GURUVAYUR DEVASWOM"), Categories.GIVING)
        expect(upi("ASTER MEDCITY"), Categories.HEALTH)
    }

    @Test fun shopNameLexicon() {
        expect(upi("HOTEL ARYA NIVAS"), Categories.FOOD)
        expect(upi("ROYAL BAKERY"), Categories.FOOD)
        expect(upi("AL TAZA THATTUKADA"), Categories.FOOD)
        expect(upi("MALABAR BIRIYANI HUB"), Categories.FOOD)
        expect(upi("SREE KRISHNA STORES"), Categories.GROCERIES)
        expect(upi("FRESH FISH MARKET"), Categories.GROCERIES)
        expect(upi("ST MARYS MEDICALS"), Categories.HEALTH)
        expect(upi("CITY DENTAL CLINIC"), Categories.HEALTH)
        expect(upi("KAKKANAD FUELS"), Categories.FUEL)
        expect(upi("STYLE GENTS BEAUTY PARLOUR"), Categories.PERSONAL)
        expect(upi("POWER GYM"), Categories.PERSONAL)
        expect(upi("ROYAL TEXTILES"), Categories.SHOPPING)
        expect(upi("ST GEORGE CHURCH"), Categories.GIVING)
        expect(upi("GREEN VALLEY RESORT"), Categories.TRAVEL)
        expect(upi("MAJESTIC CINEMAS"), Categories.ENTERTAINMENT)
        expect(upi("SPEED AUTOMOBILES"), Categories.TRANSPORT)
        expect(upi("ABC MENS HOSTEL"), Categories.RENT)
    }

    @Test fun people() {
        expect(upi("Mr ANOOP K S"), Categories.PEOPLE, name = "Anoop K S")
        expect(upi("RAMESH KUMAR"), Categories.PEOPLE)
        expect(upi("FATHIMA K P"), Categories.PEOPLE)
        expect(vpa("9876543210@ybl"), Categories.PEOPLE)
        expect(vpa("ramesh.kumar92@okaxis"), Categories.PEOPLE)
    }

    @Test fun vpaMerchants() {
        expect(vpa("swiggy@axb"), Categories.FOOD, name = "Swiggy")
        expect(vpa("swiggyinstamart@icici"), Categories.GROCERIES)
        expect(vpa("sreekrishnastores@okhdfcbank"), Categories.GROCERIES)
        expect(vpa("citymedicals.12345678@okaxis"), Categories.HEALTH)
        val opaque = vpa("paytmqr281005050101abcd@paytm")
        expect(opaque, Categories.OTHER, name = "Paytm merchant")
        assertTrue(opaque.needsReview)
    }

    @Test fun unknownMerchantNeedsReview() {
        val c = upi("ZYXWV")
        expect(c, Categories.OTHER)
        assertTrue(c.needsReview)
    }

    @Test fun natureDriven() {
        expect(
            classify("VM-ATMSBI", "Dear SBI Customer, Rs.2000 withdrawn at SBI ATM S1BW000123 from A/cX1234 on 12Nov26 Transaction Number 1234. Available Balance Rs.10,345.67."),
            Categories.CASH, name = "ATM withdrawal",
        )
        expect(
            classify("VM-SBIINB", "Dear Customer, Rs 150.00 debited from A/c XX1234 on 12-11-26 towards SMS charges for the quarter. -SBI"),
            Categories.FEES, name = "Bank charges",
        )
        expect(
            classify("VM-SBICRD-S", "We have received payment of Rs.15,000.00 via UPI & the same has been credited to your SBI Credit Card ending 1234. Your available limit is Rs.85,000.00."),
            Categories.TRANSFER, TxnType.TRANSFER, name = "SBI Card payment",
        )
        expect(upi("SBI CARDS AND PAYMENT"), Categories.TRANSFER, TxnType.TRANSFER)
        expect(upi("CRED"), Categories.TRANSFER, TxnType.TRANSFER)
    }

    @Test fun income() {
        expect(
            classify("VM-SBIINB", "Your A/C XXXXX1234 Credited INR 48,500.00 on 30/10/26 -Deposit by transfer from M/S ABC TECHNOLOGIES. Avl Bal INR 52,345.67-SBI"),
            Categories.SALARY, TxnType.INCOME, name = "Abc Technologies",
        )
        // Salary keyword wins even outside the range.
        expect(
            classify("AD-FEDBNK", "Your A/c XX1234 is credited with Rs.72,000.00 on 31OCT2026 by NEFT from INFOPARK SOLUTIONS, SALARY OCT 2026. Bal: Rs.75,095.67 -Federal Bank"),
            Categories.SALARY, TxnType.INCOME,
        )
        expect(
            classify("JD-SBIUPI", "Dear SBI User, your A/c X1234-credited by Rs.5000 on 12Nov26 transfer from RAMESH KUMAR Ref No 631234567890 -SBI"),
            Categories.POCKET, TxnType.INCOME,
        )
        expect(
            classify("AD-FEDBNK", "Rs 2000.00 credited to your A/c XX1234 via UPI on 12NOV2026 14:22:33 from VPA 9876543210@ybl. Ref No 631234567890 -Federal Bank"),
            Categories.POCKET, TxnType.INCOME,
        )
        expect(
            classify("AD-FEDBNK", "Your A/c XX1234 is credited with INR 312.00 on 30-09-26 towards interest. Avl Bal INR 12,657.67 -Federal Bank"),
            Categories.INTEREST, TxnType.INCOME,
        )
        expect(
            classify("JD-SBIUPI", "Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 12Nov26 by  (Ref no 631234567890)"),
            Categories.INCOME_OTHER, TxnType.INCOME,
        )
    }

    @Test fun refundsReduceTheirCategory() {
        expect(
            classify("VM-SBICRD-S", "Refund of Rs.499.00 from AMAZON has been credited to your SBI Credit Card ending 1234 on 12/11/26."),
            Categories.SHOPPING, TxnType.REFUND,
        )
        expect(
            classify("AX-HDFCBK", "Rs.342.00 refunded to your A/c XX1234 by SWIGGY on 12-11-26. UPI Ref 631234567892 -HDFC Bank"),
            Categories.FOOD, TxnType.REFUND,
        )
    }

    @Test fun learnedRuleOverridesEverything() {
        val rules: (String) -> LearnedRule? = { key -> if (key == "RAMESH KUMAR") LearnedRule(Categories.RENT, "Landlord") else null }
        val c = classify(
            "JD-SBIUPI",
            "Dear UPI user A/C X1234 debited by 9000.0 on date 02Nov26 trf to RAMESH KUMAR Refno 631234567890. If not u? call 1800111109. -SBI",
            rules,
        )
        expect(c, Categories.RENT, name = "Landlord")
        assertFalse(c.needsReview)
    }

    @Test fun merchantKeys() {
        assertEquals("LULU HYPERMARKET", Merchants.keyOf("LULU HYPERMARKET KOCHI"))
        assertEquals("NETFLIX", Merchants.keyOf("PAYTM*NETFLIX"))
        assertEquals("ABC TECHNOLOGIES", Merchants.keyOf("ABC TECHNOLOGIES PVT LTD"))
        assertEquals("KOCHI METRO RAIL", Merchants.keyOf("KOCHI METRO RAIL LTD"))
        assertEquals("Kochi Metro Rail", Merchants.titleCase("KOCHI METRO RAIL"))
        assertEquals("KSEB", Merchants.titleCase("KSEB"))
    }
}
