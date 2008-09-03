package org.unicode.cldr.icu;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.ant.CLDRConverterTool;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.CLDRFile.Factory;

import com.ibm.icu.dev.test.util.BagFormatter;
import com.ibm.icu.dev.test.util.ElapsedTimer;
import com.ibm.icu.dev.tool.UOption;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.text.Transliterator;

public class ConvertTransforms extends CLDRConverterTool{
	
	private static final int
	HELP1 = 0,
	HELP2 = 1,
	SOURCEDIR = 2,
	DESTDIR = 3,
	MATCH = 4,
	SKIP_COMMENTS = 5,
	WRITE_INDEX = 6,
	VERBOSE = 7	;
	
	private static final UOption[] options = {
		UOption.HELP_H(),
		UOption.HELP_QUESTION_MARK(),
		UOption.SOURCEDIR().setDefault(Utility.COMMON_DIRECTORY + "transforms/"),
		UOption.DESTDIR().setDefault(Utility.GEN_DIRECTORY + "icu-transforms/"),
		UOption.create("match", 'm', UOption.REQUIRES_ARG).setDefault(".*"),
		UOption.create("commentSkip", 'c', UOption.NO_ARG),
		UOption.create("writeIndex", 'x', UOption.NO_ARG),
		UOption.VERBOSE(),
	};
	
	static final String HELP_TEXT1 = "Use the following options" + XPathParts.NEWLINE
	+ "-h or -?\t for this message" + XPathParts.NEWLINE
	+ "-"+options[SOURCEDIR].shortName + "\t source directory. Default = -s" 
	+ Utility.getCanonicalName(Utility.MAIN_DIRECTORY) + XPathParts.NEWLINE
	+ "\tExample:-sC:\\Unicode-CVS2\\cldr\\common\\gen\\source\\" + XPathParts.NEWLINE
	+ "-"+options[DESTDIR].shortName + "\t destination directory. Default = -d"
	+ Utility.getCanonicalName(Utility.GEN_DIRECTORY + "main/") + XPathParts.NEWLINE
	+ "-m<regex>\t to restrict the files to what matches <regex>" + XPathParts.NEWLINE
//	"--writeIndex / -x   to write the index (trnsfiles.mk)"+ XPathParts.NEWLINE
	;

	// TODO add options to set input and output directories, matching pattern
	public static void main(String[] args) throws Exception {
	    ConvertTransforms ct = new ConvertTransforms();
        ct.processArgs(args);
	}

	private boolean skipComments;
	private boolean writeIndex = false;
	private boolean verbose = false;
	
	int fileCount = 0;
	
    public void writeIndex(String inputDirectory, String matchingPattern, String outputDirectory) throws IOException {
// not needed - only one important file to ICU (root.txt) is output.
//        System.out.println(new File(inputDirectory).getCanonicalPath());
//        Factory cldrFactory = CLDRFile.Factory.make(inputDirectory, matchingPattern);
//        Set ids = cldrFactory.getAvailable();
//        Set<String> files = new TreeSet<String>();
//        for (Iterator idIterator = ids.iterator(); idIterator.hasNext();) {
//            String id = (String) idIterator.next();
//            if (id.equals("All")) continue;
//            String filename = null;
//            CLDRFile cldrFile = cldrFactory.make(id, false);
//            if (cldrFile.getDtdVersion().equals("1.4")) {
//                if (id.indexOf("Ethiopic") >= 0 || id.indexOf("CanadianAboriginal") >= 0) {
//                   System.out.println("WARNING: Skipping file for 1.4" + id);
//                    return;
//                }
//            }
//            boolean first = true;
//            for (Iterator it = cldrFile.iterator("", CLDRFile.ldmlComparator); it.hasNext();) {
//                String path = (String) it.next();
//                String value = cldrFile.getStringValue(path);
//                if (first) {
//                    filename = addIndexInfo(null, path, id);
//                    if (filename == null) return; // not a transform file!
//                    files.add(filename);
//                    first = false;
//                }
//            }
//        }
    }
	
