package com.sandflow.smpte.mxf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.Group;
import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.PartitionPack.Kind;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.MaterialPackage;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.BoundedInputStream;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.EventHandler;

/**
 * StreamingReader provides a streaming interface to read MXF (Material Exchange
 * Format) files.
 */
public class StreamingReader {

  /**
   * Represents information associated with an Essence Track.
   *
   * @param descriptor File descriptor associated with the track.
   * @param track      Track metadata.
   * @param container  Essence container reference.
   */
  public record TrackInfo(
      FileDescriptor descriptor,
      Track track,
      EssenceData container) {
  }

  /**
   * Represents the state of a track during streaming,
   * including its current playback position.
   */
  private class TrackState {
    Fraction currentPosition;
    TrackInfo info;

    TrackState(TrackInfo info) {
      this.currentPosition = Fraction.from(0);
      this.info = info;
    }
  }

  private MXFInputStream mis;
  private boolean isDone = false;
  private Preface preface;
  private ArrayList<TrackState> tracks = new ArrayList<TrackState>();

  protected static Preface readHeaderMetadataFrom(InputStream is, long headerByteCount, EventHandler evthandler)
      throws IOException, KLVException, MXFException {
    MXFInputStream mis = new MXFInputStream(is);

    /* look for the primer pack */
    LocalTagRegister localreg = null;
    for (Triplet t; (t = mis.readTriplet()) != null; mis.resetCount()) {

      /* skip fill items, if any */
      if (!FillItem.getKey().equalsIgnoreVersion(t.getKey())) {
        localreg = PrimerPack.createLocalTagRegister(t);
        break;
      }
    }

    if (localreg == null) {
      MXFEvent.handle(evthandler, new MXFEvent(
          MXFEvent.EventCodes.MISSING_PRIMER_PACK,
          "No Primer Pack found"));
    }

    /*
     * capture all local sets within the header metadata
     */
    HashMap<UUID, Set> setresolver = new HashMap<>();

    while (mis.getCount() < headerByteCount) {

      Triplet t = mis.readTriplet();

      /* skip fill items */
      /* TODO: replace with call to FillItem static method */
      if (FillItem.getKey().equalsIgnoreVersion(t.getKey())) {
        continue;
      }

      try {
        Set s = Set.fromLocalSet(t, localreg);

        if (s != null) {

          UUID instanceID = HeaderMetadataSet.getInstanceID(s);
          if (instanceID != null) {
            setresolver.put(instanceID, s);
          }

        } else {
          MXFEvent.handle(evthandler, new MXFEvent(
              MXFEvent.EventCodes.GROUP_READ_FAILED,
              String.format("Failed to read Group: {0}", t.getKey().toString())));
        }
      } catch (KLVException ke) {
        MXFEvent.handle(evthandler, new MXFEvent(
            MXFEvent.EventCodes.GROUP_READ_FAILED,
            String.format("Failed to read Group %s with error %s", t.getKey().toString(), ke.getMessage())));
      }
    }

    /*
     * check that the header metadata length is consistent
     */
    if (mis.getCount() != headerByteCount) {
      MXFEvent.handle(evthandler, new MXFEvent(
          MXFEvent.EventCodes.INCONSISTENT_HEADER_LENGTH,
          String.format("Actual Header Metadata length (%s) does not match the Partition Pack information (%s)",
              mis.getCount(), headerByteCount)));
    }

    MXFInputContext mic = new MXFInputContext() {
      @Override
      public Set getSet(UUID uuid) {
        return setresolver.get(uuid);
      }
    };

    for (Set s : setresolver.values()) {
      if (Preface.getKey().equalsIgnoreVersionAndGroupCoding(s.getKey())) {
        return Preface.fromSet(s, mic);
      }
    }

    return null;
  }

  /**
   * Creates a new StreamingReader from an InputStream.
   *
   * @param is         InputStream containing the MXF file data.
   * @param evthandler Event handler for reporting parsing events or
   *                   inconsistencies.
   */
  StreamingReader(InputStream is, EventHandler evthandler) throws IOException, KLVException, MXFException {
    if (is == null) {
      throw new NullPointerException("InputStream cannot be null");
    }

    /* TODO: handle byte ordering */

    mis = new MXFInputStream(is);

    /* look for the header partition pack */
    PartitionPack pp = null;
    for (Triplet t; (t = mis.readTriplet()) != null;) {
      if ((pp = PartitionPack.fromTriplet(t)) != null) {
        break;
      }
    }

    if (pp == null) {
      MXFEvent.handle(evthandler,
          new MXFEvent(MXFEvent.EventCodes.MISSING_PARTITION_PACK, "No Partition Pack found"));
    }

    this.preface = readHeaderMetadataFrom(mis, pp.getHeaderByteCount(), evthandler);

    /* TODO: handle NULL preface */

    /* collect tracks that are stored in essence containers */
    for (EssenceData ed : this.preface.ContentStorageObject.EssenceDataObjects) {

      /* retrieve the File Package */
      SourcePackage fp = null;
      for (Package p : preface.ContentStorageObject.Packages) {
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
        fds = new FileDescriptor[] { (FileDescriptor) fp.EssenceDescription };
      }

      for (FileDescriptor fd : fds) {
        Track foundTrack = null;

        for (Track t : fp.PackageTracks) {
          if (t.TrackID == fd.LinkedTrackID) {
            foundTrack = t;
            break;
          }
        }

        /* TODO: handle missing Track */

        this.tracks.add(new TrackState(new TrackInfo(fd, foundTrack, ed)));
      }
    }

    /* find the first material package */
    /* TODO: what to do if more than one Material Package */

    MaterialPackage mp;
    for (Package p : preface.ContentStorageObject.Packages) {
      if (p instanceof MaterialPackage) {
        mp = (MaterialPackage) p;
        break;
      }
    }

    /*
     * skip over index tables, if any
     */
    if (pp.getIndexSID() != 0) {
      mis.skip(pp.getIndexByteCount());
    }

  }

