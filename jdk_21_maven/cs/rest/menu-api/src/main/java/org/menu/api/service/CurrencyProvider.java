package org.menu.api.service;

import org.menu.api.dto.CurrencyDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The ISO 4217 list the .NET original carried, verbatim. Static data; nothing is stored. */
@Service
public class CurrencyProvider {

    private static final Map<String, CurrencyDto> BY_CODE = new LinkedHashMap<>();

    private static void put(String code, String name) {
        BY_CODE.put(code, new CurrencyDto(code, name));
    }

    static {
        put("AED", "United Arab Emirates Dirham");
        put("AFN", "Afghanistan Afghani");
        put("ALL", "Albania Lek");
        put("AMD", "Armenia Dram");
        put("ANG", "Netherlands Antilles Guilder");
        put("AOA", "Angola Kwanza");
        put("ARS", "Argentina Peso");
        put("AUD", "Australia Dollar");
        put("AWG", "Aruba Guilder");
        put("AZN", "Azerbaijan New Manat");
        put("BAM", "Bosnia and Herzegovina Convertible Marka");
        put("BBD", "Barbados Dollar");
        put("BDT", "Bangladesh Taka");
        put("BGN", "Bulgaria Lev");
        put("BHD", "Bahrain Dinar");
        put("BIF", "Burundi Franc");
        put("BMD", "Bermuda Dollar");
        put("BND", "Brunei Darussalam Dollar");
        put("BOB", "Bolivia Boliviano");
        put("BRL", "Brazil Real");
        put("BSD", "Bahamas Dollar");
        put("BTN", "Bhutan Ngultrum");
        put("BWP", "Botswana Pula");
        put("BYR", "Belarus Ruble");
        put("BZD", "Belize Dollar");
        put("CAD", "Canada Dollar");
        put("CDF", "Congo/Kinshasa Franc");
        put("CHF", "Switzerland Franc");
        put("CLP", "Chile Peso");
        put("CNY", "China Yuan Renminbi");
        put("COP", "Colombia Peso");
        put("CRC", "Costa Rica Colon");
        put("CUC", "Cuba Convertible Peso");
        put("CUP", "Cuba Peso");
        put("CVE", "Cape Verde Escudo");
        put("CZK", "Czech Republic Koruna");
        put("DJF", "Djibouti Franc");
        put("DKK", "Denmark Krone");
        put("DOP", "Dominican Republic Peso");
        put("DZD", "Algeria Dinar");
        put("EGP", "Egypt Pound");
        put("ERN", "Eritrea Nakfa");
        put("ETB", "Ethiopia Birr");
        put("EUR", "Euro Member Countries");
        put("FJD", "Fiji Dollar");
        put("FKP", "Falkland Islands (Malvinas) Pound");
        put("GBP", "United Kingdom Pound");
        put("GEL", "Georgia Lari");
        put("GGP", "Guernsey Pound");
        put("GHS", "Ghana Cedi");
        put("GIP", "Gibraltar Pound");
        put("GMD", "Gambia Dalasi");
        put("GNF", "Guinea Franc");
        put("GTQ", "Guatemala Quetzal");
        put("GYD", "Guyana Dollar");
        put("HKD", "Hong Kong Dollar");
        put("HNL", "Honduras Lempira");
        put("HRK", "Croatia Kuna");
        put("HTG", "Haiti Gourde");
        put("HUF", "Hungary Forint");
        put("IDR", "Indonesia Rupiah");
        put("ILS", "Israel Shekel");
        put("IMP", "Isle of Man Pound");
        put("INR", "India Rupee");
        put("IQD", "Iraq Dinar");
        put("IRR", "Iran Rial");
        put("ISK", "Iceland Krona");
        put("JEP", "Jersey Pound");
        put("JMD", "Jamaica Dollar");
        put("JOD", "Jordan Dinar");
        put("JPY", "Japan Yen");
        put("KES", "Kenya Shilling");
        put("KGS", "Kyrgyzstan Som");
        put("KHR", "Cambodia Riel");
        put("KMF", "Comoros Franc");
        put("KPW", "Korea (North) Won");
        put("KRW", "Korea (South) Won");
        put("KWD", "Kuwait Dinar");
        put("KYD", "Cayman Islands Dollar");
        put("KZT", "Kazakhstan Tenge");
        put("LAK", "Laos Kip");
        put("LBP", "Lebanon Pound");
        put("LKR", "Sri Lanka Rupee");
        put("LRD", "Liberia Dollar");
        put("LSL", "Lesotho Loti");
        put("LYD", "Libya Dinar");
        put("MAD", "Morocco Dirham");
        put("MDL", "Moldova Leu");
        put("MGA", "Madagascar Ariary");
        put("MKD", "Macedonia Denar");
        put("MMK", "Myanmar (Burma) Kyat");
        put("MNT", "Mongolia Tughrik");
        put("MOP", "Macau Pataca");
        put("MRO", "Mauritania Ouguiya");
        put("MUR", "Mauritius Rupee");
        put("MVR", "Maldives (Maldive Islands) Rufiyaa");
        put("MWK", "Malawi Kwacha");
        put("MXN", "Mexico Peso");
        put("MYR", "Malaysia Ringgit");
        put("MZN", "Mozambique Metical");
        put("NAD", "Namibia Dollar");
        put("NGN", "Nigeria Naira");
        put("NIO", "Nicaragua Cordoba");
        put("NOK", "Norway Krone");
        put("NPR", "Nepal Rupee");
        put("NZD", "New Zealand Dollar");
        put("OMR", "Oman Rial");
        put("PAB", "Panama Balboa");
        put("PEN", "Peru Nuevo Sol");
        put("PGK", "Papua New Guinea Kina");
        put("PHP", "Philippines Peso");
        put("PKR", "Pakistan Rupee");
        put("PLN", "Poland Zloty");
        put("PYG", "Paraguay Guarani");
        put("QAR", "Qatar Riyal");
        put("RON", "Romania New Leu");
        put("RSD", "Serbia Dinar");
        put("RUB", "Russia Ruble");
        put("RWF", "Rwanda Franc");
        put("SAR", "Saudi Arabia Riyal");
        put("SBD", "Solomon Islands Dollar");
        put("SCR", "Seychelles Rupee");
        put("SDG", "Sudan Pound");
        put("SEK", "Sweden Krona");
        put("SGD", "Singapore Dollar");
        put("SHP", "Saint Helena Pound");
        put("SLL", "Sierra Leone Leone");
        put("SOS", "Somalia Shilling");
        put("SPL", "Seborga Luigino");
        put("SRD", "Suriname Dollar");
        put("STD", "São Tomé and Príncipe Dobra");
        put("SVC", "El Salvador Colon");
        put("SYP", "Syria Pound");
        put("SZL", "Swaziland Lilangeni");
        put("THB", "Thailand Baht");
        put("TJS", "Tajikistan Somoni");
        put("TMT", "Turkmenistan Manat");
        put("TND", "Tunisia Dinar");
        put("TOP", "Tonga Pa'anga");
        put("TRY", "Turkey Lira");
        put("TTD", "Trinidad and Tobago Dollar");
        put("TVD", "Tuvalu Dollar");
        put("TWD", "Taiwan New Dollar");
        put("TZS", "Tanzania Shilling");
        put("UAH", "Ukraine Hryvnia");
        put("UGX", "Uganda Shilling");
        put("USD", "United States Dollar");
        put("UYU", "Uruguay Peso");
        put("UZS", "Uzbekistan Som");
        put("VEF", "Venezuela Bolivar");
        put("VND", "Viet Nam Dong");
        put("VUV", "Vanuatu Vatu");
        put("WST", "Samoa Tala");
        put("XAF", "Communauté Financière Africaine (BEAC) CFA Franc BEAC");
        put("XCD", "East Caribbean Dollar");
        put("XDR", "International Monetary Fund (IMF) Special Drawing Rights");
        put("XOF", "Communauté Financière Africaine (BCEAO) Franc");
        put("XPF", "Comptoirs Français du Pacifique (CFP) Franc");
        put("YER", "Yemen Rial");
        put("ZAR", "South Africa Rand");
        put("ZMW", "Zambia Kwacha");
        put("ZWD", "Zimbabwe Dollar");
    }

    public List<CurrencyDto> getAll() {
        return List.copyOf(BY_CODE.values());
    }

    /** Case insensitive, the way the original was. */
    public CurrencyDto getByCode(String code) {
        if (code == null) {
            return null;
        }
        return BY_CODE.get(code.toUpperCase(Locale.ROOT));
    }

    public boolean isKnown(String code) {
        return getByCode(code) != null;
    }
}
