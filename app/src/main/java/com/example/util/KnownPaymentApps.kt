package com.example.util

object KnownPaymentApps {
    val packageNames = mapOf(
        // Major UPI Apps & Wallets
        "com.phonepe.app" to "PhonePe",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM UPI",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.mobikwik_new" to "MobiKwik",
        "com.whatsapp" to "WhatsApp Pay",
        "com.dreamplug.androidapp" to "CRED",
        "com.fampay.in" to "FamApp",
        "com.slice.app" to "Slice",
        "in.fi.app" to "Fi Money",
        "com.jupiter" to "Jupiter",
        "com.razorpay.payments.app" to "Razorpay",
        "com.freecharge.android" to "Freecharge",
        "com.samsung.android.spay" to "Samsung Pay",
        "com.super.money" to "Super.money",
        "com.pop.app" to "POP Club",
        "com.cheq.android" to "CHEQ",
        "com.navi.navi.app" to "Navi",
        "com.bajaj.pay" to "Bajaj Pay",
        "com.tatadigital.tcp" to "Tata Neu",

        // Merchant & Business UPI Apps
        "com.phonepe.merchant.app" to "PhonePe Business",
        "com.phonepe.app.business" to "PhonePe Business",
        "com.paytm.business" to "Paytm for Business",
        "com.google.android.apps.nbu.paisa.merchant" to "Google Pay for Business",
        "in.bharatpe.merchant" to "BharatPe",
        "in.bharatpe.app" to "BharatPe",
        "com.pinelabs.merchant" to "Pine Labs",
        "com.mswipe.merchant" to "Mswipe",
        "com.airtel.bank.merchant" to "Airtel Merchant",
        "com.payu.india" to "PayU",
        "com.cashfree.merchant" to "Cashfree",
        "com.super.merchant" to "Super.money Merchant",
        "com.cred.merchant" to "CRED Merchant",
        "com.whatsapp.w4b" to "WhatsApp Business",

        // Major Private Banks
        "com.hdfc.hdfcpayapp" to "PayZapp",
        "com.hdfcbank.payzapp" to "PayZapp",
        "com.snapwork.hdfc" to "HDFC Bank Mobile",
        "com.csam.icici.bank.imobile" to "iMobile Pay",
        "com.icicibank.pockets" to "ICICI Pockets",
        "com.axis.mobile" to "Axis Mobile",
        "com.axis.open" to "Open by Axis Bank",
        "com.kotak.mahindra.kotak811" to "Kotak 811",
        "com.msf.kpay" to "Kotak Bank",
        "com.kotak.mbanking" to "Kotak Bank",
        "com.kotak811" to "Kotak 811",
        "com.kotak.kmbl" to "Kotak Bank",
        "com.kotak.breeze" to "Kotak Cherry",
        "com.indusind.indusmobile" to "IndusMobile",
        "com.idfcfirstbank.banking" to "IDFC FIRST Bank",
        "com.yesbank" to "iris by YES BANK",
        "com.rblbank.mobank" to "RBL MoBank",
        "com.federalbank.mobileapp" to "FedMobile",
        "com.sib.mvault" to "SIB Mirror+",
        "com.kvb.dlite" to "KVB DLite",
        "com.bandhan.mbandhan" to "mBandhan",
        "com.csb.mobile" to "CSB Bank",
        "com.tmb.mobile" to "TMB Mobile",
        "com.cityunionbank.mobile" to "CUB Mobile",
        "com.dbs.in.dbsmbanking" to "DBS digibank",

        // Public Sector Banks
        "com.sbi.SBIFreedomPlus" to "YONO SBI",
        "com.moneytransfer.yono" to "SBI YONO Lite",
        "com.sbi.upi" to "BHIM SBI Pay",
        "com.canarabank.ai1" to "Canara Bank",
        "com.bankofbaroda.mconnect" to "BOB World",
        "com.pnb.pnbone" to "PNB ONE",
        "com.boi.boimobile" to "BOI Mobile",
        "com.unionbank.iMobile" to "Union Bank Vyom",
        "com.indianbank.indoasis" to "IndOASIS",
        "com.centralbank.centmobile" to "Cent Mobile",
        "com.iob.mconnect" to "IOB Mobile",
        "com.uco.ucombanking" to "UCO mBanking",
        "com.psb.mobile" to "PSB UnIC",

        // Small Finance & Payments Banks
        "com.aubank.au0101" to "AU 0101",
        "com.equitasbank.mobile" to "Equitas Mobile",
        "com.ujjivan.mobile" to "Ujjivan Mobile",
        "com.suryoday.mobile" to "Suryoday Bank",
        "com.jana.mobile" to "Jana Bank",
        "com.airteldigital.wallet" to "Airtel Payments Bank",
        "com.ippb.mobilebanking" to "IPPB Mobile",
        "com.jio.myjio" to "MyJio / Jio Pay",
        "com.nsdl.pb" to "NSDL Jiffy"
    )

    private val BANK_PACKAGE_KEYWORDS = listOf(
        "kotak", "bank", "pay", "upi", "npci", "financial", "mamp", "mobilebanking", "paisa",
        "sbi", "hdfc", "icici", "axis", "pnb", "bob", "canara", "union", "boi", "indianbank",
        "iob", "uco", "psb", "idfc", "indus", "yesbank", "rbl", "federal", "sib", "kvb",
        "bandhan", "aubank", "equitas", "ujjivan", "jana", "suryoday", "ippb", "airtel",
        "jio", "nsdl", "navi", "bajaj", "fintech", "finance", "banking", "mbanking", "wallet"
    )

    private val PAYMENT_TEXT_KEYWORDS = listOf(
        "received", "credited", "credit", "added", "deposited", "paid",
        "₹", "rs.", "inr", "rupees", "upi", "a/c", "cr", "successful", "transferred"
    )

    fun shouldProcessNotification(packageName: String, text: String): Boolean {
        if (packageNames.containsKey(packageName)) return true
        val pkgLower = packageName.lowercase()
        if (BANK_PACKAGE_KEYWORDS.any { pkgLower.contains(it) }) return true
        val textLower = text.lowercase()
        return PAYMENT_TEXT_KEYWORDS.any { textLower.contains(it) }
    }
}
