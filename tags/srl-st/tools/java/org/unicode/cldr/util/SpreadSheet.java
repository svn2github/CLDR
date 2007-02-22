package org.unicode.cldr.util;
import com.ibm.icu.dev.test.util.BagFormatter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class SpreadSheet {
  public static List<List<String>> convert(String filename) throws IOException {
    return convert(BagFormatter.openUTF8Reader("", filename));
  }
  public static List<List<String>> convert(BufferedReader r) throws IOException {
    List<List<String>> result = new ArrayList<List<String>>();
    boolean inQuote = false;
    while (true) {
      String line = r.readLine();
      if (line == null) break;
      String[] parts = line.split("\t");
      List<String> row = new ArrayList<String>(parts.length);
      for (String part : parts) {
        if (part.startsWith("\"") && part.endsWith("\"")) {
          row.add(part.substring(1,part.length()-1));
        } else {
          row.add(part);
        }
      }
      result.add(row);
    }
    return result;
  }
}