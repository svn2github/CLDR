package org.unicode.cldr.test;

import java.util.List;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Factory;

import com.ibm.icu.dev.util.PrettyPrinter;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;

/**
 * Subclass of CheckCLDR that requires a factory during checking.
 * 
 * @author jchye
 */
abstract class FactoryCheckCLDR extends CheckCLDR {
    private Factory factory;
    private CLDRFile resolvedCldrFileToCheck;

    public FactoryCheckCLDR(Factory factory) {
        super();
        this.factory = factory;
    }

    public CLDRFile getResolvedCldrFileToCheck() {
        if (resolvedCldrFileToCheck == null) {
            resolvedCldrFileToCheck = factory.make(getCldrFileToCheck().getLocaleID(), true);
        }
        return resolvedCldrFileToCheck;
    }

    @Override
    public CheckCLDR setCldrFileToCheck(CLDRFile cldrFileToCheck, Options options,
        List<CheckStatus> possibleErrors) {
        super.setCldrFileToCheck(cldrFileToCheck, options, possibleErrors);
        resolvedCldrFileToCheck = null;
        return this;
    }

    public Factory getFactory() {
        return factory;
    }

    protected PrettyPrinter createPrettyPrinter(Collator theCollator) {
        final Collator rootCol=Collator.getInstance(ULocale.ROOT);
        return new PrettyPrinter()
            .setOrdering(theCollator != null ? theCollator : rootCol.freeze())
            .setSpaceComparator(theCollator != null ? theCollator : rootCol
                .setStrength2(Collator.PRIMARY))
            .setCompressRanges(true);
    }
}