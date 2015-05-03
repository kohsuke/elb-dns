package org.kohsuke.elbdns;

import org.kohsuke.args4j.Option;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.Type;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hello world!
 */
public abstract class App {
    @Option(name="-p",usage="Port to listen to")
    private int port = 53;

    private final ExecutorService exec = Executors.newCachedThreadPool();

    public void main() {
        exec.submit(wrap(() -> {
            tcp();
            return null;
        }));
        exec.submit(wrap(() -> {
            udp();
            return null;
        }));
    }

    public void tcp() throws IOException {
        try (ServerSocket ss = new ServerSocket(port)){
            while (true) {
                final Socket s = ss.accept();
                exec.submit(wrap(() -> { tcp(s); return null; }));
            }
        }
    }

    public void tcp(Socket s) throws IOException {
        try {
            DataInputStream i = new DataInputStream(s.getInputStream());

            int len = i.readUnsignedShort();
            byte[] in = new byte[len];
            i.readFully(in);

            Message query;
            Message response;
            try {
                query = new Message(in);
                response = generateReply(query);
                if (response == null)
                    return;
            } catch (IOException e) {
                response = formerrMessage(in);
            }

            byte[] bytes = response.toWire();
            DataOutputStream o = new DataOutputStream(s.getOutputStream());
            o.writeShort(bytes.length);
            o.write(bytes);
        } finally {
            s.close();
        }
    }

    public void udp() throws IOException {
        try (DatagramSocket sock = new DatagramSocket(port)) {
            byte[] in = new byte[512];
            DatagramPacket i = new DatagramPacket(in, in.length);

            while (true) {
                i.setLength(in.length);
                sock.receive(i);
                Message response;
                try {
                    Message query = new Message(in);
                    response = generateReply(query);
                    if (response == null)
                        continue;
                } catch (IOException e) {
                    response = formerrMessage(in);
                }

                byte[] r = response.toWire(512);
                DatagramPacket o = new DatagramPacket(r, r.length, i.getAddress(), i.getPort());
                sock.send(o);
            }
        }
    }

    /**
     * Check to see if a given name falls within our zone.
     */
    protected abstract boolean isNameInZone(Name name);

    private Message generateReply(Message query) throws IOException {
        Header header = query.getHeader();
        if (header.getOpcode() != Opcode.QUERY)
            return errorMessage(query, Rcode.NOTIMP);
        if (query.getTSIG() != null)
            return errorMessage(query, Rcode.NOTIMP);

        Record queryRecord = query.getQuestion();
        Message response = new Message(query.getHeader().getID());
        response.getHeader().setFlag(Flags.QR);
        if (query.getHeader().getFlag(Flags.RD))
            response.getHeader().setFlag(Flags.RD);
        response.addRecord(queryRecord, Section.QUESTION);

        Name name = queryRecord.getName();
        int type = queryRecord.getType();

        if (type == Type.AXFR)
            return errorMessage(query, Rcode.NOTIMP);
        if (!Type.isRR(type) && type != Type.ANY)
            return errorMessage(query, Rcode.NOTIMP);

        // Reject names outside our zone.
        // TODO - We should refer the client to other name servers here.
        if (!isNameInZone(name))
            return errorMessage(query, Rcode.NOTIMP);

        // We're authoritative for this zone.
        response.getHeader().setFlag(Flags.AA);

        // Look up the records. If we can't find any, send an
        // authoritative error response.
        SetResponse zr = findMatchingRecords(name, type);
        if (zr.isNXDOMAIN())
            response.getHeader().setRcode(Rcode.NXDOMAIN);

        // Copy the records we found into the response.
        if (zr.isSuccessful()) {
            for (RRset rrset : zr.answers()) {
                for (Iterator itt = rrset.rrs(); itt.hasNext(); ) {
                    response.addRecord((Record) itt.next(), Section.ANSWER);
                }
            }
        }

        addAdditional(response);
        return response;
    }


    protected abstract SetResponse findMatchingRecords(Name name, int type);

    // Look for some records to use as glue. See addAditional.
    private RRset findExactMatch(Name name, int type) {
        // TODO
        if (!isNameInZone(name))
            return null;
        // return zone.findExactMatch(name, type);
        return null;
    }

    // Add address records for hosts mentioned in the response.
    // See addAditional.
    private void addGlue(Message response, Name name) {
        RRset a = findExactMatch(name, Type.A);
        if (a==null)        return;
        for (Iterator itt = a.rrs(); itt.hasNext(); ) {
            Record r = (Record) itt.next();
            if (!response.findRecord(r))
                response.addRecord(r, Section.ADDITIONAL);
        }
    }


    private void addAdditional2(Message response, int section) {
        for (Record r : response.getSectionArray(section)) {
            Name glueName = r.getAdditionalName();
            if (glueName != null)
                addGlue(response, glueName);
        }
    }

    private void addAdditional(Message response) {
        addAdditional2(response, Section.ANSWER);
        addAdditional2(response, Section.AUTHORITY);
    }

    public Message formerrMessage(byte[] in) throws IOException {
        return buildErrorMessage(new Header(in), Rcode.FORMERR, null);
    }

    public Message errorMessage(Message query, int rcode) {
        return buildErrorMessage(query.getHeader(), rcode, query.getQuestion());
    }

    Message buildErrorMessage(Header header, int rcode, Record question) {
        Message response = new Message();
        response.setHeader(header);
        for (int i = 0; i < 4; i++)
            response.removeAllRecords(i);
        if (rcode == Rcode.SERVFAIL)
            response.addRecord(question, Section.QUESTION);
        header.setRcode(rcode);
        return response;
    }

    private Runnable wrap(final Callable<?> r) {
        return () -> {
            try {
                r.call();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Failed to process "+r, t);
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
}
