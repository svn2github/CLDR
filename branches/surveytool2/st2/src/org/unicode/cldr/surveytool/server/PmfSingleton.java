package org.unicode.cldr.surveytool.server;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;

/**
 * A singleton wrapper for the PersistenceManagerFactory.
 * @author jchye
 */
public final class PmfSingleton {
    private static final PersistenceManagerFactory pmfInstance =
        JDOHelper.getPersistenceManagerFactory("transactions-optional");

    private PmfSingleton() {}

    public static PersistenceManagerFactory getInstance() {
        return pmfInstance;
    }
}