	public void writeTransforms(String inputDirectory, String matchingPattern, String outputDirectory) throws IOException {
		System.out.println(new File(inputDirectory).getCanonicalPath());
		Factory cldrFactory = CLDRFile.Factory.make(inputDirectory, matchingPattern);
		Set ids = cldrFactory.getAvailable();
		PrintWriter index = BagFormatter.openUTF8Writer(outputDirectory, "root.txt");
		doHeader(index, "//", "root.txt");
		try {
			index.println("root {");
			index.println("    RuleBasedTransliteratorIDs {");
            //addAlias(index, "Latin", "el", "", "Latin", "Greek", "UNGEGN");
            //addAlias(index, "el", "Latin", "", "Greek", "Latin", "UNGEGN");
			//addAlias(index, "Latin", "Jamo", "", "Latin", "ConjoiningJamo", "");
            addAlias(index, "Tone", "Digit", "", "Pinyin", "NumericPinyin", "");
            addAlias(index, "Digit", "Tone", "", "NumericPinyin", "Pinyin", "");
			for (Iterator idIterator = ids.iterator(); idIterator.hasNext();) {
				String id = (String) idIterator.next();
				if (id.equals("All")) continue;
				try {
					convertFile(cldrFactory, id, outputDirectory, index);
				} catch (IOException e) {
					System.err.println("Failure in: " + id);
					throw e;
				}
			}
			index.println("    }");
			index.println("    TransliteratorNamePattern {");
			index.println("        // Format for the display name of a Transliterator.");
			index.println("        // This is the language-neutral form of this resource.");
			index.println("        \"{0,choice,0#|1#{1}|2#{1}-{2}}\" // Display name");
			index.println("    }");
			index.println("    // Transliterator display names");
			index.println("    // This is the English form of this resource.");
			index.println("    \"%Translit%Hex\"         { \"%Translit%Hex\" }");
			index.println("    \"%Translit%UnicodeName\" { \"%Translit%UnicodeName\" }");
			index.println("    \"%Translit%UnicodeChar\" { \"%Translit%UnicodeChar\" }");
			index.println("    TransliterateLATIN{        ");
			index.println("    \"\",");
			index.println("    \"\"");
			index.println("    }");
			index.println("}");
		} finally {
			index.close();
		}	
	}

	public void convertFile(Factory cldrFactory, String id, String outputDirectory, PrintWriter index) throws IOException {
		PrintWriter output = null;
		String filename = null;
		CLDRFile cldrFile = cldrFactory.make(id, false);
        if (cldrFile.getDtdVersion().equals("1.4")) {
            if (id.indexOf("Ethiopic") >= 0 || id.indexOf("CanadianAboriginal") >= 0) {
               System.out.println("WARNING: Skipping file for 1.4" + id);
                return;
            }
        }
		boolean first = true;
		for (Iterator it = cldrFile.iterator("", CLDRFile.ldmlComparator); it.hasNext();) {
			String path = (String) it.next();
			String value = cldrFile.getStringValue(path);
			if (first) {
				filename = addIndexInfo(index, path, id);
				if (filename == null) return; // not a transform file!
				output = BagFormatter.openUTF8Writer(outputDirectory, filename);
				doHeader(output, "#", filename);
				first = false;
			}
			if (path.indexOf("/comment") >= 0) {
				if (!skipComments) {
					if (!value.trim().startsWith("#")) value = value + "# ";
					output.println(value);
				}
			} else if (path.indexOf("/tRule") >= 0) {
				//value = replaceUnquoted(value,"\u00A7", "&");
				value = replaceUnquoted(value, "\u2192", ">");
				value = replaceUnquoted(value, "\u2190", "<");
				value = replaceUnquoted(value, "\u2194", "<>");
				value=fixup.transliterate(value);
				output.println(value);
			} else {
				throw new IllegalArgumentException("Unknown element: " + path + "\t " + value);
			}
		}
		output.close();
	}
	
	public static final Transliterator fixup = Transliterator.getInstance("[:Mn:]any-hex/java");

	public static String replaceUnquoted(String value, String toReplace, String replacement) {
		// quick exit in most cases
		if ( value.indexOf(toReplace) < 0 )
			return value;
		
		String  updatedValue = "";
		int     segmentStart = 0;
		boolean inQuotes = false;
		boolean ignoreCharValue = false;
		int     length = value.length();
		
		for (int pos = 0; pos < length; ++pos) {
			char curChar = (char)0;
			
			if (ignoreCharValue) {
				ignoreCharValue = false;
			} else {
				curChar = value.charAt(pos);
			}
		
			if (curChar == '\\') {
				// escape, ignore the value of the next char (actually the next UTF16 code unit, but that works here)
				ignoreCharValue = true;
			}
			boolean isLastChar = (pos + 1 >= length);
			if (curChar == '\'' || isLastChar) {
				// quote, begin or end of a quoted literal (in which no replacement takes place)
				if (inQuotes) {
					// End of a quoted segment; guaranteed to include at least opening quote.
					// Just add the segment (including current char) to updatedValue.
					 updatedValue = updatedValue + value.substring(segmentStart, pos+1);
					 segmentStart = pos+1;
				} else {
					if (isLastChar)
						++pos;
					if (pos > segmentStart) {
						// End of a nonempty unquoted segment; perform requested replacements and
						// then add segment to updatedValue.
						String currentSegment = value.substring(segmentStart, pos);
						updatedValue = updatedValue + currentSegment.replace(toReplace, replacement);
						segmentStart = pos;
					}
				}
				inQuotes = !inQuotes;
			}
			// else the char just becomes part of the current segment
		}
		return updatedValue;
	}
	
	static XPathParts parts = new XPathParts();
	
