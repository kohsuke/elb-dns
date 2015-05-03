package org.kohsuke.elbdns;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Name;
import org.xbill.DNS.RRset;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.SetResponses;
import org.xbill.DNS.TextParseException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps <tt>*.NAME-elb-ID.REGION.elb.io</tt> to <tt>NAME-elb-ID.REGION.elb.amazonaws.com</tt>.
 *
 * <p>
 * This way, every ELB gets automatic wildcard domain names.
 *
 * @author Kohsuke Kawaguchi
 */
public class ElbApp extends App {
    private Name base;

    public static void main(String[] args) throws CmdLineException {
        ElbApp app = new ElbApp();
        new CmdLineParser(app).parseArgument(args);
        app.main();
    }

    @Option(name="-b",usage="Based domain name",required=true)
    public void setBase(String name) throws TextParseException {
        if (!name.endsWith("."))    name=name+".";
        this.base = Name.fromString(name);
    }

    @Override
    protected boolean isNameInZone(Name name) {
        return name.subdomain(base);
    }

    @Override
    protected SetResponse findMatchingRecords(Name name, int type) {
        Name n = name.relativize(base);

        int l = n.labels();

        if (l <2) // expecting at least two labels: NAME-elb-ID.REGION
            return SetResponses.NXDOMAIN;

        try {
            Name t = Name.fromString(n.getLabelString(l - 2) + "." + n.getLabelString(l - 1) + ".elb.amazonaws.com.");
            return SetResponses.success(new RRset(new CNAMERecord(name, DClass.IN, 60, t)));
        } catch (TextParseException e) {
            LOGGER.log(Level.WARNING, "Failed to parse "+name, e);
            return SetResponses.NXDOMAIN;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ElbApp.class.getName());
}
