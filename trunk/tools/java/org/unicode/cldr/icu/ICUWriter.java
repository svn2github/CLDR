// Copyright 2009 Google Inc. All Rights Reserved.

package org.unicode.cldr.icu;

import com.ibm.icu.util.ULocale;

import static org.unicode.cldr.icu.ICUID.*;

import org.unicode.cldr.ant.CLDRConverterTool.Alias;
import org.unicode.cldr.icu.ICUResourceWriter.Resource;
import org.unicode.cldr.icu.ICUResourceWriter.ResourceString;
import org.unicode.cldr.icu.ICUResourceWriter.ResourceTable;
import org.unicode.cldr.icu.LDML2ICUConverter.LDMLServices;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.LDMLUtilities;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

class ICUWriter {
  private static final String LINESEP = System.getProperty("line.separator");
  private static final String BOM = "\uFEFF";
  private static final String CHARSET = "UTF-8";
  
  private static final String DEPRECATED_LIST = "icu-config.xml & build.xml";

  private final LDMLServices services;
  private final String dstDirName;
  private final ICULog log;

  ICUWriter(LDMLServices services, String dstDirName, ICULog log) {
    this.services = services;
    this.dstDirName = dstDirName;
    this.log = log;
  }

  public void writeResource(Resource res, String sourceFileName) {
    String outputFileName = dstDirName + "/" + res.name + ".txt";
    writeResource(res, sourceFileName, outputFileName);
  }

  public void writeResource(Resource set, String sourceFileName, String outputFileName) {
    try {
      System.out.println("Writing " + outputFileName);
      FileOutputStream file = new FileOutputStream(outputFileName);
      BufferedOutputStream writer = new BufferedOutputStream(file);
      writeHeader(writer, sourceFileName);

      Resource current = set;
      while (current != null) {
        current.sort();
        current = current.next;
      }

      current = set;
      while (current != null) {
        current.write(writer, 0, false);
        current = current.next;
      }
      writer.flush();
      writer.close();
    } catch (Resource.MalformedResourceError mre) {
      String where = set.findResourcePath(mre.offendingResource);
      System.err.println(
          sourceFileName + ": ERROR (writing resource " + where + ") :" + mre.toString());
      mre.printStackTrace();
      if (new File(outputFileName).delete()) {
        System.err.println("## Deleted partial file: " + outputFileName);
      }
      System.exit(1);
      return; // NOTREACHED
    } catch (Exception ie) {
      System.err.println(sourceFileName + ": ERROR (writing resource) :" + ie.toString());
      ie.printStackTrace();
      if (new File(outputFileName).delete()) {
        System.err.println("## Deleted partial file: " + outputFileName);
      }
      System.exit(1);
      return; // NOTREACHED
    }
  }

  private void writeLine(OutputStream writer, String line) {
    try {
      byte[] bytes = line.getBytes(CHARSET);
      writer.write(bytes, 0, bytes.length);
    } catch (Exception e) {
      System.err.println(e);
      System.exit(1);
    }
  }

  private void writeHeader(OutputStream writer, String fileName) {
    writeBOM(writer);
    Calendar c = Calendar.getInstance();
    StringBuilder buffer = new StringBuilder();
    buffer.append("// ***************************************************************************")
    .append(LINESEP)
    .append("// *")
    .append(LINESEP)
    .append("// * Copyright (C) ")
    .append(c.get(Calendar.YEAR))
    .append(" International Business Machines")
    .append(LINESEP)
    .append("// * Corporation and others.  All Rights Reserved.")
    .append(LINESEP)
    .append("// * Tool: com.ibm.icu.dev.tool.cldr.LDML2ICUConverter.java")
    .append(LINESEP);
    // buffer.append("// * Date & Time: ")
    // .append(c.get(Calendar.YEAR))
    // .append("/")
    // .append(c.get(Calendar.MONTH) + 1)
    // .append("/")
    // .append(c.get(Calendar.DAY_OF_MONTH))
    // .append(" ")
    // .append(c.get(Calendar.HOUR_OF_DAY))
    // .append(COLON)
    // .append(c.get(Calendar.MINUTE))
    // .append(LINESEP);
    //         String ver = LDMLUtilities.getCVSVersion(fileName);
    //         if (ver == null) {
    //             ver = "";
    //         } else {
    //             ver = " v" + ver;
    //         }

    String tempdir = fileName.replace('\\','/');
    int index = tempdir.indexOf("/common");
    if (index > -1) {
      tempdir = "<path>" + tempdir.substring(index, tempdir.length());
    } else {
      index = tempdir.indexOf("/xml");
      if (index > -1) {
        tempdir = "<path>" + tempdir.substring(index, tempdir.length());
      } else {
        tempdir = "<path>/" + tempdir;
      }
    }
    buffer.append("// * Source File:" + tempdir)
    .append(LINESEP)
    .append("// *")
    .append(LINESEP)
    .append("// ***************************************************************************")
    .append(LINESEP);
    writeLine(writer, buffer.toString());
  }