  private long currentSID;
  private BoundedInputStream currentPlayload;
  private long currentPayloadLength;
  private TrackInfo currentTrackInfo;
  private Fraction currentOffset;

  /**
   * Advances the stream to the next essence unit.
   *
   * @return true if a new unit is available; false if end of stream.
   * @throws IOException  if an I/O error occurs.
   * @throws KLVException if a KLV reading error occurs.
   */
  boolean nextUnit() throws KLVException, IOException {

    if (this.isDone) {
      return false;
    }

    /* exhaust current unit */
    if (this.currentPlayload != null) {
      this.currentPlayload.exhaust();
    }

    /* loop until we find an essence element */
    while (true) {
      AUID auid;

      try {
        auid = mis.readAUID();
      } catch (EOFException e) {
        this.isDone = true;
        return false;
      }

      long len = mis.readBERLength();

      if (PartitionPack.isInstance(auid)) {

        /* skip over partition header metadata and index tables */

        /* partition pack is fixed length so that cast is ok */
        byte[] value = new byte[(int) len];
        mis.readFully(value);
        Triplet t = new MemoryTriplet(auid, value);
        PartitionPack pp = PartitionPack.fromTriplet(t);

        /* we are done when we reach the footer partition */
        if (pp.getKind() == Kind.FOOTER) {
          this.isDone = true;
          return false;
        }

        this.currentSID = pp.getBodySID();

        mis.skip(pp.getHeaderByteCount());
        if (pp.getIndexSID() != 0) {
          mis.skip(pp.getIndexByteCount());
        }

      } else if (FillItem.getKey().equalsIgnoreVersion(auid)) {

        /* skip over the fill item */

        mis.skip(len);

      } else {

        /* we have reached an essence element */
        UL essenceKey = auid.asUL();
        long trackNum = MXFFiles.getTrackNumber(essenceKey);

        /* find track info */
        this.currentTrackInfo = null;
        for (TrackState trackState : this.tracks) {
          if (trackState.info.container.EssenceStreamID == currentSID &&
              trackState.info.track.EssenceTrackNumber == trackNum) {
            this.currentOffset = trackState.currentPosition;
            trackState.currentPosition = trackState.currentPosition
                .add(trackState.info.descriptor.SampleRate.reciprocal());
            this.currentTrackInfo = trackState.info;
            break;
          }
        }

        this.currentPlayload = new BoundedInputStream(mis, len);
        this.currentPayloadLength = len;
        break;
      }
    }

    return true;
  }

  /**
   * Returns the temporal offset of the current unit.
   *
   * @return Fractional offset from start.
   */
  Fraction getUnitOffset() {
    return this.currentOffset;
  }

  /**
   * Returns metadata about the current essence unit's track.
   *
   * @return TrackInfo object associated with the current unit.
   */
  TrackInfo getUnitTrackInfo() {
    return this.currentTrackInfo;
  }

  /**
   * Returns the length of the current payload in bytes.
   *
   * @return Payload length.
   */
  long getUnitPayloadLength() {
    return this.currentPayloadLength;
  }

  /**
   * Returns an InputStream over the current essence payload.
   *
   * @return InputStream for reading payload data.
   */
  InputStream getUnitPayload() {
    return this.currentPlayload;
  }

  /**
   * Returns the TrackInfo for a specific track.
   *
   * @param i index of the track.
   * @return TrackInfo object.
   */
  TrackInfo getTrack(int i) {
    return this.tracks.get(i).info;
  }

  /**
   * Returns the number of essence tracks available.
   *
   * @return number of tracks.
   */
  int getTrackCount() {
    return this.tracks.size();
  }

  /**
   * Checks if the stream has reached the end.
   *
   * @return true if done reading, false otherwise.
   */
  boolean isDone() {
    return this.isDone;
  }

  /**
   * Returns the Preface metadata object parsed from the MXF file.
   *
   * @return Preface object.
   */
  Preface getPreface() {
    return this.preface;
  }

}
