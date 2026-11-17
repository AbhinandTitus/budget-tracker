package app.kanakku.core.category

import app.kanakku.core.category.Categories.BILLS
import app.kanakku.core.category.Categories.EDUCATION
import app.kanakku.core.category.Categories.EMI
import app.kanakku.core.category.Categories.ENTERTAINMENT
import app.kanakku.core.category.Categories.FOOD
import app.kanakku.core.category.Categories.FUEL
import app.kanakku.core.category.Categories.GIVING
import app.kanakku.core.category.Categories.GROCERIES
import app.kanakku.core.category.Categories.HEALTH
import app.kanakku.core.category.Categories.INSURANCE
import app.kanakku.core.category.Categories.INVESTMENTS
import app.kanakku.core.category.Categories.PERSONAL
import app.kanakku.core.category.Categories.RENT
import app.kanakku.core.category.Categories.SHOPPING
import app.kanakku.core.category.Categories.SUBSCRIPTIONS
import app.kanakku.core.category.Categories.TRANSPORT
import app.kanakku.core.category.Categories.TRAVEL

/**
 * What the app knows about merchants before the user teaches it anything.
 *
 * Two layers, both matched against an upper-cased, punctuation-free form of the merchant text:
 *  - [brands]  : named businesses, national and Kerala-specific, each with a display name.
 *  - [lexicon] : the words Kerala shops put in their names ("Bakery", "Medicals", "Fuels",
 *                "Thattukada"), which is enough to place most local merchants.
 */
object MerchantBook {

    data class Known(val pattern: Regex, val name: String?, val category: String)

    private class Builder {
        val out = ArrayList<Known>()
        fun group(category: String, vararg entries: Pair<String, String?>) {
            entries.forEach { (pattern, name) -> out += Known(Regex(pattern), name, category) }
        }
    }

