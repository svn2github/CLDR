package org.unicode.cldr.test;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Factory;

/**
 * Subclass of CheckCLDR that requires a factory during checking.
 * @author jchye
 */
abstract class FactoryCheckCLDR extends CheckCLDR {
    private Factory factory;
    public FactoryCheckCLDR(Factory factory) {
        super();
        this.factory = factory;
    }

    public CLDRFile getResolvedCldrFileToCheck() {
        return factory.make(getCldrFileToCheck().getLocaleID(), true);
    }

    public Factory getFactory() {
        return factory;
    }
}