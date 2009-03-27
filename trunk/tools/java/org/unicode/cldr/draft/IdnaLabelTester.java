package org.unicode.cldr.draft;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.util.Utility.Transform;

import com.ibm.icu.dev.test.util.VariableReplacer;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.text.UnicodeSet;

public class IdnaLabelTester {

  enum Result {none, next, fail};

  static class Rule {
    Matcher before;
    Matcher at;
    Result result;
    String title;

    public Rule(String before, String at, String result, String title) {
      if (before != null) {
        before = before.trim();
      }
      this.before = before == null || before == "" ? null 
              : Pattern.compile(".*" + before, Pattern.COMMENTS).matcher(""); // hack, because Java doesn't have lookingBefore
      this.at = Pattern.compile(at.trim(), Pattern.COMMENTS).matcher("");
      this.result = Result.valueOf(result.toLowerCase().trim());
      this.title = title;
    }
    public Result match(String label, int position) {
      if (before != null) {
        before.reset(label);
        before.region(0, position);
      }
      at.reset(label);
      at.region(position, label.length());
      if (at.lookingAt()) return result;
      return Result.none;
    }
  }

  private List<Rule> rules = new ArrayList<Rule>();

  public IdnaLabelTester(String file) throws IOException {

    VariableReplacer variables = new VariableReplacer();
    BufferedReader in = openFile(file);
    
    String title = "???";
    for (int lineCount = 0; ; ++lineCount) {
      String line = in.readLine();
      if (line == null) break;
      int commentPos = line.indexOf("#");
      if (commentPos >= 0) {
        line = line.substring(0,commentPos);
      }
      line = line.trim();
      if (line.length() == 0) continue;
      
      // do title
      
      if (line.startsWith("Title:")) {
        title = line.substring(6).trim();
        continue;
      }
      
      // do variables
      
      if (line.startsWith("$")) {
        int equals = line.indexOf("=");
        if (equals >= 0) {
          final String variable = line.substring(0,equals).trim();
          UnicodeSet s = new UnicodeSet(line.substring(equals+1).trim()).complement().complement();
          variables.add(variable, s.toPattern(false));
          continue;
        }
      }
      
      // do rules. This could be much more compact, but is broken out for debugging
      
      String[] pieces = line.split("\\s*;\\s*");
      System.out.println(Arrays.asList(pieces));
      String before, at, result;
      switch (pieces.length) {
        case 2: before = null; at = variables.replace(pieces[0]); result= pieces[1]; break;
        case 3: before = variables.replace(pieces[0]); at = variables.replace(pieces[1]); result= pieces[2]; break;
        default: throw new IllegalArgumentException(line + " => " + Arrays.asList(pieces));
      }
      Rule rule = new Rule(before, at, result, title);
      rules.add(rule);
    }
    in.close();
  }

  private static BufferedReader openFile(String file) throws IOException {
    try {
      return GenerateNormalizeForMatch.openUTF8Reader(file);
    } catch (Exception e) {
      File f = new File(file);
      throw new IllegalArgumentException("Bad file name: " + f.getCanonicalPath());
    }
  }
  
  static class TestStatus {
    String title;
    int position;
    public TestStatus(int position, String title) {
      this.position = position;
      this.title = title;
    }
  }
  
  /**
   * Test a label; null for success.
   * Later, return information.
   * @param label
   * @return
   */
  
  public TestStatus test(String label) {
    charLoop:
    for (int i= 0; i < label.length(); ++i) {
      for (Rule rule : rules) {
        Result result = rule.match(label, i);
        switch (result) {
          case next: continue charLoop;
          case fail: return new TestStatus(i, rule.title);
        }
      }
    }
    return null; // success!
  }
  
  public static void main(String[] args) throws IOException {
    String dir = "java/org/unicode/cldr/draft/";
    IdnaLabelTester tester = new IdnaLabelTester(dir + "idnaContextRules.txt");
    Transliterator unescaper = Transliterator.getInstance("hex-any");
    BufferedReader in = openFile(dir + "idnaTestCases.txt");
    boolean expectedSuccess = true;
    
    for (int lineCount = 0; ; ++lineCount) {
      String line = in.readLine();
      if (line == null) break;
      int commentPos = line.indexOf("#");
      if (commentPos >= 0) {
        line = line.substring(0,commentPos);
      }
      line = unescaper.transform(line);
      
      line = line.trim();
      if (line.length() == 0) continue;
      
      if ("valid".equalsIgnoreCase(line)) {
        expectedSuccess = true;
        continue;
      } else if ("invalid".equalsIgnoreCase(line)) {
        expectedSuccess = false;
        continue;
      }

      TestStatus result = tester.test(line);
      if (result == null) {
        if (expectedSuccess) {
          System.out.println("Success - expected Valid, got it:\t" + line);
        } else {
          System.out.println("FAILURE - expected Invalid, was valid:\t" + line);
        }
      } else {
        if (expectedSuccess) {
          System.out.println("FAILURE - expected Valid, was invalid:\t" + line);
        } else {
          System.out.println("Success - expected Invalid, got it:\t" + line.substring(0, result.position) + "$" + line.substring(result.position) + "\t\t" + result.title);
        }
      }
    }
    in.close();
  }
}
