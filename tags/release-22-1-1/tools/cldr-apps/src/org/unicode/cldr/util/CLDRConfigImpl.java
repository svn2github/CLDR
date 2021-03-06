package org.unicode.cldr.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Map.Entry;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.web.CookieSession;
import org.unicode.cldr.web.SurveyLog;
import org.unicode.cldr.web.SurveyMain;
import org.unicode.cldr.web.UserRegistry;
import org.unicode.cldr.web.SurveyMain.Phase;

public class CLDRConfigImpl extends CLDRConfig implements JSONString {

    static String cldrHome = null;
    static boolean cldrHomeSet = false;
    
    public static File homeFile = null;

    boolean isInitted = false;
    private Properties survprops;
    CLDRConfigImpl() {
        if(System.getProperty("CLDR_WEB_TESTS")!=null) {
            throw new InternalError("CLDR_WEB_TESTS set - exitting.");
        }
        System.err.println(getClass().getName()+".cldrHome="+cldrHome);
        if(cldrHomeSet==false) {
            System.err.println("[cldrHome not set] stack=\n"+StackTracker.currentStack()+"\n CLDRHOMESET = " + cldrHomeSet);
        }
    }

    public static void setCldrHome(String initParameter) {
        cldrHome = initParameter;
        cldrHomeSet = true;
    }
    private synchronized void init() {
        if(isInitted) return;
        if(!cldrHomeSet) {
            RuntimeException t = new RuntimeException("CLDRConfigImpl used before SurveyMain.init() called! (check static ordering).  Set -DCLDR_WEB_TESTS=true if you are in the test cases.");
            //SurveyLog.logException(t);
            throw t;
        }
        
        survprops = new java.util.Properties(); 
        
        // set defaults here
        survprops.put("CLDR_SURVEY_URL", "survey"); // default to relative URL.
        
        
        File propFile;
        System.err.println("init, cldrHome="+cldrHome);
        if(cldrHome == null) {
            String homeParent = null;
            String props[] = { 
                    "catalina.home",
                    "websphere.home",
                    "user.dir"
            };
            for(String prop : props) {
                if(homeParent == null) {
                    homeParent = System.getProperty(prop);
                    if(homeParent!=null) {
                        System.err.println(" Using " + prop + " = " + homeParent);
                    } else {
                        System.err.println(" Unset: " + prop);
                    }
                }
            }
            if(homeParent == null) {  
                throw new InternalError("Could not find cldrHome. please set catalina.home, user.dir, etc, or set a servlet parameter cldr.home");
                //              for(Object qq : System.getProperties().keySet()) {
                //                  SurveyLog.logger.warning("  >> "+qq+"="+System.getProperties().get(qq));
                //              }
            } 
            homeFile = new File(homeParent, "cldr");
            propFile = new File(homeFile, "cldr.properties");
            if(!propFile.exists()) {
                System.err.println("Does not exist: "+propFile.getAbsolutePath());
                createBasicCldr(homeFile); // attempt to create
            }
            if(!homeFile.exists()) {
                throw new InternalError("$(catalina.home)/cldr isn't working as a CLDR home. Not a directory: " + homeFile.getAbsolutePath());
            }
            cldrHome = homeFile.getAbsolutePath();
        } else {
            homeFile = new File(cldrHome);
            propFile = new File(homeFile, "cldr.properties");
        }

        SurveyLog.setDir(homeFile);
        
//        SurveyLog.logger.info("SurveyTool starting up. root=" + new File(cldrHome).getAbsolutePath() + " time="+setupTime);

        try {
            java.io.FileInputStream is = new java.io.FileInputStream(propFile);
            survprops.load(is);
//            progress.update("Loading configuration..");
            is.close();
        } catch(java.io.IOException ioe) {
            /*throw new UnavailableException*/
            InternalError ie = new InternalError("Couldn't load cldr.properties file from '" + propFile.getAbsolutePath()+"' :" + ioe.toString());
            System.err.println(ie.toString() + ioe.toString());
            ioe.printStackTrace();
            throw ie;
        }
        
        File currev = new File(homeFile,"currev.properties");
        if(currev.canRead()) {
            try {
                java.io.FileInputStream is = new java.io.FileInputStream(currev);
                survprops.load(is);
    //            progress.update("Loading configuration..");
                is.close();
            } catch(java.io.IOException ioe) {
                /*throw new UnavailableException*/
                InternalError ie = new InternalError("Warning: Couldn't load currev.properties file from '" + currev.getAbsolutePath()+"' :" + ioe.toString());
                System.err.println(ie.toString() + ioe.toString());
                //ioe.printStackTrace();
                //throw ie;
            }
        }
        
        survprops.put("CLDRHOME", cldrHome);
        
        isInitted = true;
    }
    private void createBasicCldr(File homeFile) {
        System.err.println("Attempting to create /cldr  dir at " + homeFile.getAbsolutePath());

        try {
            homeFile.mkdir();
            File propsFile = new File(homeFile, "cldr.properties");
            OutputStream file = new FileOutputStream(propsFile, false); // Append
            PrintWriter pw = new PrintWriter(file);

            pw.println("## autogenerated cldr.properties config file");
            pw.println("## generated on " + SurveyMain.localhost() + " at "+new Date());
            pw.println("## see the readme at \n## "+SurveyMain.URL_CLDR+"data/tools/java/org/unicode/cldr/web/data/readme.txt ");
            pw.println("## make sure these settings are OK,\n## and comment out CLDR_MESSAGE for normal operation");
            pw.println("##");
            pw.println("## SurveyTool must be reloaded, or the web server restarted, \n## for these to take effect.");
            pw.println();
            pw.println("## your password. Login as user 'admin@' and this password for admin access.");
            pw.println("CLDR_VAP="+UserRegistry.makePassword("admin@"));
            pw.println();
            pw.println("## Special Test Enablement.");
            pw.println("#CLDR_TESTPW="+UserRegistry.makePassword("admin@"));
            pw.println();
            pw.println("## Special message shown to users as to why survey tool is down.");
            pw.println("## Comment out for normal start-up.");
            pw.println("CLDR_MESSAGE=Welcome to SurveyTool@"+SurveyMain.localhost()+". Please edit "+propsFile.getAbsolutePath()+". Comment out CLDR_MESSAGE to continue normal startup.");
            pw.println();
            pw.println("## Special message shown to users.");
            pw.println("CLDR_HEADER=Welcome to SurveyTool@"+SurveyMain.localhost()+". Please edit "+propsFile.getAbsolutePath()+" to change CLDR_HEADER (to change this message), or comment it out entirely. Also see " + homeFile.getAbsolutePath() + "/admin.html to get to the admin panel.");
            pw.println();
            pw.println("## Current SurveyTool phase ");
            pw.println("CLDR_PHASE="+Phase.BETA.name());
            pw.println();
            pw.println("## 'old' (previous) version");
            pw.println("CLDR_OLDVERSION=CLDR_OLDVERSION");
            pw.println();
            pw.println("## 'new'  version");
            pw.println("CLDR_NEWVERSION=CLDR_NEWVERSION");
            pw.println();
            pw.println("## Current SurveyTool phase ");
            pw.println("CLDR_PHASE="+Phase.BETA.name());
            pw.println();
            pw.println("## CLDR common data. Default value shown, uncomment to override");
            pw.println("CLDR_COMMON="+homeFile.getAbsolutePath()+"/common");
            pw.println();
            pw.println("## CLDR seed data. Default value shown, uncomment to override");
            pw.println("CLDR_SEED="+homeFile.getAbsolutePath()+"/seed");
            pw.println();
            pw.println("## SMTP server. Mail is disabled by default.");
            pw.println("#CLDR_SMTP=127.0.0.1");
            pw.println();
            pw.println("## FROM address for mail. Don't be a bad administrator, change this.");
            pw.println("#CLDR_FROM=bad_administrator@"+SurveyMain.localhost());
            pw.println();
            pw.println("# That's all!");
            pw.close();
            file.close();
        }
        catch(IOException exception){
          System.err.println("While writing "+homeFile.getAbsolutePath()+" props: "+exception);
          exception.printStackTrace();
        }
    }

    public SupplementalDataInfo getSupplementalDataInfo() {
        init();
        return CookieSession.sm.getSupplementalDataInfo();
    }
    public String getProperty(String key) {
        init();
        return survprops.getProperty(key);
    }
    
    public void setProperty(String key, String value) {
        if(key.equals("CLDR_HEADER")) {
            System.err.println(">> CLDRConfig set " + key + " = " + value);
            if(value==null||value.isEmpty()) {
                survprops.setProperty(key, "");
                survprops.remove(key);
            } else {
                survprops.setProperty(key, value);
            }
        }
    }

    @Override
    public String toJSONString() throws JSONException {
        JSONObject ret = new JSONObject();
        
        ret.put("CLDR_HEADER",""); // always show these
        for(Entry<Object, Object>e  : survprops.entrySet()) {
            ret.put(e.getKey().toString(), e.getValue().toString());
        }
        
        
        return ret.toString();
    }

    @Override
    public CheckCLDR.Phase getPhase() {
        return SurveyMain.phase().getCPhase();
    }
}
