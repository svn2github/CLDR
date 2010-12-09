package org.unicode.cldr.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class IsoRegionData {
  static Map<String, String> _numeric = new HashMap<String,String>();
  static Map<String, String> _alpha3 = new HashMap<String,String>();
  static Map<String, String> _fips10 = new HashMap<String,String>();
  static Map<String, String> _internet = new HashMap<String,String>();
  static {
    /*
# RFC3066; UN Numeric; ISO3166 Alpha-3, internet, FIPS-10
# whitespace delimited: - for empty
# See http://unstats.un.org/unsd/methods/m49/m49regin.htm
# and http://www.iso.org/iso/en/prods-services/iso3166ma/01whats-new/index.html
# See also http://www.cia.gov/cia/publications/factbook/appendix/appendix-d.html
#      and http://data.iana.org/TLD/tlds-alpha-by-domain.txt for the latest domains
#      and http://www.iana.org/cctld/cctld-whois.htm
#      and https://www.icmwg.org/ccwg/documents/ISO3166-FIPS10-A2-Mapping/3166-1-A2--to-FIPS10-A2-mapping.htm
#      for FIPS: http://earth-info.nga.mil/gns/html/fips_files.html
RS  688 SRB rs  RB

     */
    try {
      BufferedReader codes = CldrUtility.getUTF8Data("territory_codes.txt");
      while (true) {
        String line = codes.readLine();
        if (line == null)
          break;
        line = line.split("#")[0].trim();
        if (line.length() == 0)
          continue;
        String[] sourceValues = line.split("\\s+");
        String[] values = new String[5];
        for (int i = 0; i < values.length; ++i) {
          if (i >= sourceValues.length || sourceValues[i].equals("-")) {
            values[i] = null;
          } else {
            values[i] = sourceValues[i];
          }
        }
        String alpha2 = values[0];
        String numeric = values[1];
        String alpha3 = values[2];
        String internet = values[3];
        if (internet != null) {
          internet = internet.replace("/", " ");
        }
        if (internet != null)
          internet = internet.toUpperCase();
        String fips10 = values[4];
        _numeric.put(alpha2, numeric);
        _alpha3.put(alpha2, alpha3);
        _fips10.put(alpha2, fips10);
        _internet.put(alpha2, numeric);
      }
      codes.close();
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
    _numeric = Collections.unmodifiableMap(_numeric);
    _alpha3 = Collections.unmodifiableMap(_alpha3);
    _fips10 = Collections.unmodifiableMap(_fips10);
    _internet = Collections.unmodifiableMap(_internet);
  }
  public static String getNumeric(String countryCodeAlpha2) {
    return _numeric.get(countryCodeAlpha2);
  }
  public static String get_alpha3(String countryCodeAlpha2) {
    return _alpha3.get(countryCodeAlpha2);
  }
  public static String get_fips10(String countryCodeAlpha2) {
    return _fips10.get(countryCodeAlpha2);
  }
  public static String get_internet(String countryCodeAlpha2) {
    return _internet.get(countryCodeAlpha2);
  }
}