  private void writeBOM(OutputStream buffer) {
    try {
      byte[] bytes = BOM.getBytes(CHARSET);
      buffer.write(bytes, 0, bytes.length);
    } catch(Exception e) {
      System.err.println(e);
      System.exit(1);
    }
  }

  public void writeDeprecated(File depDir, File dstDir, List<String> emptyLocaleList, 
      Map<String, Alias> aliasMap, List<String> aliasLocaleList, boolean parseDraft,
      boolean parseSubLocale) {
    String myTreeName = depDir.getName();
    final File[] destFiles = dstDir.listFiles();

    // parse a bunch of locales?
    boolean parseThem = parseDraft || parseSubLocale;

    // ex: "ji" -> "yi"
    TreeMap<String, String> fromToMap = new TreeMap<String, String>();

    // ex:  "th_TH_TRADITIONAL" -> "@some xpath.."
    TreeMap<String, String> fromXpathMap = new TreeMap<String, String>();

    // ex:  "mt.xml" -> File .  Ordinary XML source files
    Map<String, File> fromFiles = new TreeMap<String, File>();

    // ex:  "en_US.xml" -> File .  empty files generated by validSubLocales
    Map<String, File> emptyFromFiles = new TreeMap<String, File>();

    // ex:  th_TH_TRADITIONAL.xml -> File  Files generated directly from the alias list
    // (no XML actually exists).
    Map<String, File> generatedAliasFiles = new TreeMap<String, File>();

    // ex: zh_MO.xml -> File  Files which actually exist in LDML and contain aliases
    Map<String, File> aliasFromFiles = new TreeMap<String, File>();

    // en -> "en_US en_GB ..."
    TreeMap<String, String> validSubMap = new TreeMap<String, String>();

    // for in -> id where id is a synthetic alias
    TreeMap<String, String> maybeValidAlias = new TreeMap<String, String>();

    // 1. get the list of input XML files
    FileFilter myFilter = new FileFilter() {
      public boolean accept(File f) {
        String n = f.getName();
        return !f.isDirectory()
            && n.endsWith(".xml")
            && !n.startsWith("supplementalData") // not a locale
            /* &&!n.startsWith("root") */
            && isInDest(n); // root is implied, will be included elsewhere.
      }

      public boolean isInDest(String n) {
        String name = n.substring(0, n.indexOf('.') + 1);
        for (int i = 0; i < destFiles.length; i++) {
          String dest = destFiles[i].getName();
          if (dest.indexOf(name) == 0) {
            return true;
          }
        }

        return false;
      }
    };

    File inFiles[] = depDir.listFiles(myFilter);

    int nrInFiles = inFiles.length;
    if (parseThem) {
      System.out.println(
          "Parsing: " + nrInFiles + " LDML locale files to check " + (parseDraft ? "draft, " : "")
          + (parseSubLocale ? "valid-sub-locales, " : ""));
    }

    for (int i = 0; i < nrInFiles; i++) {
      if (i > 0 && (i % 60 == 0)) {
        System.out.println(" " + i);
        System.out.flush();
      }
      boolean thisOK = true;
      String localeName = inFiles[i].getName();
      localeName = localeName.substring(0, localeName.indexOf('.'));
      if (parseThem) {
        try {
          Document doc2 = LDMLUtilities.parse(inFiles[i].toString(), false);
          // TODO: figure out if this is really required
          if (parseDraft && LDMLUtilities.isLocaleDraft(doc2)) {
            thisOK = false;
          }
          if (thisOK && parseSubLocale) {
            Node collations = LDMLUtilities.getNode(doc2, "//ldml/collations");
            if (collations != null) {
              String vsl = LDMLUtilities.getAttributeValue(collations, "validSubLocales");
              if (vsl != null && vsl.length() > 0) {
                validSubMap.put(localeName, vsl);
                log.info(localeName + " <- " + vsl);
              }
            }
          }
        } catch (Throwable t) {
          System.err.println("While parsing " + inFiles[i].toString() + " - ");
          System.err.println(t.toString());
          t.printStackTrace(System.err);
          System.exit(-1); // TODO: should be full 'parser error'stuff.
        }
      }

      if (!localeName.equals("root")) {
        // System.out.println("FN put " + inFiles[i].getName());
        if (thisOK) {
          System.out.print("."); // regular file
          fromFiles.put(inFiles[i].getName(), inFiles[i]); // add to hash
        } else {
          if (services.isDraftStatusOverridable(localeName)) {
            fromFiles.put(inFiles[i].getName(), inFiles[i]); // add to hash
            System.out.print("o"); // override
            // System.out.print("[o:" + localeName + "]");
          } else {
            System.out.print("d"); // draft
            // System.out.print("[d:" + localeName + "]");
          }
        }
      } else {
        System.out.print("_");
      }
    }

    if (parseThem == true) {
      // end the debugging line
      System.out.println("");
    }
    // End of parsing all XML files.

    if (emptyLocaleList != null && emptyLocaleList.size() > 0) {
      for (int i = 0; i < emptyLocaleList.size(); i++) {
        String loc = emptyLocaleList.get(i);
        writeSimpleLocale(
            loc + ".txt", loc, null, null, "empty locale file for dependency checking");
        // we do not want these files to show up in installed locales list!
        generatedAliasFiles.put(loc + ".xml", new File(depDir, loc + ".xml"));
      }
    }

    // interpret the deprecated locales list
    if (aliasMap != null && aliasMap.size() > 0) {
      for (Iterator<String> i = aliasMap.keySet().iterator(); i.hasNext();) {
        String from = i.next();
        Alias value = aliasMap.get(from);
        String to = value.to;
        String xpath = value.xpath;
        if (to.indexOf('@') != -1 && xpath == null) {
          System.err.println(
              "Malformed alias - '@' but no xpath: from=\"" + from + "\" to=\"" + to + "\"");
          System.exit(-1);
          return; // NOTREACHED
        }

        if (from == null || to == null) {
          System.err.println(
              "Malformed alias - no 'from' or no 'to':from=\"" + from + "\" to=\"" + to + "\"");
          System.exit(-1);
          return; // NOTREACHED
        }

        String toFileName = to;
        if (xpath != null) {
          toFileName = to.substring(0, to.indexOf('@'));
        }
        if (fromFiles.containsKey(from + ".xml")) {
          throw new IllegalArgumentException(
              "Can't be both a synthetic alias locale and a real xml file - "
              + "consider using <aliasLocale locale=\"" + from + "\"/> instead. ");
        }
        ULocale fromLocale = new ULocale(from);
        if (!fromFiles.containsKey(toFileName + ".xml")) {
          maybeValidAlias.put(toFileName, from);
          // System.err.println("WARNING: Alias from \"" + from + "\"
          // not generated, because it would point to a nonexistent
          // LDML file " + toFileName + ".xml");
          // writeSimpleLocale(from + ".txt", fromLocale, new
          // ULocale(to), xpath,null);
        } else {
          // System.out.println("Had file " + toFileName + ".xml");
          generatedAliasFiles.put(from, new File(depDir, from + ".xml"));
          fromToMap.put(fromLocale.toString(), to);
          if (xpath != null) {
            fromXpathMap.put(fromLocale.toString(), xpath);
          }

          // write an individual file
          writeSimpleLocale(from + ".txt", fromLocale, new ULocale(to), xpath, null);
        }
      }
    }

    if (aliasLocaleList != null && aliasLocaleList.size() > 0) {
      for (int i = 0; i < aliasLocaleList.size(); i++) {
        String source = aliasLocaleList.get(i);
        if (!fromFiles.containsKey(source + ".xml")) {
          System.err.println(
              "WARNING: Alias file " + source
              + ".xml named in deprecates list but not present. Ignoring alias entry.");
        } else {
          aliasFromFiles.put(source + ".xml", new File(depDir, source + ".xml"));
          fromFiles.remove(source + ".xml");
        }
      }
    }

    // Post process: calculate any 'valid sub locales' (empty locales
    // generated due to validSubLocales attribute)
    if (!validSubMap.isEmpty() && parseSubLocale) {
      log.info("Writing valid sub locs for : " + validSubMap.toString());

      for (Iterator<String> e = validSubMap.keySet().iterator(); e.hasNext();) {
        String actualLocale = e.next();
        String list = validSubMap.get(actualLocale);
        String validSubs[] = list.split(" ");
        // printInfo(actualLocale + " .. ");
        for (int i = 0; i < validSubs.length; i++) {
          String aSub = validSubs[i];
          String testSub;
          // printInfo(" " + aSub);

          for (testSub = aSub;
               testSub != null && !testSub.equals("root") && !testSub.equals(actualLocale);
               testSub = LDMLUtilities.getParent(testSub)) {

            // printInfo(" trying " + testSub);
            if (fromFiles.containsKey(testSub + ".xml")) {
              log.setStatus(actualLocale + ".xml");
              log.warning(
                  "validSubLocale=" + aSub + " overridden because  " + testSub + ".xml  exists.");
              testSub = null;
              break;
            }

            if (generatedAliasFiles.containsKey(testSub)) {
              log.setStatus(actualLocale + ".xml");
              log.warning(
                  "validSubLocale=" + aSub + 
                  " overridden because an alias locale " + testSub
                  + ".xml  exists.");
              testSub = null;
              break;
            }
          }

          if (testSub != null) {
            emptyFromFiles.put(aSub + ".xml", new File(depDir, aSub + ".xml"));
            // ULocale aSubL = new ULocale(aSub);
            if (maybeValidAlias.containsKey(aSub)) {
              String from = maybeValidAlias.get(aSub);
              // writeSimpleLocale(from + ".txt", fromLocale, new
              // ULocale(to), xpath,null);
              writeSimpleLocale(from + ".txt", from, aSub, null, null);
              maybeValidAlias.remove(aSub);
              generatedAliasFiles.put(from, new File(depDir, from + ".xml"));
            }
            writeSimpleLocale(
                aSub + ".txt", aSub, null, null, "validSubLocale of \"" + actualLocale + "\"");
          }
        }
      }
    }

    if (!maybeValidAlias.isEmpty()) {
      Set<String> keys = maybeValidAlias.keySet();
      Iterator<String> iter = keys.iterator();
      while (iter.hasNext()) {
        String to = iter.next();
        String from = maybeValidAlias.get(to);
        log.warning("Alias from \"" + from
            + "\" not generated, because it would point to a nonexistent LDML file " + to + ".xml");
      }
    }

    String inFileText = fileMapToList(fromFiles);
    String emptyFileText = null;
    if (!emptyFromFiles.isEmpty()) {
      emptyFileText = fileMapToList(emptyFromFiles);
    }
    String aliasFilesList = fileMapToList(aliasFromFiles);
    String generatedAliasList = fileMapToList(generatedAliasFiles);

    // Now- write the actual items (resfiles.mk, etc)
    String[] brkArray = new String[2];
    if (myTreeName.equals("brkitr")) {
      getBrkCtdFilesList(depDir, brkArray);
    }
    writeResourceMakefile(myTreeName, generatedAliasList, aliasFilesList,
            inFileText, emptyFileText, brkArray[0], brkArray[1]);

    log.log("done.");
  }