    /** First match wins, so narrower names ("Swiggy Instamart") sit above broader ones ("Swiggy"). */
    val brands: List<Known> = Builder().apply {
        group(
            GROCERIES,
            "INSTAMART" to "Swiggy Instamart",
            "BLINKIT|GROFERS" to "Blinkit",
            "ZEPTO|KIRANAKART" to "Zepto",
            "BIG ?BASKET|BBNOW|BB ?DAILY|INNOVATIVE RETAIL" to "BigBasket",
            "JIO ?MART" to "JioMart",
            "AMAZON ?(?:FRESH|PANTRY|NOW)" to "Amazon Fresh",
            "FLIPKART ?(?:GROCERY|MINUTES|SUPERMART)" to "Flipkart Minutes",
            """\bD ?MART\b|AVENUE SUPERMART""" to "DMart",
            "DUNZO" to "Dunzo",
            "LICIOUS" to "Licious",
            "FRESH ?TO ?HOME" to "FreshToHome",
            "COUNTRY ?DELIGHT" to "Country Delight",
            "MILKBASKET" to "Milkbasket",
        )
        group(SHOPPING, "LULU ?(?:FASHION|CONNECT|CELEBRATE)" to "Lulu Fashion")
        group(
            GROCERIES,
            "LULU" to "Lulu Hypermarket",
            """\bNESTO\b""" to "Nesto",
            "BISMI ?HYPER" to "Bismi Hypermart",
            "MARGIN ?FREE" to "Margin Free Market",
            """SUPPLY ?CO\b|MAVELI""" to "Supplyco",
            "RELIANCE ?(?:SMART|FRESH)|SMART ?(?:BAZAAR|POINT)" to "Reliance Smart",
            """\bMORE ?(?:SUPERMARKET|RETAIL|MEGA|HYPER)""" to "More",
            """\bSPAR\b""" to "SPAR",
            "NILGIRI" to "Nilgiris",
            "STAR ?BAZAAR" to "Star Bazaar",
            "SPENCER" to "Spencer's",
            "BIG ?BAZAAR" to "Big Bazaar",
            "VISHAL ?MEGA" to "Vishal Mega Mart",
            """\bMILMA\b""" to "Milma",
            "HORTICORP|VFPCK" to "Horticorp",
            "TRIVENI|CONSUMERFED|NEETHI ?STORE" to "Triveni",
        )
        group(
            SUBSCRIPTIONS,
            "NETFLIX" to "Netflix",
            "SPOTIFY" to "Spotify",
            "AMAZON ?PRIME|PRIME ?VIDEO|AMZN ?PRIME" to "Amazon Prime",
            "HOTSTAR|JIO ?CINEMA|DISNEY" to "JioHotstar",
            "YOUTUBE" to "YouTube Premium",
            "GOOGLE ?ONE|GOOGLE ?STORAGE" to "Google One",
            """GOOGLE ?PLAY|GOOGLE ?PAYMENT|GOOGLE ?INDIA ?DIGITAL|^GOOGLE\b""" to "Google Play",
            "APPLE ?COM|APPLE ?SERVICES|APPLE ?MEDIA|ITUNES|ICLOUD|APPLE ?BILL" to "Apple",
            "SONY ?LIV" to "SonyLIV",
            """ZEE ?5\b""" to "ZEE5",
            "SUN ?NXT" to "Sun NXT",
            "MANORAMA ?MAX" to "ManoramaMAX",
            "SAINA ?PLAY" to "Saina Play",
            "MUBI" to "MUBI",
            "CRUNCHYROLL" to "Crunchyroll",
            "AUDIBLE" to "Audible",
            "KUKU ?FM" to "Kuku FM",
            "POCKET ?FM" to "Pocket FM",
            "GAANA" to "Gaana",
            "JIO ?SAAVN" to "JioSaavn",
            "CHATGPT|OPENAI" to "ChatGPT",
            "CLAUDE|ANTHROPIC" to "Claude",
            "PERPLEXITY" to "Perplexity",
            "MIDJOURNEY" to "Midjourney",
            "GITHUB" to "GitHub",
            """\bNOTION\b""" to "Notion",
            "CANVA" to "Canva",
            "ADOBE" to "Adobe",
            "MICROSOFT|MSFT|OFFICE ?365" to "Microsoft",
            "LINKEDIN" to "LinkedIn",
            "DROPBOX" to "Dropbox",
            "TINDER" to "Tinder",
            "BUMBLE" to "Bumble",
        )
        group(
            EDUCATION,
            "DC ?BOOKS" to "DC Books",
            "MATHRUBHUMI ?BOOKS" to "Mathrubhumi Books",
            "CURRENT ?BOOKS" to "Current Books",
        )
        group(SUBSCRIPTIONS, "MALAYALA ?MANORAMA|MATHRUBHUMI|THE HINDU|TIMES OF INDIA|DESHABHIMANI|KERALA ?KAUMUDI" to null)
        group(
            FOOD,
            "UBER ?EATS" to "Uber Eats",
            "SWIGGY" to "Swiggy",
            "ZOMATO" to "Zomato",
            "EATSURE|FAASOS|BEHROUZ|REBEL ?FOODS" to "EatSure",
            "EAZYDINER" to "EazyDiner",
            "MAGICPIN" to "magicpin",
            "DOMINO" to "Domino's",
            "PIZZA ?HUT" to "Pizza Hut",
            """\bKFC\b""" to "KFC",
            "MC ?DONALD" to "McDonald's",
            "BURGER ?KING" to "Burger King",
            """\bSUBWAY\b""" to "Subway",
            "STARBUCKS" to "Starbucks",
            """CAFE ?COFFEE ?DAY|\bCCD\b""" to "Café Coffee Day",
            "CHICKING" to "Chicking",
            "PARAGON" to "Paragon",
            "RAHMATH" to "Rahmath",
            "ARYAAS|ARYA ?BHAVAN" to "Aryaas",
            "INDIAN ?COFFEE ?HOUSE" to "Indian Coffee House",
            "SARAVANA ?BHAVAN" to "Saravana Bhavan",
            """\bA2B\b|ADYAR ?ANANDA""" to "A2B",
            "BARBEQUE ?NATION" to "Barbeque Nation",
            "PAPA ?JOHN" to "Papa John's",
            "BASKIN" to "Baskin Robbins",
            """\bIBACO\b""" to "Ibaco",
            "HALDIRAM" to "Haldiram's",
            "WOW ?MOMO" to "Wow! Momo",
            "CHAAYOS" to "Chaayos",
            "CHAI ?POINT" to "Chai Point",
            "THIRD ?WAVE" to "Third Wave Coffee",
            "BLUE ?TOKAI" to "Blue Tokai",
            "ZAM ?ZAM" to "Zam Zam",
            "KAYEES|KAYIKKA" to "Kayees",
            "DHE ?PUTTU" to "Dhe Puttu",
            """\bTHAFF\b""" to "Thaff",
            """\bAJWA\b""" to "Ajwa",
            "BUHARI" to "Buhari",
        )
        group(
            SHOPPING,
            "AMAZON|AMZN" to "Amazon",
            "FLIPKART|EKART" to "Flipkart",
            "MYNTRA" to "Myntra",
            """\bAJIO\b""" to "AJIO",
            "MEESHO" to "Meesho",
            "NYKAA" to "Nykaa",
            "TATA ?CLIQ" to "Tata CLiQ",
            "SNAPDEAL" to "Snapdeal",
            "SHOPSY" to "Shopsy",
            "FIRSTCRY" to "FirstCry",
            "BEWAKOOF" to "Bewakoof",
            "SOULED ?STORE" to "The Souled Store",
            "SNITCH" to "Snitch",
            "URBANIC" to "Urbanic",
            "RELIANCE ?DIGITAL" to "Reliance Digital",
            """RELIANCE ?TRENDS|\bTRENDS\b""" to "Trends",
            "CROMA|INFINITI ?RETAIL" to "Croma",
            "VIJAY ?SALES" to "Vijay Sales",
            "OXYGEN" to "Oxygen",
            """\bMY ?G\b|3G ?MOBILE ?WORLD""" to "myG",
            "NANDILATH" to "Nandilath G-Mart",
            "PITTAPPILLIL" to "Pittappillil",
            """\bQRS\b""" to "QRS",
            "BISMI" to "Bismi",
            "KALYAN ?SILKS" to "Kalyan Silks",
            "SEEMATTI" to "Seematti",
            "JAYALAKSHMI" to "Jayalakshmi",
            "POTHYS" to "Pothys",
            "CHENNAI ?SILKS" to "The Chennai Silks",
            "RAMRAJ" to "Ramraj Cotton",
            "PULIMOOTTIL" to "Pulimoottil Silks",
            "MAX ?FASHION|MAX ?RETAIL" to "Max Fashion",
            "ZUDIO" to "Zudio",
            "WESTSIDE" to "Westside",
            "PANTALOONS" to "Pantaloons",
            "LIFESTYLE" to "Lifestyle",
            "SHOPPERS ?STOP" to "Shoppers Stop",
            "HENNES" to "H&M",
            """\bZARA\b""" to "Zara",
            "UNIQLO" to "Uniqlo",
            "DECATHLON" to "Decathlon",
            "ADIDAS" to "Adidas",
            """\bNIKE\b""" to "Nike",
            """\bPUMA\b""" to "Puma",
            "SKECHERS" to "Skechers",
            """\bBATA\b""" to "Bata",
            "WOODLAND" to "Woodland",
            "MALABAR ?GOLD" to "Malabar Gold",
            "KALYAN ?JEWEL" to "Kalyan Jewellers",
            """\bBHIMA\b""" to "Bhima",
            "JOY ?ALUKKAS" to "Joyalukkas",
            "JOSCO" to "Josco",
            "CHEMMANUR" to "Chemmanur",
            "TANISHQ" to "Tanishq",
            "CARATLANE" to "CaratLane",
            """\bIKEA\b""" to "IKEA",
            "PEPPERFRY" to "Pepperfry",
            "HOME ?CENTRE" to "Home Centre",
            "APTRONIX|IMAGINE ?STORE" to "Apple reseller",
            "SAMSUNG" to "Samsung",
            "ONEPLUS" to "OnePlus",
            "XIAOMI" to "Xiaomi",
            "MINISO" to "Miniso",
            "HAMLEYS" to "Hamleys",
            "SUPERTAILS|HEADS ?UP ?FOR ?TAILS" to "Supertails",
        )
        group(
            TRANSPORT,
            """\bUBER\b""" to "Uber",
            """\bOLA\b|OLACABS|ANI ?TECHNOLOGIES""" to "Ola",
            "RAPIDO|ROPPEN" to "Rapido",
            "NAMMA ?YATRI|KERALA ?SAVAARI" to "Namma Yatri",
            "KSRTC|K ?SWIFT|KERALA ?STATE ?ROAD" to "KSRTC",
            """KMRL|KOCHI ?METRO|KOCHI ?1\b|WATER ?METRO""" to "Kochi Metro",
            """IRCTC|INDIAN ?RAILWAY|\bUTS\b""" to "IRCTC",
            "RED ?BUS" to "redBus",
            "ABHIBUS" to "AbhiBus",
            "KALLADA" to "Kallada Travels",
            """FASTAG|\bNETC\b|\bNHAI\b""" to "FASTag",
            "YULU" to "Yulu",
            "ZOOMCAR" to "Zoomcar",
            """PARIVAHAN|E ?CHALLAN|\bMVD\b""" to "Motor Vehicles Dept",
            "ROYAL ?ENFIELD" to "Royal Enfield",
            "POPULAR ?VEHICLES" to "Popular Vehicles",
            "INDUS ?MOTORS" to "Indus Motors",
            "NIPPON ?TOYOTA" to "Nippon Toyota",
            """\bATHER\b""" to "Ather",
        )
        group(
            FUEL,
            """\bIOCL?\b|INDIAN ?OIL""" to "Indian Oil",
            """\bBPCL?\b|BHARAT ?PETRO""" to "Bharat Petroleum",
            """\bHPCL\b|HINDUSTAN ?PETRO|HP ?PETROL|\bHP ?PAY\b""" to "HP Petrol",
            "NAYARA|ESSAR ?OIL" to "Nayara",
            """\bSHELL\b""" to "Shell",
            "JIO ?BP|RELIANCE ?PETRO" to "Jio-bp",
            "STATIQ|CHARGEZONE|TATA ?POWER ?EZ|CHARGE ?MOD|ZEON" to "EV charging",
        )
        group(
            BILLS,
            "KSEB|KERALA ?STATE ?ELECTRICITY" to "KSEB",
            """\bKWA\b|KERALA ?WATER|WATER ?AUTHORITY""" to "Kerala Water Authority",
            "JIO ?FIBER" to "JioFiber",
            """\bJIO\b|RELIANCE ?JIO|JIO ?(?:PREPAID|POSTPAID|RECHARGE)""" to "Jio",
            "AIRTEL" to "Airtel",
            """VODAFONE|\bVI\b|VODA ?IDEA""" to "Vi",
            """\bBSNL\b""" to "BSNL",
            "ASIANET" to "Asianet Broadband",
            "KERALA ?VISION|KCCL" to "Kerala Vision",
            "RAILWIRE" to "RailWire",
            """\bACT ?FIBER|ATRIA ?CONV""" to "ACT Fibernet",
            "TATA ?PLAY|TATA ?SKY" to "Tata Play",
            "SUN ?DIRECT" to "Sun Direct",
            "DISH ?TV" to "Dish TV",
            """\bD2H\b""" to "d2h",
            """INDANE|BHARAT ?GAS|HP ?GAS|\bLPG\b""" to "Cooking gas",
            """K ?SMART\b|\bLSGD\b|MUNICIPALITY|PANCHAYATH?\b""" to "Local body",
            "E ?TREASURY" to "e-Treasury",
            "AKSHAYA ?(?:CENT|E)" to "Akshaya centre",
            "BBPS|BHARAT ?BILL" to "Bill payment",
        )
        group(
            TRAVEL,
            """MAKE ?MY ?TRIP|\bMMT\b""" to "MakeMyTrip",
            "GOIBIBO" to "Goibibo",
            "CLEARTRIP" to "Cleartrip",
            "IXIGO" to "ixigo",
            "EASE ?MY ?TRIP" to "EaseMyTrip",
            "INDIGO|INTERGLOBE" to "IndiGo",
            "AIR ?INDIA" to "Air India",
            "AKASA" to "Akasa Air",
            "SPICEJET" to "SpiceJet",
            "EMIRATES" to "Emirates",
            "QATAR ?AIR" to "Qatar Airways",
            "ETIHAD" to "Etihad",
            "AIR ?ARABIA" to "Air Arabia",
            "FLY ?DUBAI" to "flydubai",
            """\bOYO\b|ORAVEL""" to "OYO",
            "AIRBNB" to "Airbnb",
            "BOOKING ?COM" to "Booking.com",
            "AGODA" to "Agoda",
            "TREEBO" to "Treebo",
            "ZOSTEL" to "Zostel",
            """\bKTDC\b|\bDTPC\b|KERALA ?TOURISM""" to "Kerala Tourism",
            "VFS ?GLOBAL" to "VFS Global",
        )
        group(
            ENTERTAINMENT,
            "BOOK ?MY ?SHOW|BIGTREE" to "BookMyShow",
            """\bPVR\b|\bINOX\b""" to "PVR INOX",
            "CINEPOLIS" to "Cinépolis",
            "AASHIRVAD|SHENOYS|CARNIVAL ?CINE" to null,
            "TICKETNEW" to "TicketNew",
            """STEAM ?GAMES|\bSTEAM\b""" to "Steam",
            "PLAYSTATION|SONY ?INTERACTIVE" to "PlayStation",
            """\bXBOX\b""" to "Xbox",
            "EPIC ?GAMES" to "Epic Games",
            "KRAFTON|BGMI" to "BGMI",
            """DREAM ?11""" to "Dream11",
            """\bMPL\b|MY11 ?CIRCLE|RUMMY|WINZO|ZUPEE""" to null,
            "WONDERLA" to "Wonderla",
            "SILVER ?STORM" to "Silver Storm",
            """BEVCO|\bKSBC\b|BEVERAGES ?CORP|KERALA ?STATE ?BEVERAGES""" to "Bevco",
            "LOTTER" to "Lottery",
        )
        group(
            HEALTH,
            "APOLLO" to "Apollo",
            "MEDPLUS" to "MedPlus",
            "NETMEDS" to "Netmeds",
            "PHARMEASY" to "PharmEasy",
            """\b1 ?MG\b|TATA ?1MG|ONEMG""" to "Tata 1mg",
            "TRUEMEDS" to "Truemeds",
            "NEETHI ?MEDICAL" to "Neethi Medicals",
            "JAN ?AUSHADHI" to "Jan Aushadhi",
            """\bASTER\b""" to "Aster",
            """\bKIMS\b|KIMSHEALTH""" to "KIMSHEALTH",
            "LAKESHORE" to "Lakeshore Hospital",
            """AMRITA ?(?:INSTITUTE|HOSPITAL)|\bAIMS\b""" to "Amrita Hospital",
            "BABY ?MEMORIAL" to "Baby Memorial Hospital",
            """\bMIMS\b""" to "Aster MIMS",
            "RAJAGIRI ?HOSP" to "Rajagiri Hospital",
            "LISIE" to "Lisie Hospital",
            "LOURDES" to "Lourdes Hospital",
            "MEDICAL ?TRUST" to "Medical Trust Hospital",
            "MEITRA" to "Meitra Hospital",
            "CARITAS" to "Caritas Hospital",
            "PUSHPAGIRI" to "Pushpagiri",
            """\bDDRC\b""" to "DDRC",
            "DR ?LAL|LAL ?PATH" to "Dr Lal PathLabs",
            "METROPOLIS" to "Metropolis",
            "THYROCARE" to "Thyrocare",
            "KOTTAKKAL|ARYA ?VAIDYA" to "Kottakkal Arya Vaidya Sala",
            "OUSHADHI|VAIDYARATNAM" to null,
            "PRACTO" to "Practo",
            "LENSKART" to "Lenskart",
            "TITAN ?EYE" to "Titan Eye+",
        )
        group(
            EDUCATION,
            "UDEMY" to "Udemy",
            "COURSERA" to "Coursera",
            "UNACADEMY" to "Unacademy",
            "BYJU" to "BYJU'S",
            "PHYSICS ?WALLAH" to "Physics Wallah",
            "UPGRAD" to "upGrad",
            "SCALER" to "Scaler",
            "NPTEL|SWAYAM" to "NPTEL",
            "DUOLINGO" to "Duolingo",
            "XYLEM" to "Xylem",
            """\bENTRI\b""" to "Entri",
            "BRILLIANT ?(?:PALA|STUDY)" to "Brilliant Pala",
            """IELTS|BRITISH ?COUNCIL|\bIDP\b|\bOET\b""" to null,
            "KINDLE" to "Kindle",
            """\bKTU\b|CUSAT|IGNOU""" to null,
        )
        group(
            PERSONAL,
            "URBAN ?COMPANY|URBANCLAP" to "Urban Company",
            "CULT ?FIT|CUREFIT" to "cult.fit",
            "GOLD ?S ?GYM" to "Gold's Gym",
            "ANYTIME ?FITNESS" to "Anytime Fitness",
            "GREEN ?TRENDS" to "Green Trends",
            "LAKME" to "Lakmé Salon",
            "PURPLLE" to "Purplle",
            "MAMAEARTH" to "Mamaearth",
        )
        group(
            INVESTMENTS,
            "ZERODHA" to "Zerodha",
            "GROWW|NEXTBILLION" to "Groww",
            "UPSTOX|RKSV" to "Upstox",
            "ANGEL ?(?:ONE|BROKING)" to "Angel One",
            """\b5 ?PAISA""" to "5paisa",
            "ICICI ?(?:DIRECT|SECURITIES)" to "ICICI Direct",
            "HDFC ?SEC" to "HDFC Securities",
            "IND ?MONEY|INDWEALTH" to "INDmoney",
            "KUVERA" to "Kuvera",
            "PAYTM ?MONEY" to "Paytm Money",
            "ET ?MONEY" to "ET Money",
            "SMALLCASE" to "smallcase",
            """INDIAN ?CLEARING|\bICCL\b|BSE ?STAR|BSE ?LIMITED|NSE ?CLEARING|NSCCL""" to "Mutual fund",
            """MUTUAL ?FUND|\bAMC\b|ASSET ?MANAGEMENT|\bCAMS\b|KFIN|MF ?UTILIT""" to "Mutual fund",
            """\bKSFE\b""" to "KSFE",
            """CHITTY|\bCHITS?\b|\bKURI\b""" to null,
            """\bPPF\b""" to "PPF",
            """\bNPS\b|NSDL ?CRA|PROTEAN""" to "NPS",
            "SUKANYA" to "Sukanya Samriddhi",
            """SAFEGOLD|MMTC|AUGMONT|DIGI ?GOLD|DIGITAL ?GOLD""" to "Digital gold",
            "COINDCX|WAZIRX|COINSWITCH|ZEBPAY|BINANCE|MUDREX" to null,
            "TREASURY ?SAVINGS|KERALA ?TREASURY" to "Kerala Treasury",
        )
        group(
            INSURANCE,
            """\bLIC\b|LIFE ?INSURANCE ?CORP""" to "LIC",
            "HDFC ?LIFE" to "HDFC Life",
            "HDFC ?ERGO" to "HDFC ERGO",
            "ICICI ?PRU" to "ICICI Prudential",
            "ICICI ?LOMBARD" to "ICICI Lombard",
            "SBI ?LIFE" to "SBI Life",
            "SBI ?GENERAL" to "SBI General",
            "MAX ?LIFE" to "Max Life",
            "BAJAJ ?ALLIANZ" to "Bajaj Allianz",
            "TATA ?AI[AG]" to "Tata AIA",
            "STAR ?HEALTH" to "Star Health",
            "NIVA ?BUPA|MAX ?BUPA" to "Niva Bupa",
            "CARE ?HEALTH" to "Care Health",
            """\bACKO\b""" to "ACKO",
            "GO ?DIGIT|DIGIT ?INSURANCE" to "Digit",
            "NEW ?INDIA ?ASSURANCE|UNITED ?INDIA ?INS|ORIENTAL ?INS|NATIONAL ?INS" to null,
            "POLICY ?BAZAAR|PB ?FINTECH" to "Policybazaar",
        )
        group(
            EMI,
            "BAJAJ ?FIN" to "Bajaj Finserv",
            "HDB ?FIN" to "HDB Financial",
            "TATA ?CAPITAL" to "Tata Capital",
            "MUTHOOT" to "Muthoot",
            "MANAPPURAM" to "Manappuram",
            "KOSAMATTAM" to "Kosamattam",
            "HOME ?CREDIT" to "Home Credit",
            """KREDITBEE|MONEYVIEW|\bNAVI\b|\bFIBE\b|EARLYSALARY|ZESTMONEY|MPOKKET|PAYSENSE""" to null,
            """LAZYPAY|\bSIMPL\b""" to null,
        )
        group(
            GIVING,
            "GURUVAYUR|SABARIMALA|DEVASWOM" to null,
            """KETTO|MILAAP|IMPACT ?GURU|GIVE ?INDIA|AKSHAYA ?PATRA|\bCMDRF\b|PM ?CARES|RELIEF ?FUND""" to null,
        )
        group(RENT, """NOBROKER|RED ?GIRAFFE|RENT ?PAY|HOUSING ?COM""" to null)
    }.out

