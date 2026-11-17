package app.kanakku.core

import app.kanakku.core.model.AccountKind
import app.kanakku.core.model.Channel
import app.kanakku.core.model.Direction
import app.kanakku.core.model.Nature
import app.kanakku.core.sms.ParsedTxn
import app.kanakku.core.sms.SmsParser
import app.kanakku.core.sms.SmsVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class SmsParserTest {

    private val today = LocalDate.of(2026, 11, 16)

    private fun parse(sender: String, body: String) = SmsParser.parse(sender, body, today)

    private fun txn(sender: String, body: String): ParsedTxn {
        val v = parse(sender, body)
        if (v !is SmsVerdict.Transaction) fail("Expected a transaction but got $v for:\n$body")
        return (v as SmsVerdict.Transaction).txn
    }

    private fun ParsedTxn.expect(
        rupees: Double,
        direction: Direction,
        kind: AccountKind? = null,
        last4: String? = null,
        party: String? = null,
        nature: Nature = Nature.GENERIC,
        channel: Channel? = null,
        balance: Double? = null,
        ref: String? = null,
        person: Boolean? = null,
        checkParty: Boolean = true,
    ) {
        assertEquals("amount", Math.round(rupees * 100), amountMinor)
        assertEquals("direction", direction, this.direction)
        if (kind != null) assertEquals("account kind", kind, accountKind)
        if (last4 != null) assertEquals("last4", last4, accountLast4)
        if (checkParty) assertEquals("counterparty", party, counterparty)
        assertEquals("nature", nature, this.nature)
        if (channel != null) assertEquals("channel", channel, this.channel)
        if (balance != null) assertEquals("balance", Math.round(balance * 100), balanceMinor)
        if (ref != null) assertEquals("ref", ref, refNo)
        if (person != null) assertEquals("person hint", person, personHint)
    }

    private fun ignored(sender: String, body: String) {
        val v = parse(sender, body)
        assertTrue("Expected Ignored but got $v for:\n$body", v is SmsVerdict.Ignored)
    }

    private fun sensitive(sender: String, body: String) {
        val v = parse(sender, body)
        assertEquals("Expected Sensitive for:\n$body", SmsVerdict.Sensitive, v)
    }

    // ---------------------------------------------------------------- SBI Card

    @Test fun sbiCard_spend() = txn(
        "VM-SBICRD-S",
        "Rs.1,234.00 spent on your SBI Credit Card ending 1234 at SWIGGY on 12/11/26. Trxn. not done by you? Report at https://sbicard.com/Dispute",
    ).expect(1234.0, Direction.OUT, AccountKind.CREDIT_CARD, "1234", "SWIGGY", channel = Channel.CARD)

    @Test fun sbiCard_spendViaUpi() = txn(
        "JD-SBICRD",
        "Rs.499.00 spent on your SBI Credit Card ending with 1234 at AMAZON PAY INDIA PRIVA on 05-11-26 via UPI (Ref No. 631234567890). Trxn. Not done by you? Report at https://sbicard.com/Dispute",
    ).expect(499.0, Direction.OUT, AccountKind.CREDIT_CARD, "1234", "AMAZON PAY INDIA PRIVA", channel = Channel.CARD, ref = "631234567890")

    @Test fun sbiCard_spendWithLimit() = txn(
        "AD-SBICRD-S",
        "Rs.1,500.00 spent on your SBI Credit Card ending 1234 at IOCL PETROL PUMP on 12/11/26. Avl limit Rs.83,500.00. Trxn. not done by you? Report at https://sbicard.com/Dispute",
    ).expect(1500.0, Direction.OUT, AccountKind.CREDIT_CARD, "1234", "IOCL PETROL PUMP", balance = 83500.0)

    @Test fun sbiCard_paymentReceived() = txn(
        "VM-SBICRD-S",
        "We have received payment of Rs.15,000.00 via UPI & the same has been credited to your SBI Credit Card ending 1234. Your available limit is Rs.85,000.00.",
    ).expect(15000.0, Direction.IN, AccountKind.CREDIT_CARD, "1234", nature = Nature.CARD_BILL_PAYMENT, balance = 85000.0, checkParty = false)

    @Test fun sbiCard_refund() = txn(
        "VM-SBICRD-S",
        "Refund of Rs.499.00 from AMAZON has been credited to your SBI Credit Card ending 1234 on 12/11/26.",
    ).expect(499.0, Direction.IN, AccountKind.CREDIT_CARD, "1234", "AMAZON", nature = Nature.REFUND)

    @Test fun sbiCard_declined() = ignored(
        "VM-SBICRD-S",
        "Transaction of Rs.2,500.00 on your SBI Credit Card ending 1234 at FLIPKART on 12/11/26 was declined due to insufficient limit.",
    )

    @Test fun sbiCard_foreignCurrencyIsSkipped() = ignored(
        "VM-SBICRD-S",
        "USD 20.00 spent on your SBI Credit Card ending 1234 at OPENAI on 12/11/26. Trxn. not done by you? Report at https://sbicard.com/Dispute",
    )

    @Test fun sbiCard_statement() {
        val v = parse(
            "VM-SBICRD-S",
            "E-statement of SBI Credit Card ending XX1234 dated 05/11/2026 has been mailed. Total Amt Due: Rs 12,345.00; Min Amt Due: Rs 617.00; Payable by 25/11/2026. If not received, SMS ENRS to 5676791",
        )
        assertTrue("got $v", v is SmsVerdict.Due)
        val due = (v as SmsVerdict.Due).due
        assertEquals(1234500L, due.totalMinor)
        assertEquals(61700L, due.minimumMinor)
        assertEquals(LocalDate.of(2026, 11, 25), due.dueDate)
        assertEquals("1234", due.accountLast4)
    }

    @Test fun sbiCard_reminder() {
        val v = parse(
            "VM-SBICRD-S",
            "Your SBI Credit Card ending 1234 payment of Rs.12,345.00 is due on 25-Nov-26. Min Amt Due Rs.617.00. Pay now at sbicard.com. Ignore if already paid.",
        )
        assertTrue("got $v", v is SmsVerdict.Due)
        val due = (v as SmsVerdict.Due).due
        assertEquals(1234500L, due.totalMinor)
        assertEquals(LocalDate.of(2026, 11, 25), due.dueDate)
    }

    // ---------------------------------------------------------------- SBI

    @Test fun sbi_upiDebit_merchant() = txn(
        "JD-SBIUPI",
        "Dear UPI user A/C X1234 debited by 500.0 on date 12Nov26 trf to SWIGGY Refno 631234567890. If not u? call 1800111109. -SBI",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "SWIGGY", channel = Channel.UPI, ref = "631234567890")

    @Test fun sbi_upiDebit_person() = txn(
        "JD-SBIUPI-S",
        "Dear UPI user A/C X1234 debited by 250.0 on date 12Nov26 trf to Mr ANOOP K S Refno 631234567891. If not u? call 1800111109. -SBI",
    ).expect(250.0, Direction.OUT, AccountKind.BANK, "1234", "ANOOP K S", person = true)

    @Test fun sbi_upiCredit() = txn(
        "JD-SBIUPI",
        "Dear SBI User, your A/c X1234-credited by Rs.5000 on 12Nov26 transfer from RAMESH KUMAR Ref No 631234567890 -SBI",
    ).expect(5000.0, Direction.IN, AccountKind.BANK, "1234", "RAMESH KUMAR", ref = "631234567890")

    @Test fun sbi_upiCredit_noName() = txn(
        "JD-SBIUPI",
        "Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 12Nov26 by  (Ref no 631234567890)",
    ).expect(500.0, Direction.IN, AccountKind.BANK, "1234", null)

    @Test fun sbi_neftCredit() = txn(
        "VM-SBIINB",
        "Your A/C XXXXX1234 Credited INR 48,500.00 on 30/10/26 -Deposit by transfer from M/S ABC TECHNOLOGIES. Avl Bal INR 52,345.67-SBI",
    ).expect(48500.0, Direction.IN, AccountKind.BANK, "1234", "ABC TECHNOLOGIES", balance = 52345.67)

    @Test fun sbi_salaryCredit() = txn(
        "VM-SBIINB",
        "Your A/C XXXXX1234 has credit for BY SALARY of Rs 48,500.00 on 30/10/26. Avl Bal Rs 52,345.67.-SBI",
    ).expect(48500.0, Direction.IN, AccountKind.BANK, "1234", nature = Nature.SALARY, balance = 52345.67, checkParty = false)

    @Test fun sbi_atm() = txn(
        "VM-ATMSBI",
        "Dear SBI Customer, Rs.2000 withdrawn at SBI ATM S1BW000123 from A/cX1234 on 12Nov26 Transaction Number 1234. Available Balance Rs.10,345.67. If not withdrawn by you, forward this SMS to 9223008333 / call 1800111109 or 09449112211 to block your card. Call 1930 for cyber fraud.",
    ).expect(2000.0, Direction.OUT, AccountKind.BANK, "1234", nature = Nature.ATM_WITHDRAWAL, channel = Channel.ATM, balance = 10345.67, checkParty = false)

    @Test fun sbi_debitCardPos() = txn(
        "VM-SBIPSG",
        "Dear Customer, transaction number 631234567890 for Rs.500.00 by SBI Debit Card X1234 done at 12345678 on 12Nov26 at 14:22:33. Your updated available balance is Rs.12,345.67. If not done by you, forward this SMS to 7400165218/ call 1800111109/9449112211 to block card. GOI helpline for cyber fraud 1930.",
    ).expect(500.0, Direction.OUT, AccountKind.DEBIT_CARD, "1234", null, channel = Channel.CARD, balance = 12345.67)

    @Test fun sbi_imps() = txn(
        "VM-SBIINB",
        "Your a/c no. XXXXXXXX1234 is debited for Rs.5000.00 on 12-11-26 and a/c XXXXXX5678 credited (IMPS Ref no 631234567890).",
    ).expect(5000.0, Direction.OUT, AccountKind.BANK, "1234", null, channel = Channel.BANK_TRANSFER, ref = "631234567890")

    @Test fun sbi_charges() = txn(
        "VM-SBIINB",
        "Dear Customer, Rs 150.00 debited from A/c XX1234 on 12-11-26 towards SMS charges for the quarter. -SBI",
    ).expect(150.0, Direction.OUT, AccountKind.BANK, "1234", nature = Nature.FEE, checkParty = false)

    @Test fun sbi_mandateExecuted() = txn(
        "VM-SBIUPI",
        "Rs.499.00 debited from a/c XX1234 for UPI-Mandate NETFLIX on 15-11-26. Ref 631234567890 -SBI",
    ).expect(499.0, Direction.OUT, AccountKind.BANK, "1234", "NETFLIX", channel = Channel.UPI)

    @Test fun sbi_mandateUpcoming() = ignored(
        "VM-SBIUPI",
        "Rs.499.00 will be debited from your a/c XX1234 on 15-11-26 towards NETFLIX for UPI-Mandate. -SBI",
    )

    @Test fun sbi_failedUpi() = ignored(
        "VM-SBIUPI",
        "Your UPI transaction of Rs 500.00 to SWIGGY has failed. Amount if debited will be credited back to your a/c XX1234 within 3 working days. -SBI",
    )

    @Test fun sbi_balanceEnquiry() = ignored(
        "VM-SBIINB",
        "Your SBI A/c XX1234 balance is Rs.12,345.67 as on 12-11-26.",
    )

    // ---------------------------------------------------------------- HDFC

    @Test fun hdfc_upiSent() = txn(
        "AX-HDFCBK",
        "Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 12/11/26\nRef 631234567890\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "SWIGGY", ref = "631234567890")

    @Test fun hdfc_upiCredit() = txn(
        "AX-HDFCBK-S",
        "Credit Alert!\nRs.5000.00 credited to HDFC Bank A/c XX1234 on 12-11-26 from VPA ramesh@okaxis (UPI 631234567890)",
    ).expect(5000.0, Direction.IN, AccountKind.BANK, "1234", "ramesh@okaxis", channel = Channel.UPI, ref = "631234567890")

    @Test fun hdfc_neftSalary() = txn(
        "AX-HDFCBK",
        "Update! INR 48,500.00 deposited in HDFC Bank A/c XX1234 on 30-OCT-26 for NEFT Cr-SBIN0001234-ABC TECHNOLOGIES-NAME-SBINN12345678.Avl bal INR 52,345.67. Cheque deposits in A/C are subject to clearing",
    ).expect(48500.0, Direction.IN, AccountKind.BANK, "1234", "ABC TECHNOLOGIES", balance = 52345.67)

    @Test fun hdfc_cardSpend() = txn(
        "AX-HDFCBK",
        "Spent Rs.1234.00 On HDFC Bank Card 1234 At SWIGGY On 2026-11-12:14:22:33.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 1234 to 7308080808",
    ).expect(1234.0, Direction.OUT, AccountKind.CREDIT_CARD, "1234", "SWIGGY", channel = Channel.CARD)

    @Test fun hdfc_cardUpiTxn() = txn(
        "AX-HDFCBK",
        "Txn Rs.1234.00\nOn HDFC Bank Card 1234\nAt swiggy@icici by UPI 631234567890\nOn 12-11\nNot You?\nCall 18002586161/SMS BLOCK CC 1234 to 7308080808",
    ).expect(1234.0, Direction.OUT, AccountKind.CREDIT_CARD, "1234", "swiggy@icici", channel = Channel.CARD)

    @Test fun hdfc_cardPayment() = txn(
        "AX-HDFCBK",
        "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 15000.00 RECEIVED TOWARDS YOUR CREDIT CARD ENDING WITH 1234 ON 12-11-2026.YOUR AVAILABLE LIMIT IS RS. 85000.00",
    ).expect(15000.0, Direction.IN, AccountKind.CREDIT_CARD, "1234", nature = Nature.CARD_BILL_PAYMENT, balance = 85000.0, checkParty = false)

    @Test fun hdfc_infoFormat() = txn(
        "AX-HDFCBK",
        "UPDATE: INR 500.00 debited from HDFC Bank XX1234 on 12-NOV-26. Info: UPI-SWIGGY-swiggy@icici-ICIC0000001-631234567890-Payment. Avl bal:INR 12,345.67",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "SWIGGY", channel = Channel.UPI, balance = 12345.67)

    @Test fun hdfc_moneyTransfer() = txn(
        "AX-HDFCBK",
        "Money Transfer:Rs 5000.00 from HDFC Bank A/c **1234 on 12-11-26 to RAMESH KUMAR UPI: 631234567890 Not you? Call 18002586161",
    ).expect(5000.0, Direction.OUT, AccountKind.BANK, "1234", "RAMESH KUMAR", channel = Channel.UPI)

    @Test fun hdfc_debitCard() = txn(
        "AX-HDFCBK",
        "Rs.500.00 spent on HDFC Bank Debit Card xx5678 at RELIANCE SMART on 2026-11-12:14:22:33. Avl bal: Rs.12,345.67. Not you? Call 18002586161",
    ).expect(500.0, Direction.OUT, AccountKind.DEBIT_CARD, "5678", "RELIANCE SMART", channel = Channel.CARD, balance = 12345.67)

    @Test fun hdfc_refund() = txn(
        "AX-HDFCBK",
        "Rs.342.00 refunded to your A/c XX1234 by SWIGGY on 12-11-26. UPI Ref 631234567892 -HDFC Bank",
    ).expect(342.0, Direction.IN, AccountKind.BANK, "1234", "SWIGGY", nature = Nature.REFUND)

    @Test fun hdfc_preApprovedLoanPromo() = ignored(
        "AX-HDFCBK",
        "Dear Customer, you are eligible for a pre-approved personal loan of Rs.5,00,000. Get it credited to your A/c XX1234 instantly. Apply now: hdfc.in/loan",
    )

    @Test fun hdfc_promotionalHeader() = ignored(
        "AX-HDFCBK-P",
        "Rs.500 cashback credited on spends above Rs.5000 with HDFC Bank Card 1234. T&C",
    )

    // ---------------------------------------------------------------- ICICI

    @Test fun icici_upiDebit() = txn(
        "VK-ICICIB",
        "ICICI Bank Acct XX123 debited for Rs 500.00 on 12-Nov-26; SWIGGY credited. UPI:631234567890. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "123", "SWIGGY", channel = Channel.UPI, ref = "631234567890")

    @Test fun icici_upiCredit() = txn(
        "VK-ICICIB",
        "Dear Customer, Acct XX123 is credited with Rs 5000.00 on 12-Nov-26 from RAMESH KUMAR. UPI:631234567890-ICICI Bank.",
    ).expect(5000.0, Direction.IN, AccountKind.BANK, "123", "RAMESH KUMAR")

    @Test fun icici_neft() = txn(
        "VK-ICICIB",
        "ICICI Bank Account XX123 credited:Rs. 48,500.00 on 30-Oct-26. Info NEFT-SBIN0001234-ABC TECH. Available Balance is Rs. 52,345.67.",
    ).expect(48500.0, Direction.IN, AccountKind.BANK, "123", "ABC TECH", balance = 52345.67)

    @Test fun icici_cardSpend() = txn(
        "VK-ICICIB",
        "INR 1,234.00 spent using ICICI Bank Card XX1234 on 12-Nov-26 on SWIGGY. Avl Limit: INR 98,766.00. If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766.",
    ).expect(1234.0, Direction.OUT, AccountKind.CREDIT_CARD, "1234", "SWIGGY", balance = 98766.0)

    @Test fun icici_cardPayment() = txn(
        "VK-ICICIB",
        "Payment of INR 15,000.00 has been received on your ICICI Bank Credit Card XX1234 through UPI on 12-NOV-26.",
    ).expect(15000.0, Direction.IN, AccountKind.CREDIT_CARD, "1234", nature = Nature.CARD_BILL_PAYMENT, checkParty = false)

    // ---------------------------------------------------------------- Axis

    @Test fun axis_upiDebit() = txn(
        "AD-AXISBK",
        "INR 500.00 debited\nA/c no. XX1234\n12-11-26, 14:22:33\nUPI/P2M/631234567890/SWIGGY\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "SWIGGY", channel = Channel.UPI, person = false)

    @Test fun axis_upiCredit() = txn(
        "AD-AXISBK",
        "INR 5000.00 credited\nA/c no. XX1234\n12-11-26, 14:22:33 IST\nUPI/P2A/631234567890/RAMESH KUMAR/okaxis - Axis Bank",
    ).expect(5000.0, Direction.IN, AccountKind.BANK, "1234", "RAMESH KUMAR", person = true)

    @Test fun axis_cardSpend() = txn(
        "AD-AXISBK",
        "Spent\nCard no. XX1234\nINR 1234\n12-11-26 14:22:33\nSWIGGY\nAvl Lmt INR 98766\nSMS BLOCK 1234 to 919951860002, if not you - Axis Bank",
    ).expect(1234.0, Direction.OUT, AccountKind.CREDIT_CARD, "1234", "SWIGGY", balance = 98766.0)

    // ---------------------------------------------------------------- Kotak

    @Test fun kotak_sent() = txn(
        "VM-KOTAKB",
        "Sent Rs.500.00 from Kotak Bank AC X1234 to swiggy@icici on 12-11-26.UPI Ref 631234567890. Not you, https://kotak.com/KBANKT/Fraud",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "swiggy@icici", channel = Channel.UPI, ref = "631234567890")

    @Test fun kotak_received() = txn(
        "VM-KOTAKB",
        "Received Rs.5000.00 in your Kotak Bank AC X1234 from ramesh@okaxis on 12-11-26.UPI Ref:631234567890.",
    ).expect(5000.0, Direction.IN, AccountKind.BANK, "1234", "ramesh@okaxis")

    @Test fun kotak_cashback() = txn(
        "VM-KOTAKB",
        "Cashback of Rs.25.00 credited to your A/c XX1234 for UPI txn. -Kotak",
    ).expect(25.0, Direction.IN, AccountKind.BANK, "1234", nature = Nature.CASHBACK, checkParty = false)

    // ---------------------------------------------------------------- Kerala banks

    @Test fun federal_upiDebit() = txn(
        "AD-FEDBNK",
        "Rs 500.00 debited via UPI on 12-11-2026 14:22:33 to VPA swiggy@axb.Ref No 631234567890.Small txns?Use UPI Lite!-Federal Bank",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, null, "swiggy@axb", channel = Channel.UPI, ref = "631234567890")

    @Test fun federal_upiCredit() = txn(
        "AD-FEDBNK",
        "Rs 5000.00 credited to your A/c XX1234 via UPI on 12NOV2026 14:22:33 from VPA ramesh@okaxis. Ref No 631234567890 -Federal Bank",
    ).expect(5000.0, Direction.IN, AccountKind.BANK, "1234", "ramesh@okaxis")

    @Test fun federal_neftSalary() = txn(
        "AD-FEDBNK",
        "Your A/c XX1234 is credited with Rs.47,250.00 on 31OCT2026 by NEFT from INFOPARK SOLUTIONS, SALARY OCT 2026. Bal: Rs.51,095.67 -Federal Bank",
    ).expect(47250.0, Direction.IN, AccountKind.BANK, "1234", "INFOPARK SOLUTIONS", nature = Nature.SALARY, balance = 51095.67)

    @Test fun federal_atm() = txn(
        "AD-FEDBNK",
        "Rs.2000.00 withdrawn from your A/c XX1234 at FEDERAL BANK ATM KAKKANAD on 12NOV2026. Avl Bal: Rs.10,345.67 -Federal Bank",
    ).expect(2000.0, Direction.OUT, AccountKind.BANK, "1234", nature = Nature.ATM_WITHDRAWAL, channel = Channel.ATM, checkParty = false)

    @Test fun federal_emi() = txn(
        "AD-FEDBNK",
        "Rs.3,250.00 debited from A/c XX1234 on 05-11-26 towards EMI of loan a/c XX9876. Avl Bal Rs.9,095.67 -Federal Bank",
    ).expect(3250.0, Direction.OUT, AccountKind.BANK, "1234", nature = Nature.EMI, balance = 9095.67, checkParty = false)

    @Test fun federal_interest() = txn(
        "AD-FEDBNK",
        "Your A/c XX1234 is credited with INR 312.00 on 30-09-26 towards interest. Avl Bal INR 12,657.67 -Federal Bank",
    ).expect(312.0, Direction.IN, AccountKind.BANK, "1234", nature = Nature.INTEREST, checkParty = false)

    @Test fun sib_upiDebit() = txn(
        "VM-SIBSMS",
        "A/c X1234 debited INR 500.00 on 12-11-26. Info: UPI/P2M/631234567890/LULU HYPERMARKET. Bal: INR 12,345.67 -South Indian Bank",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "LULU HYPERMARKET", balance = 12345.67, person = false)

    @Test fun keralaBank_unknownHeader() {
        val t = txn(
            "AD-KSCBTV",
            "Your A/c XXXX1234 is debited with Rs.500.00 on 12-11-2026 by transfer. Bal Rs.12,345.67 - Kerala Bank",
        )
        t.expect(500.0, Direction.OUT, AccountKind.BANK, "1234", balance = 12345.67, checkParty = false)
        assertEquals("Kerala Bank", t.institution?.name)
    }

    @Test fun csb_warningFooterIsNotSensitive() = txn(
        "VM-CSBBNK",
        "Rs.500.00 debited from A/c XX1234 on 12-11-26 to VPA swiggy@icici (UPI Ref 631234567890). Never share OTP/PIN with anyone. -CSB Bank",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "swiggy@icici")

    @Test fun csb_warningPrefixIsNotSensitive() = txn(
        "VM-CSBBNK",
        "Do not share your OTP with anyone. Rs.500.00 debited from A/c XX1234 on 12-11-26 to SWIGGY. UPI Ref 631234567890 -CSB Bank",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "SWIGGY")

    // ---------------------------------------------------------------- Other banks

    @Test fun canara_debitNoParty() = txn(
        "VM-CANBNK",
        "An amount of INR 500.00 has been DEBITED to your account XXX1234 on 12/11/2026. Total Avail.bal INR 12,345.67. - Canara Bank",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", null, balance = 12345.67)

    @Test fun canara_upiPaid() = txn(
        "VM-CANBNK",
        "Rs.500.00 paid thru A/C XX1234 on 12-11-26 14:22:33 to SWIGGY, UPI Ref 631234567890. If not done, SMS BLOCKUPI to 9901771222.-Canara Bank",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "SWIGGY", ref = "631234567890")

    @Test fun union_debit() = txn(
        "VM-UNIONB",
        "A/c *1234 Debited for Rs:500.00 on 12-11-2026 14:22:33 by Mob Bk ref no 631234567890 Avl Bal Rs:12345.67.If not you, Call 1800222243 -Union Bank of India",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", balance = 12345.67, checkParty = false)

    @Test fun pnb_debit() = txn(
        "VM-PNBSMS",
        "A/c XX1234 debited INR 500.00 Dt 12-11-26 14:22 thru UPI:631234567890.Bal INR 12345.67 Not u?Fwd this SMS to 9264092640 to block UPI.Download PNB ONE-PNB",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", balance = 12345.67, checkParty = false)

    @Test fun bob_debit() = txn(
        "VM-BOBTXN",
        "Rs.500 Dr. from A/C XXXXXX1234 and Cr. to swiggy@icici. Ref:631234567890. AvlBal:Rs12345.67(2026:11:12 14:22:33). Not you? Call 18005700/5000-BOB",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "swiggy@icici", balance = 12345.67)

    @Test fun bob_credit() = txn(
        "VM-BOBTXN",
        "Rs.5000 Credited to A/c ...1234 thru UPI/631234567890 by ramesh_okaxis. Total Bal:Rs.17345.67CR. Avlbl Amt:Rs.17345.67(12-11-2026 14:22:33) - Bank of Baroda",
    ).expect(5000.0, Direction.IN, AccountKind.BANK, "1234", "ramesh_okaxis", balance = 17345.67)

    @Test fun idfc_debit() = txn(
        "VM-IDFCFB",
        "Your A/C XXXXXXX1234 is debited by INR 500.00 on 12/11/26 14:22. New Bal :INR 12,345.67. Call us at 180010888 for dispute -IDFC FIRST Bank",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", balance = 12345.67, checkParty = false)

    @Test fun idfc_card() = txn(
        "VM-IDFCFB",
        "INR 1,234.00 spent on your IDFC FIRST Bank Credit Card ending XX1234 at SWIGGY on 12 NOV 2026 at 02:22 PM Avbl Limit: INR 98,766.00 If not done by you, call 180010888",
    ).expect(1234.0, Direction.OUT, AccountKind.CREDIT_CARD, "1234", "SWIGGY", balance = 98766.0)

    @Test fun amex_spend() = txn(
        "VM-AMEXIN",
        "Alert: You've spent INR 1,234.00 on your AMEX card ** 12345 at SWIGGY on 12 November 2026 at 02:22 PM IST. Call 18004190691 if this was not made by you.",
    ).expect(1234.0, Direction.OUT, AccountKind.CREDIT_CARD, "2345", "SWIGGY")

    @Test fun boi_debit() = txn(
        "VM-BOIIND",
        "Rs.500.00 debited A/cXX1234 and credited to SWIGGY via UPI Ref No 631234567890 on 12Nov26. Call 18001031906, if not done by you. -BOI",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "SWIGGY", channel = Channel.UPI)

    @Test fun indianBank_debit() = txn(
        "VM-INDBNK",
        "A/c *1234 debited Rs. 500.00 on 12-11-26 to SWIGGY. UPI:631234567890. Not you? SMS BLOCK to 9289592895, Dial 1930 for Cyber Fraud - Indian Bank",
    ).expect(500.0, Direction.OUT, AccountKind.BANK, "1234", "SWIGGY")

    @Test fun paytm_wallet() = txn(
        "VM-PAYTM",
        "Paid Rs.120 to RAVI STORES from Paytm Wallet. Updated Balance: Paytm Wallet- Rs 380. More Details: https://paytm.me/xyz",
    ).expect(120.0, Direction.OUT, AccountKind.WALLET, null, "RAVI STORES", balance = 380.0)

    // ---------------------------------------------------------------- Noise

    @Test fun personalNumberIsIgnored() = ignored(
        "+919876543210",
        "I have sent Rs.500 to your a/c XX1234, pls check",
    )

    @Test fun merchantConfirmationIsIgnored() = ignored(
        "JD-SWIGGY",
        "Your order #1234 is confirmed. Rs.342 paid via UPI. Track at swiggy.com",
    )

    @Test fun collectRequestIsIgnored() = ignored(
        "VM-SBIUPI",
        "RAMESH has requested Rs.500 from you. Approve in your UPI app to pay from A/c X1234. -SBI",
    )

    @Test fun rechargePromoIsIgnored() = ignored(
        "JX-JIOPAY",
        "Recharge with Rs.299 and get Rs.50 cashback credited to your wallet. Recharge now.",
    )

    @Test fun billReminderIsIgnored() = ignored(
        "VM-KSEBLT",
        "Your KSEB bill of Rs.1,940 for Con.No 1156789012345 is due on 20/11/2026. Pay at wss.kseb.in",
    )

    // ---------------------------------------------------------------- Secrets

    @Test fun otp_purchase() = sensitive(
        "VM-SBICRD-S",
        "OTP for online purchase of Rs. 1,234.00 at AMAZON thru SBI Credit Card ending 1234 is 123456. Do not share it with anyone.",
    )

    @Test fun otp_leading() = sensitive(
        "VM-SBICRD-S",
        "123456 is OTP for txn of INR 500.00 at SWIGGY on your SBI card ending 1234. Valid for 10 mins. Do not share OTP with anyone",
    )

    @Test fun otp_plain() = sensitive("VM-HDFCBK", "Your OTP is 482913 for login. Valid 5 mins.")

    @Test fun otp_verificationCode() = sensitive("JD-SWIGGY", "482913 is your verification code for Swiggy.")

    @Test fun otp_useAs() = sensitive(
        "VM-SBICRD",
        "Use 482913 as one time password for txn of Rs 2,500.00 at FLIPKART. SBI Card",
    )

    @Test fun otp_withDebitWording() = sensitive(
        "VM-SBIUPI",
        "482913 is the OTP to approve debit of Rs.500.00 from A/c X1234 debited via mandate. Do not share.",
    )

    @Test fun pin_changed() = sensitive("VM-SBIUPI", "Your UPI PIN has been changed successfully. If not you, call 1800111109.")

    @Test fun pin_green() = sensitive("VM-SBIPSG", "Dear Customer, Green PIN for your SBI Debit Card ending 1234 is generated.")

    @Test fun cvv() = sensitive("VM-ICICIB", "Your CVV for the virtual card ending 1234 is 123.")

    @Test fun statementPassword() = sensitive(
        "VM-HDFCBK",
        "Password for your e-statement is the first 4 letters of your name followed by DDMM of birth.",
    )

    @Test fun otp_fromPersonalNumberStillSensitive() = sensitive("+919876543210", "your otp is 4455")
}
