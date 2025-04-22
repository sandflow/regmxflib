package com.sandflow.smpte.mxf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.Group;
import com.sandflow.smpte.klv.KLVInputStream;
import com.sandflow.smpte.klv.LocalSet;
import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.PartitionPack.Kind;
import com.sandflow.smpte.mxf.adapters.ClassAdapter;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.BoundedInputStream;
import com.sandflow.smpte.util.CountingInputStream;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.BasicEvent;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;


public class StreamingReader {
  private static final UL PREFACE_KEY = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.01012f00");

  public class Track {
    FileDescriptor fileDescriptor;
  }

  KLVInputStream kis;
  boolean isDone = false;

  Track unitTrack;
  BoundedInputStream unitPayload;
  Long unitLength;
  AUID unitKey;

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

  StreamingReader(InputStream is, EventHandler evthandler) throws IOException, KLVException, MXFException {
    if (is == null) {
      throw new NullPointerException("InputStream cannot be null");
    }

    CountingInputStream cis = new CountingInputStream(is);
    kis = new KLVInputStream(cis);

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

    while (cis.getCount() < pp.getHeaderByteCount()) {

      Triplet t = kis.readTriplet();

      /* skip fill items */
      /* TODO: replace with call to FillItem static method */
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
          String.format("Actual Header Metadata length (%s) does not match the Partition Pack information (%s)",
              cis.getCount(), pp.getHeaderByteCount())));
    }

    Preface preface;
    for (Group agroup : gs) {
      if (agroup.getKey().equalsWithMask(PREFACE_KEY, 0b1111101011111111 /* ignore version and Group coding */)) {
        Set s = Set.fromGroup(agroup);
        preface = (Preface) ClassAdapter.fromSet(s, new MXFInputContext() {
          @Override
          public Set getSet(UUID uuid) {
            return setresolver.get(uuid);
          }
        });
        System.out.println(preface.FileLastModified);

        SourcePackage sp = (SourcePackage) Arrays.stream(preface.ContentStorageObject.Packages).filter(x -> x instanceof SourcePackage).findFirst().orElse(null);

        unitTrack = new Track();
        unitTrack.fileDescriptor = (FileDescriptor) sp.EssenceDescription;

      }
    }

    /*
     * skip over index tables, if any
     */
    if (pp.getIndexSID() != 0) {
      kis.skip(pp.getIndexByteCount());
    }

  }

  boolean nextUnit() throws KLVException, EOFException, IOException {

    if (this.isDone) {
      return false;
    }

    /* exhause current unit */

    if (this.unitPayload != null) {
      this.unitPayload.exhaust();
    }

    /* loop until we find an essence element */
    while (true) {
      AUID auid;

      try {
        auid = kis.readAUID();
      } catch (EOFException e) {
        this.isDone = true;
        return false;
      }

      long len = kis.readBERLength();

      if (PartitionPack.isInstance(auid)) {

        /* skip over partition header metadata and index tables */

        /* partition pack is fixed length so that cast is ok */
        byte[] value = new byte[(int) len];
        kis.readFully(value);
        Triplet t = new MemoryTriplet(auid, value);
        PartitionPack pp = PartitionPack.fromTriplet(t);

        /* we are done when we reach the footer partition */
        if (pp.getKind() == Kind.FOOTER) {
          this.isDone = true;
          return false;
        }

        kis.skip(pp.getHeaderByteCount());
        if (pp.getIndexSID() != 0) {
          kis.skip(pp.getIndexByteCount());
        }

      } else if (FillItem.getKey().equalsIgnoreVersion(auid)) {
      
        /* skip over the fill item */

        kis.skip(len);

      } else {

        /* we have reached an essence element */

        this.unitPayload = new BoundedInputStream(kis, len);
        this.unitLength = len;
        break;
      }
    }

    return true;
  }

  Fraction getUnitOffset() {
    // TODO Auto-generated method stub
    return null;
  }

  Track getUnitTrack() {
    return this.unitTrack;
  }

  long getUnitPayloadLength() {
    return this.unitLength;
  }

  InputStream getUnitPayload() {
    return this.unitPayload;
  }

  Track[] getTracks() {
    // TODO Auto-generated method stub
    return null;
  }

  boolean isDone() {
    return this.isDone;
  }

}
