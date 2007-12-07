package org.unicode.cldr.tool;

import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XMLFileReader;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchXml {
  private static Matcher fileMatcher;

  private static Matcher pathMatcher;

  private static Matcher valueMatcher;
  
  private static boolean showFiles;
  private static boolean showValues;
  
  private static int count = 0;

  public static void main(String[] args) throws IOException {
    String sourceDirectory = Utility.getProperty("SOURCE", null);
    if (sourceDirectory == null) {
      System.out.println("Need Source Directory! ");
      return;
    }
    fileMatcher = Pattern.compile(Utility.getProperty("FILE", ".*")).matcher("");
    pathMatcher = Pattern.compile(Utility.getProperty("XMLPATH", ".*")).matcher("");
    valueMatcher = Pattern.compile(Utility.getProperty("VALUE", ".*"), Pattern.DOTALL)
        .matcher("");

    showFiles = Utility.getProperty("SHOWFILES", "FALSE","FALSE").equalsIgnoreCase("true");
    showValues = Utility.getProperty("SHOWVALUES", "FALSE","FALSE").equalsIgnoreCase("true");

    double startTime = System.currentTimeMillis();
    File src = new File(sourceDirectory);
    processDirectory(src);

    double deltaTime = System.currentTimeMillis() - startTime;
    System.out.println("Elapsed: " + deltaTime / 1000.0 + " seconds");
    System.out.println("Instances found: " + count);
  }

  private static void processDirectory(File src) throws IOException {
    for (File file : src.listFiles()) {
      if (file.isDirectory()) {
        processDirectory(file);
        continue;
      }
      if (file.length() == 0)
        continue;
      String canonicalFile = file.getCanonicalPath();
      if (!fileMatcher.reset(canonicalFile).matches())
        continue;
      if (showFiles) {
        System.out.println("* " + canonicalFile);
      }
      myHandler.firstMessage = "* " + canonicalFile;
      XMLFileReader xfr = new XMLFileReader().setHandler(myHandler);
      xfr.read(canonicalFile, XMLFileReader.CONTENT_HANDLER
          | XMLFileReader.ERROR_HANDLER, false);
      System.out.flush();
    }
  }

  static MyHandler myHandler = new MyHandler();
  
  static class MyHandler extends XMLFileReader.SimpleHandler {
    String firstMessage;
    
    public void handlePathValue(String path, String value) {
      
      if (pathMatcher.reset(path).matches()) {
        
        if (showValues) {
          System.out.println(value + "\t<=\t" + path);
        }

        if (valueMatcher.reset(value).matches()) {
          if (firstMessage != null) {
            System.out.println(firstMessage);
            firstMessage = null;
          }
          System.out.println(path + "\t" + value);
          ++count;
        }
      }
    }
  }
}