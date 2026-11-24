package com.anas.streamsnap.util

import java.util.Locale

/** Finds the country from the international prefix of a phone number (for example +92 gives Pakistan). */
object CountryCodes {
    // dial code -> ISO region. "+1" is shown as United States.
    private val dial: Map<String, String> = (
        "1:US,7:RU,20:EG,27:ZA,30:GR,31:NL,32:BE,33:FR,34:ES,36:HU,39:IT,40:RO,41:CH,43:AT,44:GB,45:DK,46:SE,47:NO,48:PL,49:DE," +
            "51:PE,52:MX,53:CU,54:AR,55:BR,56:CL,57:CO,58:VE,60:MY,61:AU,62:ID,63:PH,64:NZ,65:SG,66:TH,81:JP,82:KR,84:VN,86:CN," +
            "90:TR,91:IN,92:PK,93:AF,94:LK,95:MM,98:IR,211:SS,212:MA,213:DZ,216:TN,218:LY,220:GM,221:SN,233:GH,234:NG,249:SD," +
            "251:ET,254:KE,255:TZ,256:UG,260:ZM,263:ZW,351:PT,353:IE,354:IS,358:FI,359:BG,370:LT,371:LV,372:EE,380:UA,381:RS," +
            "385:HR,420:CZ,421:SK,673:BN,852:HK,853:MO,855:KH,856:LA,880:BD,886:TW,960:MV,961:LB,962:JO,963:SY,964:IQ,965:KW," +
            "966:SA,967:YE,968:OM,970:PS,971:AE,972:IL,973:BH,974:QA,975:BT,976:MN,977:NP,992:TJ,993:TM,994:AZ,995:GE,996:KG,998:UZ"
        ).split(",").associate { it.substringBefore(":") to it.substringAfter(":") }

    /** Returns the English country name for a number that starts with + (or 00), or null when it is not recognised yet. */
    fun fromPhone(input: String): String? {
        var t = input.trim().replace(" ", "")
        if (t.startsWith("00")) t = "+" + t.drop(2)
        if (!t.startsWith("+")) return null
        val digits = t.drop(1).takeWhile { it.isDigit() }
        for (len in 3 downTo 1) {
            if (digits.length >= len) {
                val iso = dial[digits.take(len)] ?: continue
                val name = Locale("", iso).getDisplayCountry(Locale.ENGLISH)
                if (name.isNotBlank()) return name
            }
        }
        return null
    }
}
