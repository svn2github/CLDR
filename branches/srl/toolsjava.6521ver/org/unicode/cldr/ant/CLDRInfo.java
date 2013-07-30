package org.unicode.cldr.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.unicode.cldr.util.DtdData;

/**
 * 
 * Usage:
 *     <taskdef name="cldr-info" classname="org.unicode.cldr.ant.CLDRInfo">
 *           <classpath>
 *               <pathelement path="${java.class.path}/"/>
 *               <fileset dir="${cldrlibs.dir}" includes="*.jar"/>
 *               <pathelement location="${cldrtools.dir}/cldr.jar"/>
 *           </classpath>
 *       </taskdef>
 *       <!-- fetch cldr info -->
 *       <cldr-info />
 * 
 * result:  "cldrVersion" propery is set with CLDR version (such as '24').
 * 
 * Probably depends on CLDR_DIR and other env variables being set.
 *            
 * @author srl
 *
 */
public class CLDRInfo extends Task {
    @Override
    public void execute() throws BuildException {
        String cldrVersion = DtdData.getCldrVersion();
        if(cldrVersion != null) {
            getProject().setNewProperty("cldrVersion", cldrVersion);
            this.log("cldrVersion=" + cldrVersion);
        } else {
            throw new BuildException("Could not get the CLDR version from ldml.dtd.");
        }
    }
}
