// Copyright (C) 2011 IBM Corp. and Others. All Rights Reserved.

package org.unicode.cldr.tool.web;

import java.io.File;
import java.io.FilenameFilter;

import org.unicode.cldr.util.StringId;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XMLFileReader.SimpleHandler;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.web.IntHash;
import org.unicode.cldr.web.SurveyMain;

public class VoteDataExpander {

	/**
	 * @author srl
	 *
	 */
	public static class XPathFileHandler extends SimpleHandler {
		private IntHash<String> xpathTable = new IntHash<String>();
		private XPathParts xpp = new XPathParts(null,null);
		public void handlePathValue(String path, String value) {
			xpp.set(path);
			int id = Integer.parseInt(xpp.findAttributeValue("xpath", "id"));
			
			xpathTable.put(id, value);
			
			long sid = StringId.getId(value);
			System.err.println("Got: " + id + " : #" + Long.toHexString(sid) + " = " + value);
		}
		public IntHash<String> getXpathTable() {
			return xpathTable;
		}
	}

	public static final String XPATH_TABLE = "xpathTable.xml";
	
	/**
	 * Throw usage info.
	 * @throws InternalError
	 */
	public static void usage(String error) throws InternalError {
		throw new InternalError(error+"  - Usage: " + VoteDataExpander.class.getSimpleName()+ " <path_to_votedir>");
		/* NOTREACHED */
	}
	/**
	 * Throw usage info.
	 * @throws InternalError
	 */
	public static void usage() throws InternalError {
		usage("");
		/* NOTREACHED */
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args)  throws InternalError {
		System.err.println("Date: " + SurveyMain.formatDate());
		
		
		if(args.length!=1) {
			usage();
		}
		
		File baseDir = new File(args[0]);
		if(!baseDir.exists() ||
		   !baseDir.isDirectory()) {
			usage("Not a dir: " + baseDir.getAbsolutePath());
		}
		
		IntHash<String> xpathTable = readXpathTable(baseDir);
		System.err.println("Read xpathTable: " + xpathTable.stats());
		
		File[] files = baseDir.listFiles(new FilenameFilter(){
			public boolean accept(File dir,
                    String name) {
				return(name.endsWith(".xml")&&!name.startsWith("xpathTable"));
			}
		});
		
		for(File f : files) {
			System.err.println("_> " + f.getName());
		}
	}
	
	private static IntHash<String> readXpathTable(File baseDir) {
		File xpathTableFile = new File(baseDir,XPATH_TABLE);
		if(!xpathTableFile.exists()) {
			usage("Can't open " + xpathTableFile.getAbsolutePath());
		}
		
		XMLFileReader rd = new XMLFileReader();
		XPathFileHandler xpfh = new XPathFileHandler();
		rd.setHandler(xpfh);
		rd.read(xpathTableFile.getAbsolutePath(), XMLFileReader.CONTENT_HANDLER, false);
		//IntHash<String> xpathTable = read
		return xpfh.getXpathTable();
	}
	
}
