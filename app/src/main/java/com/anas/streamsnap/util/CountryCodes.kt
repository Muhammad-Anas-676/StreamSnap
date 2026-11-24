package com.anas.streamsnap.util

import java.util.Locale

/** One country in the picker: ISO region, English name, dial code with the plus sign and the flag emoji. */
data class Country(val iso: String, val name: String, val dialCode: String, val flagEmoji: String) {
    /** Digits of the dial code without the plus sign. */
    val dialDigits: String get() = dialCode.drop(1)
}

/**
 * Complete country list for the sign-up phone field, plus helpers that follow the international prefix
 * (+92, 0092 ...) of a number that was typed or pasted.
 */
object CountryCodes {
    // ISO region : dial digits. Caribbean (+1 xxx) countries keep their area code so every entry has its own dial code.
    private const val RAW =
        "AF:93,AL:355,DZ:213,AS:1684,AD:376,AO:244,AI:1264,AG:1268,AR:54,AM:374,AW:297,AU:61,AT:43,AZ:994,BS:1242,BH:973," +
            "BD:880,BB:1246,BY:375,BE:32,BZ:501,BJ:229,BM:1441,BT:975,BO:591,BA:387,BW:267,BR:55,IO:246,VG:1284,BN:673,BG:359," +
            "BF:226,BI:257,KH:855,CM:237,CA:1,CV:238,KY:1345,CF:236,TD:235,CL:56,CN:86,CO:57,KM:269,CK:682,CR:506,HR:385," +
            "CU:53,CW:599,CY:357,CZ:420,CD:243,DK:45,DJ:253,DM:1767,DO:1809,TL:670,EC:593,EG:20,SV:503,GQ:240,ER:291,EE:372," +
            "SZ:268,ET:251,FK:500,FO:298,FJ:679,FI:358,FR:33,GF:594,PF:689,GA:241,GM:220,GE:995,DE:49,GH:233,GI:350,GR:30," +
            "GL:299,GD:1473,GP:590,GU:1671,GT:502,GN:224,GW:245,GY:592,HT:509,HN:504,HK:852,HU:36,IS:354,IN:91,ID:62,IR:98," +
            "IQ:964,IE:353,IL:972,IT:39,CI:225,JM:1876,JP:81,JO:962,KZ:7,KE:254,KI:686,XK:383,KW:965,KG:996,LA:856,LV:371," +
            "LB:961,LS:266,LR:231,LY:218,LI:423,LT:370,LU:352,MO:853,MG:261,MW:265,MY:60,MV:960,ML:223,MT:356,MH:692,MQ:596," +
            "MR:222,MU:230,MX:52,FM:691,MD:373,MC:377,MN:976,ME:382,MS:1664,MA:212,MZ:258,MM:95,NA:264,NR:674,NP:977,NL:31," +
            "NC:687,NZ:64,NI:505,NE:227,NG:234,NU:683,KP:850,MK:389,MP:1670,NO:47,OM:968,PK:92,PW:680,PS:970,PA:507,PG:675," +
            "PY:595,PE:51,PH:63,PL:48,PT:351,PR:1787,QA:974,CG:242,RE:262,RO:40,RU:7,RW:250,KN:1869,LC:1758,VC:1784,WS:685," +
            "SM:378,ST:239,SA:966,SN:221,RS:381,SC:248,SL:232,SG:65,SX:1721,SK:421,SI:386,SB:677,SO:252,ZA:27,KR:82,SS:211," +
            "ES:34,LK:94,SD:249,SR:597,SE:46,CH:41,SY:963,TW:886,TJ:992,TZ:255,TH:66,TG:228,TK:690,TO:676,TT:1868,TN:216," +
            "TR:90,TM:993,TC:1649,TV:688,UG:256,UA:380,AE:971,GB:44,US:1,VI:1340,UY:598,UZ:998,VU:678,VA:379,VE:58,VN:84," +
            "WF:681,YE:967,ZM:260,ZW:263"

    private fun flag(iso: String): String = try {
        iso.uppercase(Locale.ROOT).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    } catch (_: Throwable) {
        ""
    }

    /** Every country, sorted by name. */
    val all: List<Country> by lazy {
        RAW.split(",").mapNotNull { e ->
            val iso = e.substringBefore(":")
            val dial = e.substringAfter(":")
            val name = Locale("", iso).getDisplayCountry(Locale.ENGLISH).ifBlank { iso }
            if (iso.length != 2 || dial.isEmpty()) null else Country(iso, name, "+$dial", flag(iso))
        }.sortedBy { it.name }
    }

    fun byIso(iso: String?): Country? = if (iso.isNullOrBlank()) null else all.firstOrNull { it.iso.equals(iso, ignoreCase = true) }

    /** The country of the phone's region, or Pakistan (+92) when it is not known. */
    fun default(): Country = byIso(Locale.getDefault().country) ?: byIso("PK") ?: all.first()

    /** Search by name, ISO code or dial code ("pak", "PK", "+92", "92"). */
    fun search(query: String): List<Country> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return all
        val digits = q.removePrefix("+")
        return all.filter {
            it.name.lowercase(Locale.ROOT).contains(q) || it.iso.lowercase(Locale.ROOT) == q ||
                (digits.isNotEmpty() && digits.all { c -> c.isDigit() } && it.dialDigits.startsWith(digits))
        }
    }

    /**
     * Reads a number that starts with + or 00. Returns the matching country and the rest of the number
     * (without the dial code), or null when the text has no recognised international prefix.
     * [current] wins when several countries share the same code (for example +1 or +7).
     */
    fun detect(input: String, current: Country?): Pair<Country, String>? {
        var t = input.trim().replace(" ", "").replace("-", "")
        if (t.startsWith("00")) t = "+" + t.drop(2)
        if (!t.startsWith("+")) return null
        val digits = t.drop(1).takeWhile { it.isDigit() }
        // Only codes up to three digits are detected; Caribbean area codes are chosen from the list.
        for (len in 3 downTo 1) {
            if (digits.length < len) continue
            val code = digits.take(len)
            val matches = all.filter { it.dialDigits == code }
            if (matches.isEmpty()) continue
            val c = matches.firstOrNull { it.iso == current?.iso } ?: preferred(matches)
            return c to digits.drop(len)
        }
        return null
    }

    // The most common country for a shared code.
    private fun preferred(list: List<Country>): Country =
        list.firstOrNull { it.iso in setOf("US", "RU", "GB", "IT", "FR", "NO", "AU", "FI", "MA") } ?: list.first()

    /** Kept for older callers: English country name of a number that starts with + or 00. */
    fun fromPhone(input: String): String? = detect(input, null)?.first?.name
}
