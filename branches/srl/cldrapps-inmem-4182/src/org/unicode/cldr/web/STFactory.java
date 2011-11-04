/**
 * 
 */
package org.unicode.cldr.web;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.test.ExampleGenerator;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.XPathParts.Comments;

/**
 * @author srl
 *
 */
public class STFactory extends Factory {
	private static void readonly() {
		throw new InternalError("This is a readonly instance.");
	}

	/**
	 * @author srl
	 *
	 */
	public class ReadOnlyAliasTo extends XMLSource {
		XMLSource aliasOf;

		public ReadOnlyAliasTo(XMLSource makeFrom) {			
			aliasOf=makeFrom;
			setLocaleID(aliasOf.getLocaleID());
			CLDRFile loader = new CLDRFile(aliasOf,false);
			loader.loadFromFile(new File(sm.fileBase,getLocaleID()+".xml"), getLocaleID(), getMinimalDraftStatus());
//			System.out.println("Our id: " + this.getLocaleID());
//			System.out.println("Parent = " + LocaleIDParser.getParent(this.getLocaleID()));
//			System.out.println("XParent = " + CLDRLocale.getInstance(this.getLocaleID()).getParent());
		}

		
		/* (non-Javadoc)
		 * @see com.ibm.icu.util.Freezable#freeze()
		 */
		@Override
		public Object freeze() {
			// TODO Auto-generated method stub
			readonly();
			return null;
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#putFullPathAtDPath(java.lang.String, java.lang.String)
		 */
		@Override
		public void putFullPathAtDPath(String distinguishingXPath,
				String fullxpath) {
				readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#putValueAtDPath(java.lang.String, java.lang.String)
		 */
		@Override
		public void putValueAtDPath(String distinguishingXPath, String value) {
			readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#removeValueAtDPath(java.lang.String)
		 */
		@Override
		public void removeValueAtDPath(String distinguishingXPath) {
			readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getValueAtDPath(java.lang.String)
		 */
		@Override
		public String getValueAtDPath(String path) {
			String v =  aliasOf.getValueAtDPath(path);
			//System.err.println(path+"="+v);
			return v;
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getFullPathAtDPath(java.lang.String)
		 */
		@Override
		public String getFullPathAtDPath(String path) {
			return aliasOf.getFullPathAtDPath(path);
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getXpathComments()
		 */
		@Override
		public Comments getXpathComments() {
			return aliasOf.getXpathComments();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#setXpathComments(org.unicode.cldr.util.XPathParts.Comments)
		 */
		@Override
		public void setXpathComments(Comments comments) {
			readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#iterator()
		 */
		@Override
		public Iterator<String> iterator() {
			return aliasOf.iterator();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#make(java.lang.String)
		 */
		@Override
		public XMLSource make(String localeID) {
			return makeSource(localeID, this.isResolving());
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getAvailableLocales()
		 */
		@Override
		public Set getAvailableLocales() {
			return handleGetAvailable();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getSupplementalDirectory()
		 */
		@Override
		public File getSupplementalDirectory() {
			File suppDir =  new File(getSourceDirectory()+"/../"+"supplemental");
			return suppDir;
		}
		
		
//		@Override
//		protected synchronized TreeMap<String, String> getAliases() {
//			if(true) throw new InternalError("NOT IMPLEMENTED.");
//			return null;
//		}

	}

	/**
	 * These locales can not be modified.
	 */
	private static String readOnlyLocales[] = { "root", "en" };
	
	/**
	 * Is this a locale that can't be modified?
	 * @param loc
	 * @return
	 */
	public static final boolean isReadOnlyLocale(String loc) {
		for(int i=0;i<readOnlyLocales.length;i++) {
			if(readOnlyLocales[i].equals(loc)) return true;
		}
		return false;
	}
	
	/**
	 * Is this a locale that can't be modified?
	 * @param loc
	 * @return
	 */
	public static final boolean isReadOnlyLocale(CLDRLocale loc) {
		return isReadOnlyLocale(loc.getBaseName());
	}
	
	/**
	 * The infamous back-pointer.
	 */
	SurveyMain sm = null;
	
	/**
	 * Construct one.
	 */
	public STFactory(SurveyMain sm) {
		this.sm = sm;
	}

	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.CLDRFile.Factory#getSourceDirectory()
	 */
	@Override
	public String getSourceDirectory() {
		return sm.fileBase;
	}

	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.CLDRFile.Factory#handleMake(java.lang.String, boolean, org.unicode.cldr.util.CLDRFile.DraftStatus)
	 */
	@Override
	protected CLDRFile handleMake(String localeID, boolean resolved,
			DraftStatus madeWithMinimalDraftStatus) {
		return new CLDRFile(makeSource(localeID, resolved),resolved);
	}

	private XMLSource makeSource(String localeID, boolean resolved) {
		if(localeID==null) return null; // ?!
		if(isReadOnlyLocale(localeID)) {
			return new ReadOnlyAliasTo(new CLDRFile.SimpleXMLSource(sm.getFactory(),localeID));
		} else {
			throw new InternalError("Unimplemented: locale " + localeID);
		}
	}

	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.CLDRFile.Factory#getMinimalDraftStatus()
	 */
	@Override
	protected DraftStatus getMinimalDraftStatus() {
        return DraftStatus.unconfirmed;
	}

	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.CLDRFile.Factory#handleGetAvailable()
	 */
	@Override
	protected Set<String> handleGetAvailable() {
		// TODO Auto-generated method stub
		return sm.getFactory().getAvailable();
	}

	public CheckCLDR getCheck(CLDRLocale loc) {
		CheckCLDR cc = sm.createCheck();
		cc.setCldrFileToCheck(handleMake(loc.getBaseName(),true,getMinimalDraftStatus()), sm.basicOptionsMap(), new ArrayList<CheckStatus>());
		return cc;
	}
	public List getCheckResult(CLDRLocale loc) {
		// TODO Auto-generated method stub
		return null;
	}

	public CLDRFile make(CLDRLocale loc, boolean resolved) {
		return make(loc.getBaseName(),resolved);
	}

	public ExampleGenerator getExampleGenerator() {
			CLDRFile fileForGenerator = sm.getBaselineFile();
    		
    		if(fileForGenerator==null) {
    			System.err.println("Err: fileForGenerator is null for " );
    		}
    		ExampleGenerator exampleGenerator = new ExampleGenerator(fileForGenerator, sm.fileBase + "/../supplemental/");
    		exampleGenerator.setVerboseErrors(sm.twidBool("ExampleGenerator.setVerboseErrors"));
    		//System.err.println("-revalid exgen-"+locale + " - " + exampleIsValid + " in " + this);
    		//exampleIsValid.setValid();
    		//System.err.println(" >> "+locale + " - " + exampleIsValid + " in " + this);
    		//exampleIsValid.register();
    		//System.err.println(" >>> "+locale + " - " + exampleIsValid + " in " + this);
        return exampleGenerator;
	}
}