  private String fileMapToList(Map<String, File> files) {
    return fileIteratorToList(files.values().iterator());
  }

  private String[] getBrkCtdFilesList(File directory, String[] brkArray) {
    // read all xml files in the directory and create ctd file list and brk file list
    FilenameFilter myFilter = new FilenameFilter() {
      public boolean accept(File f, String name) {
        return !f.isFile()
            && name.endsWith(".xml")
            && !name.startsWith("supplementalData"); // not a locale
        // root is implied, will be included elsewhere.
      }
    };

    String dirName = directory.getName();
    String[] files = directory.list(myFilter);
    StringBuilder brkList = new StringBuilder();
    StringBuilder ctdList = new StringBuilder();

    // open each file and create the list of files for brk and ctd
    for (int i = 0; i <files.length; i++) {
      Document doc = LDMLUtilities.parse(dirName + "/" + files[i], false);
      for(Node node = doc.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }

        String name = node.getNodeName();
        if (name.equals(LDMLConstants.LDML)) {
          node = node.getFirstChild();
          continue;
        }

        if (name.equals(LDMLConstants.IDENTITY)) {
          continue;
        }

        if (name.equals(LDMLConstants.SPECIAL)) {
          node = node.getFirstChild();
          continue;
        }

        if (name.equals(ICU_BRKITR_DATA)) {
          node = node.getFirstChild();
          continue;
        }

        if (name.equals(ICU_BOUNDARIES)) {
          for (Node cn = node.getFirstChild(); cn != null; cn = cn.getNextSibling()) {
            if (cn.getNodeType() != Node.ELEMENT_NODE) {
              continue;
            }
            String cnName = cn.getNodeName();

            if (cnName.equals(ICU_GRAPHEME)
                || cnName.equals(ICU_WORD)
                || cnName.equals(ICU_TITLE)
                || cnName.equals(ICU_SENTENCE)
                || cnName.equals(ICU_XGC)
                || cnName.equals(ICU_LINE)) {

              String val = LDMLUtilities.getAttributeValue(cn, ICU_DEPENDENCY);
              if (val != null) {
                brkList.append(val.substring(0, val.indexOf('.')));
                brkList.append(".txt ");
              }
            } else {
              System.err.println("Encountered unknown <" + name + "> subelement: " + cnName);
              System.exit(-1);
            }
          }
        } else if (name.equals(ICU_DICTIONARIES)) {
          for (Node cn = node.getFirstChild(); cn != null; cn = cn.getNextSibling()) {
            if (cn.getNodeType() != Node.ELEMENT_NODE) {
              continue;
            }
            String cnName = cn.getNodeName();

            if (cnName.equals(ICU_DICTIONARY)) {
              String val = LDMLUtilities.getAttributeValue(cn, ICU_DEPENDENCY);
              if (val != null) {
                ctdList.append(val.substring(0, val.indexOf('.')));
                ctdList.append(".txt ");
              }
            } else {
              System.err.println("Encountered unknown <" + name + "> subelement: " + cnName);
              System.exit(-1);
            }
          }
        } else {
          System.err.println("Encountered unknown <" + doc.getNodeName() + "> subelement: " + name);
          System.exit(-1);
        }
      }
    }

