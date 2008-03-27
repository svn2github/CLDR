package org.unicode.cldr.unittest;

import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Factory;

import com.ibm.icu.dev.test.TestFmwk;

public class TestLocale extends TestFmwk {
  static TestInfo testInfo = new TestInfo();

  public static void main(String[] args) {
    new TestLocale().run(args);
  }

  public void TestLocaleNamePattern() {
    assertEquals("Locale name", "Chinese", testInfo.english.getName("zh"));
    assertEquals("Locale name", "Chinese (United States)", testInfo.english.getName("zh-US"));
    assertEquals("Locale name", "Chinese (Arabic, United States)", testInfo.english.getName("zh-Arab-US"));
    CLDRFile japanese = testInfo.cldrFactory.make("ja", true);
    assertEquals("Locale name", "中国語", japanese.getName("zh"));
    assertEquals("Locale name", "中国語（アメリカ合衆国）", japanese.getName("zh-US"));
    assertEquals("Locale name", "中国語（アラビア文字、アメリカ合衆国）", japanese.getName("zh-Arab-US"));
  }
}