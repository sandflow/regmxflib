package com.sandflow.smpte.mxf;

import java.io.EOFException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
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
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.EssenceDescriptor;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.MaterialPackage;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.Track;
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


  KLVInputStream kis;
  boolean isDone = false;

  BoundedInputStream unitPayload;
  Long unitLength;
  AUID unitKey;
  Preface preface;

  ArrayList<TrackInfo> tracks = new ArrayList<TrackInfo>();

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

    MXFInputContext mic = new MXFInputContext() {
      @Override
      public Set getSet(UUID uuid) {
        return setresolver.get(uuid);
      }
    };

    for (Group agroup : gs) {
      if (Preface.getKey().equalsIgnoreVersionAndGroupCoding(agroup.getKey())) {
        Set s = Set.fromGroup(agroup);
        this.preface = Preface.fromSet(s, mic);

        /* collect tracks that are stored in essence containers */
        for(EssenceData ed : this.preface.ContentStorageObject.EssenceDataObjects) {

          /* retrieve the File Package */
          SourcePackage fp = null;
          for(Package p : preface.ContentStorageObject.Packages) {
            if (p.PackageID.equals(ed.LinkedPackageID)) {
              fp = (SourcePackage) p;
              break;
            }
          }

          /* TODO: error in case no package is found */

          /* do we have a multi-descriptor */
          FileDescriptor fds[] = null;
          if (fp.EssenceDescription instanceof MultipleDescriptor) {
            fds = ((MultipleDescriptor) fp.EssenceDescription).SubDescriptors.toArray(fds);
          } else {
            fds = new FileDescriptor[] {(FileDescriptor) fp.EssenceDescription};
          }

          for(FileDescriptor fd : fds) {
            Track foundTrack = null;

            for(Track t : fp.PackageTracks) {
              if (t.TrackID == fd.LinkedTrackID) {
                foundTrack = t;
                break;
              }
            }

            /* TODO: handle missing Track */

            TrackInfo ts = new TrackInfo();

            ts.container = ed;
            ts.descriptor = fd;
            ts.track = foundTrack;
            ts.position = Fraction.from(0);

            this.tracks.add(ts);
          }
        }

        /* find the first material package */
        /* TODO: what to do if more than one Material Package */

        MaterialPackage mp;
        for(Package p : preface.ContentStorageObject.Packages) {
          if (p instanceof MaterialPackage) {
            mp = (MaterialPackage) p;
            break;
          }
        }

      }
    }

    /*
     * skip over index tables, if any
     */
    if (pp.getIndexSID() != 0) {
      kis.skip(pp.getIndexByteCount());
    }

  }

  public class TrackInfo {
    Fraction position;
    FileDescriptor descriptor;
    Track track;
    EssenceData container;
  }

  long currentSID;
  TrackInfo unitTrackInfo;

  boolean nextUnit() throws KLVException, EOFException, IOException {

    if (this.isDone) {
      return false;
    }

    /* exhaust current unit */

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

        this.currentSID = pp.getBodySID();

        kis.skip(pp.getHeaderByteCount());
        if (pp.getIndexSID() != 0) {
          kis.skip(pp.getIndexByteCount());
        }

      } else if (FillItem.getKey().equalsIgnoreVersion(auid)) {

        /* skip over the fill item */

        kis.skip(len);

      } else {

        /* we have reached an essence element */
        UL essenceKey = auid.asUL();
        long trackNum = (essenceKey.getValueOctet(12) << 24) +
          (essenceKey.getValueOctet(13) << 16) +
          (essenceKey.getValueOctet(14) << 8) +
          essenceKey.getValueOctet(15);

        /* find track info */
        this.unitTrackInfo = null;
        for (TrackInfo trackInfo : this.tracks) {
          if (trackInfo.container.EssenceStreamID == currentSID &&
              trackInfo.track.EssenceTrackNumber == trackNum) {
                this.unitTrackInfo = trackInfo;
                break;
              }
        }

        this.unitPayload = new BoundedInputStream(kis, len);
        this.unitLength = len;
        break;
      }
    }

    return true;
  }

  Fraction getUnitOffset() {
    return this.unitTrackInfo.position;
  }

  TrackInfo getUnitTrackInfo() {
    return this.unitTrackInfo;
  }

  long getUnitPayloadLength() {
    return this.unitLength;
  }

  InputStream getUnitPayload() {
    return this.unitPayload;
  }

  Collection<TrackInfo> getTracks() {
    return this.tracks;
  }

  boolean isDone() {
    return this.isDone;
  }

  Preface getPreface() {
    return this.preface;
  }

}
