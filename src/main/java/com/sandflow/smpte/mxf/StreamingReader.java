package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.KLVInputStream;
import com.sandflow.smpte.klv.LocalSet;
import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.Group;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.CountingInputStream;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.BasicEvent;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;

public class StreamingReader {
  private static final UL INDEX_TABLE_SEGMENT_UL = UL.fromURN("urn:smpte:ul:060e2b34.02530101.0d010201.01100100");

  private static final UL PREFACE_KEY = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.01012f00");

  public interface Track {
    String getName();

    FileDescriptor getDescriptor();
  }

  /**
   * Defines all events raised by this class
   */
  public static enum EventCodes {

    /**
     * No root object found
     */
    MISSING_ROOT_OBJECT(Event.Severity.FATAL),
    /**
     * No partition pack found in the MXF file
     */
    MISSING_PARTITION_PACK(Event.Severity.FATAL),
    /**
     * No primer pack found in the MXF file
     */
    MISSING_PRIMER_PACK(Event.Severity.FATAL),
    /**
     * Unexpected group sequence encountered
     */
    UNEXPECTED_STRUCTURE(Event.Severity.ERROR),
    /**
     * Failed to read Group
     */
    GROUP_READ_FAILED(Event.Severity.ERROR),
    /**
     * Inconsistent header metadata length
     */
    INCONSISTENT_HEADER_LENGTH(Event.Severity.FATAL);

    public final Event.Severity severity;

    private EventCodes(Event.Severity severity) {
      this.severity = severity;
    }

  }

  /**
   * All events raised by this class are instance of this class
   */
  public static class MXFEvent extends BasicEvent {

    public MXFEvent(EventCodes kind, String message) {
      super(kind.severity, kind, message);
    }

  }

  public static class MXFException extends Exception {

    public MXFException(String msg) {
      super(msg);
    }
  }

  private static void handleEvent(EventHandler handler, com.sandflow.util.events.Event evt) throws MXFException {
    if (handler != null) {
      if (!handler.handle(evt) ||
          evt.getSeverity() == Event.Severity.FATAL) {
        /* die on FATAL events or if requested by the handler */
        throw new MXFException(evt.getMessage());

      }
    } else if (evt.getSeverity() == Event.Severity.ERROR ||
        evt.getSeverity() == Event.Severity.FATAL) {
      /* if no event handler was provided, die on FATAL and ERROR events */
      throw new MXFException(evt.getMessage());
    }
  }

  StreamingReader(InputStream file, EventHandler evthandler) throws IOException, KLVException, MXFException {
    CountingInputStream cis = new CountingInputStream(file);
    KLVInputStream kis = new KLVInputStream(cis);

    /* look for the header partition pack */
    PartitionPack pp = null;
    for (Triplet t; (t = kis.readTriplet()) != null;) {
      if ((pp = PartitionPack.fromTriplet(t)) != null) {
        break;
      }
    }

    if (pp == null) {
      handleEvent(evthandler,
          new MXFEvent(EventCodes.MISSING_PARTITION_PACK, "No Partition Pack found"));
    }

    /* start counting header metadata bytes */
    cis.resetCount();

    /* look for the primer pack */
    LocalTagRegister localreg = null;
    for (Triplet t; (t = kis.readTriplet()) != null; cis.resetCount()) {

      /* skip fill items, if any */
      if (!FillItem.getKey().equalsIgnoreVersion(t.getKey())) {
        localreg = PrimerPack.createLocalTagRegister(t);
        break;
      }
    }

    if (localreg == null) {
      handleEvent(evthandler, new MXFEvent(
          EventCodes.MISSING_PRIMER_PACK,
          "No Primer Pack found"));
    }

    /*
     * capture all local sets within the header metadata
     */
    ArrayList<Group> gs = new ArrayList<>();
    HashMap<UUID, Set> setresolver = new HashMap<>();

    for (Triplet t; cis.getCount() < pp.getHeaderByteCount()
        && (t = kis.readTriplet()) != null;) {

      /* skip fill items */
      if (FillItem.getKey().equalsIgnoreVersion(t.getKey())) {
        continue;
      }

      try {
        Group g = LocalSet.fromTriplet(t, localreg);

        if (g != null) {

          gs.add(g);
          Set set = Set.fromGroup(g);
          if (set != null) {
            setresolver.put(set.getInstanceID(), set);
          }

        } else {
          handleEvent(evthandler, new MXFEvent(
              EventCodes.GROUP_READ_FAILED,
              String.format("Failed to read Group: {0}", t.getKey().toString())));
        }
      } catch (KLVException ke) {
        handleEvent(evthandler, new MXFEvent(
            EventCodes.GROUP_READ_FAILED,
            String.format("Failed to read Group %s with error %s", t.getKey().toString(), ke.getMessage())));
      }
    }

    /*
     * check that the header metadata length is consistent
     */
    if (cis.getCount() != pp.getHeaderByteCount()) {
      handleEvent(evthandler, new MXFEvent(
        EventCodes.INCONSISTENT_HEADER_LENGTH,
        String.format("Actual Header Metadata length (%s) does not match the Partition Pack information (%s)", cis.getCount(), pp.getHeaderByteCount())));
    }

    /*
     * in MXF, the first header metadata set should be the
     * Preface set according to ST 377-1 Section 9.5.1, preceded
     * by Class 14 groups
     */
    for (Group agroup : gs) {
      if (agroup.getKey().equalsWithMask(PREFACE_KEY, 0b1111101011111111 /* ignore version and Group coding */)) {
        break;
      }
      if (!agroup.getKey().isClass14()) {
        handleEvent(evthandler, new MXFEvent(
            EventCodes.UNEXPECTED_STRUCTURE,
            String.format(
                "At least one non-class 14 Set %s was found between"
                    + " the Primer Pack and the Preface Set.",
                agroup.getKey())));
        break;
      }
    }

    /*
     * skip over index tables, if any
     */
    if (pp.getIndexSID() != 0) {
      kis.skip(pp.getIndexByteCount());
    }

    /*
     * read essence container data
     */
    if (pp.getBodySID() != 0) {
      
    }

    kis.close();

  }

  boolean nextUnit() {
    /*
     * assume KLV if an essence element 
     */


    return false;
  }

  Fraction getUnitOffset() {
    // TODO Auto-generated method stub
    return null;
  }

  Track getUnitTrack() {
    // TODO Auto-generated method stub
    return null;
  }

  long getUnitLength() {
    // TODO Auto-generated method stub
    return 0;
  }

  CountingInputStream getUnitPayload() {
    // TODO Auto-generated method stub
    return null;
  }

  Track[] getTracks() {
    // TODO Auto-generated method stub
    return null;
  }

  void close() {
    // TODO Auto-generated method stub

  }

}
