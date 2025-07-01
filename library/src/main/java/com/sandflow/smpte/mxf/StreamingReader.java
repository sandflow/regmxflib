package com.sandflow.smpte.mxf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.MXFFiles.ElementInfo;
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
public class StreamingReader extends InputStream {

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
  protected static class TrackState {
    long position;
    TrackInfo info;

    TrackState(TrackInfo info) {
      this.position = -1;
      this.info = info;
    }
  }

  enum State {
    READY,
    IN_PAYLOAD,
    DONE
  }

  private MXFInputStream mis;
  private State state;
  private final Preface preface;
  private final List<TrackState> tracks;
  private ElementInfo elementInfo;
  private long remainingElementBytes = 0;

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
      if (FillItem.isInstance(t.getKey())) {
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

    /* we can only handle a single essence container at this point */
    if (this.preface.ContentStorageObject.EssenceDataObjects.size() != 1) {
      throw new RuntimeException("Only one essence container supported");
    }

    /* we can only handle one material package at this point */
    if (this.preface.ContentStorageObject.Packages.stream().filter(e -> e instanceof MaterialPackage).count() != 1) {
      throw new RuntimeException("Only one material package supported");
    }

    this.streamID = this.preface.ContentStorageObject.EssenceDataObjects.get(0).EssenceStreamID;

    this.tracks = StreamingReader.extractTracks(this.preface);

    /*
     * skip over index tables, if any
     */
    if (pp.getIndexSID() != 0) {
      mis.skipFully(pp.getIndexByteCount());
    }

    this.state = State.READY;
  }

  protected static List<TrackState> extractTracks(Preface preface) {
    ArrayList<TrackState> tracks = new ArrayList<>();

    /* collect tracks that are stored in essence containers */
    for (EssenceData ed : preface.ContentStorageObject.EssenceDataObjects) {

      /* retrieve the File Package */
      SourcePackage fp = null;
      for (Package p : preface.ContentStorageObject.Packages) {
        if (p.PackageID.equals(ed.LinkedPackageID)) {
          fp = (SourcePackage) p;
          break;
        }
      }

      if (fp == null) {
        throw new RuntimeException("No file packages found");
      }

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

        tracks.add(new TrackState(new TrackInfo(fd, foundTrack, ed)));
      }
    }

    return tracks;
  }

  private long streamID;
  private int elementTrackIndex;

  /**
   * Advances the stream to the next essence unit.
   *
   * @return true if a new unit is available; false if end of stream.
   * @throws IOException  if an I/O error occurs.
   * @throws KLVException if a KLV reading error occurs.
   */
  public boolean nextElement() throws KLVException, IOException {

    if (this.state == State.DONE) {
      return false;
    }

    this.mis.skipFully(this.remainingElementBytes);

    this.elementInfo = MXFFiles.nextElement(this.mis);

    if (this.elementInfo == null) {
      this.state = State.DONE;
      return false;
    }

    /* we have reached an essence element */
    long trackNum = MXFFiles.getTrackNumber(this.elementInfo.key().asUL());

    /* find track info */
    this.elementTrackIndex = -1;
    for (int i = 0; i < this.tracks.size(); i++) {
      TrackState ts = this.tracks.get(i);
      if (ts.info.container().EssenceStreamID == this.streamID &&
          ts.info.track().EssenceTrackNumber == trackNum) {
        this.elementTrackIndex = i;
        break;
      }
    }

    /* TODO: error if no track info found */

    this.tracks.get(this.elementTrackIndex).position++;

    this.remainingElementBytes = this.elementInfo.length();

    System.out.println(this.remainingElementBytes);

    this.state = State.IN_PAYLOAD;

    return true;
  }

  /**
   * Returns the temporal offset of the current unit.
   *
   * @return Offset in number of track edit units.
   */
  public long getElementPosition() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.tracks.get(this.elementTrackIndex).position;
  }

  /**
   * Returns metadata about the current essence unit's track.
   *
   * @return TrackInfo object associated with the current unit.
   */
  public TrackInfo getElementTrackInfo() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.tracks.get(this.elementTrackIndex).info;
  }

  /**
   * Returns the length of the current payload in bytes.
   *
   * @return Payload length.
   */
  public long getElementLength() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.elementInfo.length();
  }

  /**
   * Returns the TrackInfo for a specific track.
   *
   * @param i index of the track.
   * @return TrackInfo object.
   */
  public TrackInfo getTrack(int i) {
    return this.tracks.get(i).info;
  }

  /**
   * Returns the number of essence tracks available.
   *
   * @return number of tracks.
   */
  public int getTrackCount() {
    return this.tracks.size();
  }

  public boolean isDone() {
    return this.state == State.DONE;
  }

  /**
   * Returns the Preface metadata object parsed from the MXF file.
   *
   * @return Preface object.
   */
  public Preface getPreface() {
    return this.preface;
  }

  @Override
  public int read() throws IOException {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    if (this.remainingElementBytes == 0)
      return -1;
    int r = this.mis.read();
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - 1;
    return r;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    if (this.remainingElementBytes == 0)
      return -1;
    int r = this.mis.read(b, off, len);
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - r;
    return r;
  }

  @Override
  public long skip(long n) throws IOException {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    if (this.remainingElementBytes == 0)
      return -1;
    long s = this.mis.skip(n);
    this.remainingElementBytes = this.remainingElementBytes - s;
    return s;
  }

  @Override
  public void close() throws IOException {
    /*
     * do nothing: it is the responsibility of the caller to close the
     * underlying RandomAccessInputSource
     */
  }

}