    /** Generic naming words. Order encodes precedence between categories. */
    val lexicon: List<Known> = Builder().apply {
        group(RENT, """\bRENT\b|HOSTEL|\bPG\b|PAYING ?GUEST|LANDLORD|APARTMENT|FLAT ?OWNERS|RESIDENTS? ?ASSOC""" to null)
        group(
            HEALTH,
            """MEDICALS?\b|PHARMAC|PHARMA\b|CHEMIST|\bDRUGS?\b|MEDICINE|MEDICOS|HOSPITAL|\bCLINIC|NURSING ?HOME|MEDICITY|""" +
                """DENTAL|DENTIST|EYE ?(?:HOSP|CARE|CLINIC)|\bLABS?\b|LABORATOR|DIAGNOSTIC|\bSCANS?\b|AYUR|VAIDYA|HOMOEO|HOMEO|""" +
                """PHYSIO|OPTICALS?\b|OPTICS|HEALTH ?CARE""" to null,
        )
        group(FUEL, """PETROL|\bFUELS?\b|FILLING ?STATION|\bPETRO\b|GAS ?STATION|\bPUMP\b|\bBUNK\b|SERVICE ?STATION|EV ?CHARG""" to null)
        group(
            EDUCATION,
            """SCHOOL|COLLEGE|UNIVERSITY|ACADEMY|INSTITUTE|INSTITUTION|TUITION|TUTION|COACHING|STUDY ?CENT|EDUCATION|""" +
                """VIDYALAYA|\bBOOKS?\b|STATIONER|XEROX|PHOTOSTAT|\bDTP\b|\bEXAM""" to null,
        )
        group(
            GIVING,
            """TEMPLE|KSHETH?RAM|CHURCH|PARISH|DIOCESE|CATHEDRAL|BASILICA|FORANE|MASJID|MOSQUE|\bJUMA\b|MAHALL?U?\b|""" +
                """MADRASA|ZAKAT|DARGAH|ASHRAM|CHARIT|DONATION|PALLIATIVE|ORPHANAGE|OLD ?AGE ?HOME""" to null,
        )
        group(
            TRAVEL,
            """RESORT|HOME ?STAY|HOUSE ?BOAT|\bLODGE|LODGING|RESIDENCY|\bINN\b|\bSUITES\b|\bTOURS?\b|TOURISM|HOLIDAYS|""" +
                """AIRLINES?|AIRWAYS|AIRPORT""" to null,
        )
        group(
            PERSONAL,
            """SALON|SALOON|PARLOU?R|BEAUTY|\bHAIR\b|BARBER|\bGENTS\b|\bSPA\b|MAKEOVER|\bGYM\b|FITNESS|\bYOGA\b|ZUMBA|""" +
                """CROSSFIT|LAUNDRY|DRY ?CLEAN|IRONING|TAILOR|STITCHING|UNISEX""" to null,
        )
        group(
            INVESTMENTS,
            """\bSIP\b|RECURRING ?DEPOSIT|FIXED ?DEPOSIT|TERM ?DEPOSIT|\bRD\b|\bFD\b|CHIT ?FUND|SECURITIES|BROKING""" to null,
        )
        group(INSURANCE, """INSURANCE|ASSURANCE""" to null)
        group(EMI, """\bLOAN\b|\bFINANCE\b|FINCORP|\bNIDHI\b|MICRO ?FIN|LEASING""" to null)
        group(
            BILLS,
            """ELECTRICITY|RECHARGE|BROADBAND|FIBERNET|INTERNET|\bWIFI\b|CABLE ?(?:TV|NET)|POSTPAID|PREPAID|GAS ?AGENC|""" +
                """\bDTH\b|WATER ?BILL|BILL ?PAY""" to null,
        )
        group(
            FOOD,
            """RESTAURANT|RESTAURENT|\bRESTO\b|\bHOTEL\b|KITCHEN|\bCAFE\b|CAFFE|COFFEE|\bFOODS?\b|EATERY|\bDINER?\b|DINING|""" +
                """CATER|\bMESS\b|CANTEEN|TIFFIN|DHABA|GRILL|\bBBQ\b|BARBEQUE|PIZZA|BURGER|FRIED ?CHICKEN|BROAST|\bSWEETS?\b|""" +
                """ICE ?CREAM|BAKERY|\bBAKERS?\b|\bBAKES\b|\bCAKES?\b|JUICE|COOL ?BAR|THATTU|\bCHAYA|\bCHAI\b|\bTEA\b|""" +
                """BIRYANI|BIRIYANI|\bMANDI\b|MANTHI|SHAWARMA|SHAWAYA|AL ?FAHAM|SNACKS|\bTASTE\b|FLAVOU?RS|\bCURRY\b|""" +
                """\bDOSA\b|PAROTTA|PUTTU|FAST ?FOOD|FOOD ?COURT|REFRESHMENT|JANAKEEYA""" to null,
        )
        group(
            ENTERTAINMENT,
            """CINEMAS?\b|TALKIES|THEATRE|THEATER|MULTIPLEX|\bMOVIES?\b|\bBAR\b|\bPUB\b|BREWERY|TODDY|\bWINES?\b|LIQUOR|""" +
                """\bTURF\b|FUTSAL|BOWLING|SNOOKER|GAME ?ZONE|AMUSEMENT|\bZOO\b|MUSEUM""" to null,
        )
        group(
            TRANSPORT,
            """\bTRAVELS\b|\bMETRO\b|\bTAXI\b|\bCABS?\b|PARKING|SERVICE ?CENT|WORKSHOP|GARAGE|AUTOMOBILES?|\bMOTORS\b|""" +
                """\bTYRES?\b|TWO ?WHEELER|CAR ?WASH|PUNCTURE|\bSPARES\b|AUTO ?PARTS|\bBUS\b|FERRY|\bTOLL\b|RAILWAYS?\b""" to null,
        )
        group(
            GROCERIES,
            """SUPER ?MARKET|HYPER ?MARKET|HYPERMART|MINI ?MART|\bMART\b|PROVISION|GROCER|KIRANA|VEGETABLE|\bVEG\b|""" +
                """\bFRUITS?\b|\bFISH\b|\bMEAT\b|CHICKEN|COLD ?STORAGE|\bBEEF\b|MUTTON|\bMILK\b|DAIRY|\bEGGS?\b|\bRICE\b|""" +
                """FLOUR ?MILL|OIL ?MILL|DAILY ?NEEDS|\bSTORES?\b|\bBAZAAR?\b|\bMARKET\b""" to null,
        )
        group(
            SHOPPING,
            """TEXTILES?\b|\bSILKS\b|FASHIONS?\b|BOUTIQUE|GARMENTS|READYMADE|APPARELS?\b|DRESSES|WEDDING ?CENT|SAREES?\b|""" +
                """FOOTWEAR|\bSHOES\b|CHAPPAL|JEWEL|\bGOLD\b|DIAMOND|MOBILES?\b|ELECTRONICS|COMPUTERS?\b|GADGETS|""" +
                """HOME ?APPLIANCES|FURNITURE|HARDWARES?\b|\bPAINTS\b|SANITARY|\bTILES\b|ELECTRICALS|\bGIFTS?\b|\bTOYS\b|""" +
                """\bFANCY\b|COSMETICS|FLORIST|FLOWERS|\bPETS?\b|SHOPPING|\bMALL\b|EMPORIUM|COLLECTIONS?\b|\bRETAIL\b|""" +
                """SHOPPE?\b|\bSHOP\b""" to null,
        )
    }.out