    if (brkList.length() > 0) {
      brkArray[0] = brkList.toString();
    }

    if (ctdList.length() > 0) {
      brkArray[1] = ctdList.toString();
    }

    return brkArray;
  }
  
  private void writeSimpleLocale(
      String fileName, ULocale fromLocale, ULocale toLocale, String xpath, String comment) {

    writeSimpleLocale(
        fileName, fromLocale == null ? "" : fromLocale.toString(),
        toLocale == null ? "" : toLocale.toString(), xpath, comment);
  }

  private void writeSimpleLocale(
      String fileName, String fromLocale, String toLocale, String xpath, String comment) {

    if (xpath != null) {
      writeSimpleLocaleXPath(fromLocale, xpath);
    } else {
      // no xpath - simple locale-level alias.
      String dstFileName = dstDirName + "/" + fileName;
      writeSimpleLocaleAlias(dstFileName, fromLocale, toLocale, comment);
    }
  }

  private void writeSimpleLocaleAlias(
      String dstFileName, String fromLocale, String toLocale, String comment) {
    Resource set = null;
    try {
      ResourceTable table = new ResourceTable();
      table.name = fromLocale.toString();
      if (toLocale != null) {
        ResourceString str = new ResourceString();
        str.name = "\"%%ALIAS\"";
        str.val = toLocale.toString();
        table.first = str;
      } else {
        ResourceString str = new ResourceString();
        str.name = "___";
        str.val = "";
        str.comment = "so genrb doesn't issue warnings";
        table.first = str;
      }
      set = table;
      if (comment != null) {
        set.comment = comment;
      }
    } catch (Throwable e) {
      log.error("building synthetic locale tree for " + dstFileName, e);
      System.exit(1);
    }

    String info;
    if (toLocale != null) {
      info = "(alias to " + toLocale.toString() + ")";
    } else {
      info = comment;
    }
    log.info("Writing synthetic: " + dstFileName + " " + info);
    writeResource(set, DEPRECATED_LIST, dstFileName);
  }

  private void writeSimpleLocaleXPath(String fromLocale, String xpath) {
    CLDRFile fakeFile = CLDRFile.make(fromLocale);
    fakeFile.add(xpath, "");
    fakeFile.freeze();

    Resource res = services.parseBundle(fakeFile, fromLocale.toString());

    res.name = fromLocale.toString();
    if (res != null && ((ResourceTable) res).first != null) {
      writeResource(res, DEPRECATED_LIST);
    } else {
      // parse error?
      System.err.println(
          "Failed to write out alias bundle " + fromLocale.toString() + " from " + xpath
          + " - XML list follows:");
      fakeFile.write(new PrintWriter(System.out));
    }
  }

  private void writeResourceMakefile(
      String myTreeName, String generatedAliasList, String aliasFilesList, String inFileText,
      String emptyFileText, String brkFilesList, String ctdFilesList) {

    String stub = "UNKNOWN";
    String shortstub = "unk";

    if (myTreeName.equals("main")) {
      stub = "GENRB"; // GENRB_SOURCE, GENRB_ALIAS_SOURCE
      shortstub = "res"; // resfiles.mk
    } else if (myTreeName.equals("collation")) {
      stub = "COLLATION"; // COLLATION_ALIAS_SOURCE, COLLATION_SOURCE
      shortstub = "col"; // colfiles.mk
    } else if (myTreeName.equals("brkitr")) {
      stub = "BRK_RES"; // BRK_SOURCE, BRK_CTD_SOURCE BRK_RES_SOURCE
      shortstub = "brk"; // brkfiles.mk
    } else if (myTreeName.equals("rbnf")) {
      stub = "RBNF"; // RBNF_SOURCE, RBNF_ALIAS_SOURCE
      shortstub = "rbnf"; // brkfiles.mk
    } else {
      log.error("Unknown tree name in writeResourceMakefile: " + myTreeName);
      System.exit(-1);
    }

    String resfiles_mk_name = dstDirName + "/" + shortstub + "files.mk";
    try {
      log.info("Writing ICU build file: " + resfiles_mk_name);
      PrintStream resfiles_mk = new PrintStream(new FileOutputStream(resfiles_mk_name));
      Calendar c = Calendar.getInstance();
      resfiles_mk.println(
          "# *   Copyright (C) 1998-" + c.get(Calendar.YEAR) + ", International Business Machines");
      resfiles_mk.println("# *   Corporation and others.  All Rights Reserved.");
      resfiles_mk.println(stub + "_CLDR_VERSION = " + CLDRFile.GEN_VERSION);
      resfiles_mk.println("# A list of txt's to build");
      resfiles_mk.println("# Note: ");
      resfiles_mk.println("#");
      resfiles_mk.println("#   If you are thinking of modifying this file, READ THIS.");
      resfiles_mk.println("#");
      resfiles_mk.println("# Instead of changing this file [unless you want to check it back in],");
      resfiles_mk.println(
          "# you should consider creating a '" + shortstub
          + "local.mk' file in this same directory.");
      resfiles_mk.println("# Then, you can have your local changes remain even if you upgrade or");
      resfiles_mk.println("# reconfigure ICU.");
      resfiles_mk.println("#");
      resfiles_mk.println("# Example '" + shortstub + "local.mk' files:");
      resfiles_mk.println("#");
      resfiles_mk .println("#  * To add an additional locale to the list: ");
      resfiles_mk .println("#    _____________________________________________________");
      resfiles_mk.println("#    |  " + stub + "_SOURCE_LOCAL =   myLocale.txt ...");
      resfiles_mk.println("#");
      resfiles_mk.println("#  * To REPLACE the default list and only build with a few");
      resfiles_mk.println("#     locale:");
      resfiles_mk.println("#    _____________________________________________________");
      resfiles_mk.println("#    |  " + stub + "_SOURCE = ar.txt ar_AE.txt en.txt de.txt zh.txt");
      resfiles_mk.println("#");
      resfiles_mk.println("#");
      resfiles_mk .println("# Generated by LDML2ICUConverter, from LDML source files. ");
      resfiles_mk.println("");
      resfiles_mk .println(
          "# Aliases which do not have a corresponding xx.xml file (see " + DEPRECATED_LIST + ")");
      resfiles_mk.println(
          stub + "_SYNTHETIC_ALIAS =" + generatedAliasList); // note: lists start with a space.
      resfiles_mk.println("");
      resfiles_mk.println("");
      resfiles_mk.println(
          "# All aliases (to not be included under 'installed'), but not including root.");
      resfiles_mk.println(stub + "_ALIAS_SOURCE = $(" + stub
              + "_SYNTHETIC_ALIAS)" + aliasFilesList);
      resfiles_mk.println("");
      resfiles_mk.println("");

      if (ctdFilesList != null) {
        resfiles_mk.println("# List of compact trie dictionary files (ctd).");
        resfiles_mk.println("BRK_CTD_SOURCE = " + ctdFilesList);
        resfiles_mk.println("");
        resfiles_mk.println("");
      }

      if (brkFilesList != null) {
        resfiles_mk.println("# List of break iterator files (brk).");
        resfiles_mk.println("BRK_SOURCE = " + brkFilesList);
        resfiles_mk.println("");
        resfiles_mk.println("");
      }

      if (emptyFileText != null) {
        resfiles_mk.println("# Empty locales, used for validSubLocale fallback.");
        // note: lists start with a space.
        resfiles_mk.println(stub + "_EMPTY_SOURCE =" + emptyFileText);
        resfiles_mk.println("");
        resfiles_mk.println("");
      }

      resfiles_mk.println("# Ordinary resources");
      if (emptyFileText == null) {
        resfiles_mk.print(stub + "_SOURCE =" + inFileText);
      } else {
        resfiles_mk.print(stub + "_SOURCE = $(" + stub + "_EMPTY_SOURCE)" + inFileText);
      }
      resfiles_mk.println("");
      resfiles_mk.println("");

      resfiles_mk.close();
    } catch(IOException e) {
      System.err.println("While writing " + resfiles_mk_name);
      e.printStackTrace();
      System.exit(1);
    }
  }

  private String fileIteratorToList(Iterator<File> files) {
    String out = "";
    int i = 0;
    while (files.hasNext()) {
      File f = files.next();
      if ((++i % 5) == 0) {
        out = out + "\\" + LINESEP;
      }
      out = out + (i == 0 ? " " : " ") + f.getName().substring(0, f.getName().indexOf('.')) + ".txt";
    }
    return out;
  }
}