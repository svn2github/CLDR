package org.unicode.cldr.surveytool.server;

import org.unicode.cldr.surveytool.client.CloudTestService;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

/**
 * The server side implementation of the GreetingService.
 */
@SuppressWarnings("serial")
public class CloudTestServiceImpl extends RemoteServiceServlet implements
        CloudTestService {
    private static final PersistenceManagerFactory pmf = PmfSingleton
            .getInstance();
    private static final PersistenceManager pm = pmf.getPersistenceManager();
    boolean resolved = false;
    // Assigned in setTestType
    int testType = 0;

    public CloudTestServiceImpl() {
    }

    public String greetServer(String regex, String keyRegex, String valRegex)
            throws IllegalArgumentException {
        if (testType == 0) {
            return CloudTestSetUp.find(pm, regex, keyRegex, valRegex, resolved);
        } else {
            return CloudTestSetUp.EquivTest(pm, regex, resolved);
        }
    }

    public String setResolved(boolean resolving) {
        resolved = resolving;
        // For some reason, all calls to this class's methods must be
        // asynchronous,
        // and I can't figure out how to do async with void, so we just return
        // garbage.
        return "y";
    }

    public String setTestType(int type) {
        // 0 is a find
        // 1 is a speed test
        // 2 is an equivalence test
        testType = type;
        // See setResolved.
        return "y";
    }

    /*
     * (non-Javadoc)
     * 
     * @see cldr.backend.client.GreetingService#loadData()
     */
    @Override
    public String loadData(int i) {
        CloudTestSetUp.setUp(pm, i);
        return "GREAT SUCCESS";
    }
}