    /**
     * For UPI ids, where words run together ("rameshstores@okaxis"). Deliberately a short list of
     * words long enough not to appear inside ordinary names.
     */
    private val runTogether: List<Pair<Regex, String>> = listOf(
        Regex("""BAKERY|BAKERS|RESTAURANT|HOTEL|KITCHEN|CAFE|FOODS|BIRIYANI|BIRYANI|JUICE|THATTU|CATERING|SWEETS""") to FOOD,
        Regex("""SUPERMARKET|HYPERMARKET|PROVISION|GROCER|STORES|VEGETABLE|FRUITS|CHICKEN|COLDSTORAGE|MARGINFREE""") to GROCERIES,
        Regex("""MEDICAL|PHARMA|CLINIC|HOSPITAL|DENTAL|OPTICAL""") to HEALTH,
        Regex("""FUELS|PETROL|FILLINGSTATION""") to FUEL,
        Regex("""TEXTILE|FASHION|SILKS|FOOTWEAR|MOBILES|ELECTRONICS|JEWEL""") to SHOPPING,
        Regex("""SALON|SALOON|BEAUTY|FITNESS""") to PERSONAL,
        Regex("""TRAVELS|AUTOMOBILE|MOTORS""") to TRANSPORT,
    )

    /** Words that mark a name as a business even when no category can be read from it. */
    val businessWords = Regex(
        """\b(?:TRADERS?|TRADING|ENTERPRISES?|AGENC(?:Y|IES)|ASSOCIATES|INDUSTR(?:Y|IES)|SOLUTIONS?|TECHNOLOG(?:Y|IES)|""" +
            """TECH|SERVICES?|PVT|PRIVATE|LTD|LIMITED|LLP|INC|CORP|CORPORATION|COMPANY|GROUP|INDIA|INTERNATIONAL|GLOBAL|""" +
            """SONS|BROTHERS|BROS|CO|CENTRE|CENTER|POINT|HUB|WORLD|ZONE|PLAZA|PALACE|HOUSE|GALLERY|STUDIO|WORKS|MILLS|""" +
            """EXPORTS?|IMPORTS?|DISTRIBUTORS?|MARKETING|SUPPLIERS?|CONSTRUCTIONS?|BUILDERS|ENGINEERING|PRINTERS|""" +
            """PACKAGING|LOGISTICS|COURIER|TRANSPORT|SOCIETY|BANK|COOPERATIVE|COOP|ASSOCIATION|CLUB|TRUST|FOUNDATION|""" +
            """COMMITTEE|SANGHAM|SAMITHI|KUDUMBASHREE|UNIT|INFOTECH|SYSTEMS|SOFTWARE|LABS|CONSULTANCY|CONSULTING|""" +
            """PAYMENTS?|ONLINE|DIGITAL|MEDIA|NETWORKS?|COMMUNICATIONS?|FINSERV|CAPITAL|VENTURES|HOLDINGS|RETAILS?)\b""",
    )

    fun brand(key: String): Known? = brands.firstOrNull { it.pattern.containsMatchIn(key) }

    fun lexiconCategory(key: String): String? = lexicon.firstOrNull { it.pattern.containsMatchIn(key) }?.category

    fun runTogetherCategory(compact: String): String? = runTogether.firstOrNull { it.first.containsMatchIn(compact) }?.second

    fun isBusinessName(key: String): Boolean =
        businessWords.containsMatchIn(key) || lexicon.any { it.pattern.containsMatchIn(key) }
}
