package com.example.util

object KnownPaymentApps {

    /**
     * Dedicated UPI & Merchant SoundBox payment apps where notifications with an amount
     * (after debit, request, and promo/loan filtering) are high-confidence payment alerts.
     * NOTE: Messaging apps (WhatsApp), loan apps (Navi/Bajaj), and lifestyle/credit-card apps (CRED/Amazon)
     * are intentionally EXCLUDED from this set so they must match strict payment-receipt patterns.
     */
    val TRUSTED_UPI_ONLY_PACKAGES = setOf(
        // Major Consumer & Merchant UPI Apps
        "com.phonepe.app",
        "com.phonepe.merchant.app",
        "com.phonepe.app.business",
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.nbu.paisa.merchant",
        "net.one97.paytm",
        "com.paytm.business",
        "in.org.npci.upiapp",
        "in.org.npci.bhim.aadhaarpay",
        "in.bharatpe.merchant",
        "in.bharatpe.app",
        "com.super.money",
        "com.super.merchant",
        "com.airtel.bank.merchant",
        "com.pinelabs.merchant",
        "com.mswipe.merchant",
        "com.razorpay.payments.app",
        "com.cashfree.merchant",
        "com.payu.india",
        // Bank-Specific BHIM UPI Apps
        "com.sbi.upi",
        "com.bankofbaroda.upi",
        "com.pnb.upi",
        "com.boi.upi",
        "com.unionbank.upi",
        "com.canarabank.upi",
        "com.centralbank.upi",
        "com.indianbank.upi",
        "com.iob.upi",
        "com.uco.upi",
        "com.psb.upi",
        "com.idbi.upi",
        "com.upi.axispay",
        "com.rblbank.upi",
        "com.syndicate.upi",
        "com.corporationbank.upi",
        "com.andhrabank.upi",
        "com.allbank.upi"
    )

    /**
     * Messaging / SMS apps that may carry legitimate payment receipts (e.g., WhatsApp Pay or RCS bank SMS),
     * but ALSO carry normal chats and promotional loan/offer spam.
     * Notifications from these packages are ONLY processed if they contain explicit payment-receipt
     * phrasing and zero promo/loan indicators.
     */
    val MESSAGING_APP_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.oneplus.mms",
        "com.miui.mms"
    )

    val packageNames = mapOf(
        // Major UPI Apps & Wallets
        "com.phonepe.app" to "PhonePe",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM UPI",
        "in.org.npci.bhim.aadhaarpay" to "BHIM Aadhaar Pay",
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

        // Bank-Specific BHIM UPI Apps
        "com.sbi.upi" to "BHIM SBI Pay",
        "com.bankofbaroda.upi" to "BHIM Baroda Pay",
        "com.pnb.upi" to "BHIM PNB",
        "com.boi.upi" to "BHIM BOI UPI",
        "com.unionbank.upi" to "BHIM Union Bank UPI",
        "com.canarabank.upi" to "BHIM Canara UPI",
        "com.centralbank.upi" to "BHIM Cent UPI",
        "com.indianbank.upi" to "BHIM Indian Bank UPI",
        "com.iob.upi" to "BHIM IOB UPI",
        "com.uco.upi" to "BHIM UCO UPI",
        "com.psb.upi" to "BHIM PSB UPI",
        "com.idbi.upi" to "BHIM IDBI UPI",
        "com.upi.axispay" to "BHIM Axis Pay",
        "com.rblbank.upi" to "BHIM RBL Pay",
        "com.syndicate.upi" to "BHIM Syndicate UPI",
        "com.corporationbank.upi" to "BHIM Corp UPI",
        "com.andhrabank.upi" to "BHIM Andhra Bank UPI",
        "com.allbank.upi" to "BHIM Allahabad Bank UPI",

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
        "bhim", "kotak", "bank", "upi", "npci", "mobilebanking", "mbanking",
        "sbi", "hdfc", "icici", "axis", "pnb", "canara", "unionbank", "indianbank",
        "idfc", "indusind", "yesbank", "rblbank", "federalbank", "aubank",
        "equitas", "ujjivan", "suryoday", "ippb", "nsdl"
    )

    private val EXCLUDED_SPAM_PACKAGE_KEYWORDS = listOf(
        "browser", "chrome", "firefox", "opera", "instagram", "facebook",
        "twitter", "youtube", "telegram", "snapchat", "zomato", "swiggy",
        "flipkart", "myntra", "meesho", "dream11", "mpl", "rummy", "ludo",
        "kreditbee", "mpokket", "cashe", "branch", "kissht", "fibe", "truebalance",
        "stashfin", "smartcoin", "ring.app", "earlysalary", "paysense"
    )

    private val STRICT_CREDIT_GATE_REGEX = Regex(
        """(?i)(?:\b(?:received|credited|sent\s+you|paid\s+you|paid\s+to\s+your|transferred\s+to\s+your|deposited\s+in\s+your|deposited\s+to\s+your|upi\/cr)\b|प्राप्त|मिले|भेजे)"""
    )

    fun shouldProcessNotification(packageName: String, text: String): Boolean {
        val pkgLower = packageName.lowercase()

        // Never process known browsers, social apps, food delivery, gaming, or dedicated loan apps
        if (EXCLUDED_SPAM_PACKAGE_KEYWORDS.any { pkgLower.contains(it) }) {
            return false
        }

        // Messaging apps (WhatsApp, Google Messages, Samsung Messages) must pass strict credit gate
        if (MESSAGING_APP_PACKAGES.contains(packageName)) {
            return STRICT_CREDIT_GATE_REGEX.containsMatchIn(text)
        }

        if (TRUSTED_UPI_ONLY_PACKAGES.contains(packageName) || packageNames.containsKey(packageName)) {
            return true
        }

        if (BANK_PACKAGE_KEYWORDS.any { pkgLower.contains(it) }) {
            return true
        }

        // For any unrecognized package, require explicit credit-receipt phrasing (never bare "₹" or "cr")
        return STRICT_CREDIT_GATE_REGEX.containsMatchIn(text)
    }
}
