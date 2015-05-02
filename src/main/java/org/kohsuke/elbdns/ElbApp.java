package org.kohsuke.elbdns;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Name;
import org.xbill.DNS.RRset;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.SetResponses;
import org.xbill.DNS.Type;

/**
 * @author Kohsuke Kawaguchi
 */
public class ElbApp extends App {

    private final Name base;

    public static void main(String[] args) throws CmdLineException {
        ElbApp app = new ElbApp();
        new CmdLineParser(app).parseArgument(args);
        app.main();
    }

    public ElbApp() {
        base = Name.fromConstantString("elb.io");
    }

    @Override
    protected boolean isNameInZone(Name name) {
        return name.subdomain(base);
    }

    @Override
    protected SetResponse findMatchingRecords(Name name, int type) {
        System.out.println(name);
        System.out.println(type);
        if (type==Type.CNAME || type==Type.ANY) {
            RRset rrs = new RRset();
            rrs.addRR(new CNAMERecord(name, DClass.IN, 60, Name.fromConstantString("www.google.com")));
            return SetResponses.success(rrs);
        }

        return SetResponses.NXDOMAIN;
    }
}