	private String addIndexInfo(PrintWriter index, String path, String transID) {
		parts.set(path);
		Map attributes = parts.findAttributes("transform");
		if (attributes == null) return null; // error, not a transform file
		String source = (String) attributes.get("source");
		String target = (String) attributes.get("target");
		String variant = (String) attributes.get("variant");
		String direction = (String) attributes.get("direction");
		// HACK
		//if (transID.indexOf("InterIndic") >= 0) direction = "forward";
		// END HACK
		String visibility = (String) attributes.get("visibility");
		
		String status = "internal".equals(visibility) ? "internal" : "file";

		fileCount++;
		
		String id = source + "-" + target;
		String rid = target + "-" + source;
		String filename = source + "_" + target;
		if (variant != null) {
			id += "/" + variant;
			rid += "/" + variant;
			filename += "_" + variant;
		}
		filename += ".txt";
		//if(verbose) {
		System.out.println("Writing translit/"+filename + "   " + id + "   ("+direction+")");
		// } else { System.out.print('.'); } /* one dot per file */ 
		if (direction.equals("both") || direction.equals("forward")) {
			if (verbose) { System.out.println("    " + id + "    " +  filename + "    " + "FORWARD"); }
			index.println("        " + id + " {");
			index.println("            " + status + " {");
			index.println("                resource:process(transliterator) {\"" + filename + "\"}");
			index.println("                direction {\"FORWARD\"}");
			index.println("            }");
			index.println("        }");
		}
		if (direction.equals("both") || direction.equals("backward")) {		
			if(verbose) { System.out.println("    " + rid + "    " +  filename + "    " + "REVERSE"); } 
			index.println("        " + rid + " {");
			index.println("            " + status + " {");
			index.println("                resource:process(transliterator) {\"" + filename + "\"}");
			index.println("                direction {\"REVERSE\"}");
			index.println("            }");
			index.println("        }");
		}
		index.println();
		return filename;
	}
	
	void addAlias(PrintWriter index, String aliasSource, String aliasTarget, String aliasVariant, String originalSource, String originalTarget, String originalVariant) {
//        Spacedhan-Han {
//            alias {"null"}
//        }
		addAlias(index, getName(aliasSource, aliasTarget, aliasVariant), getName(originalSource, originalTarget, originalVariant));
	}

	private void addAlias(PrintWriter index, String alias, String original) {
		index.println("        " + alias + " {");
		index.println("            alias" + " {\"" + original + "\"}");
		index.println("        }");
	}
	
	String getName(String source, String target, String variant) {
		String id = source + "-" + target;
		if (variant != null && variant.length() != 0) {
			id += "/" + variant;
		}
		return id;
	}
	
	
	// fixData ONLY NEEDED TO FIX FILE PROBLEM
	
	private void fixData(String inputDirectory, String matchingPattern, String outputDirectory) throws IOException {
		File dir = new File(inputDirectory);
		File[] files = dir.listFiles();
		for (int i = 0; i < files.length; ++i) {
			if (files[i].isDirectory()) continue;
			BufferedReader input = BagFormatter.openUTF8Reader("", files[i].getCanonicalPath());
			PrintWriter output = BagFormatter.openUTF8Writer("", outputDirectory + files[i].getName());
			while (true) {
				String line = input.readLine();
				if (line == null) break;
				if (line.indexOf("DOCTYPE") >= 0) {
					line = line.replaceAll(" ldml ", " supplementalData ");
				}
				output.println(line);
			}
			input.close();
			output.close();
		}
	}

	private void doHeader(PrintWriter output, String quoteSymbol, String filename) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy"); 
		output.print('\uFEFF');
		output.println(quoteSymbol + " ***************************************************************************");
		output.println(quoteSymbol + " *");
		output.println(quoteSymbol + " *  Copyright (C) 2004-"+ sdf.format(new Date()) + ", International Business Machines");
		output.println(quoteSymbol + " *  Corporation; Unicode, Inc.; and others.  All Rights Reserved.");
		output.println(quoteSymbol + " *");
		output.println(quoteSymbol + " ***************************************************************************");
		output.println(quoteSymbol + " File: " + filename);
		output.println(quoteSymbol + " Generated from CLDR ");
		output.println(quoteSymbol + "");

	}

    public void processArgs(String[] args) {
        // TODO Auto-generated method stub
        UOption.parseArgs(args, options);
        if (options[HELP1].doesOccur || options[HELP2].doesOccur) {
            System.out.println(HELP_TEXT1);
            return;
        }
        
        String sourceDir = options[SOURCEDIR].value;    // Utility.COMMON_DIRECTORY + "transforms/";
        String targetDir = options[DESTDIR].value;  // Utility.GEN_DIRECTORY + "main/";
        String match = options[MATCH].value;
        skipComments = options[SKIP_COMMENTS].doesOccur;
        writeIndex = options[WRITE_INDEX].doesOccur;
        verbose = options[VERBOSE].doesOccur;
        
        try {
//          fixData(sourceDir, match, targetDir);
            if(writeIndex) {
//                writeIndex(sourceDir, match, targetDir+File.separator);
                throw new InternalError("writeIndex not implemented.");
            } else {
                ElapsedTimer et = new ElapsedTimer();
                writeTransforms(sourceDir, match, targetDir+File.separator);
//                if(!verbose) System.out.println();
                System.out.println("ConvertTransforms: wrote " + fileCount + " files + root.txt in  " + et);
            }
        }catch(IOException ex){
            RuntimeException e = new RuntimeException();
            e.initCause(ex.getCause());
            throw e;
        }finally {
            System.out.println("DONE");
        }
    }
}