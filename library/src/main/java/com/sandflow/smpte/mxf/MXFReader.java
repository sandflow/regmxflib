package com.sandflow.smpte.mxf;

import java.io.InputStream;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.Track;

public interface MXFReader {
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
   * Returns the temporal offset of the current unit.
   *
   * @return Fractional offset from start.
   */
  Fraction getUnitOffset();

  /**
   * Returns metadata about the current essence unit's track.
   *
   * @return TrackInfo object associated with the current unit.
   */
  TrackInfo getUnitTrackInfo();

  /**
   * Returns the length of the current payload in bytes.
   *
   * @return Payload length.
   */
  long getUnitPayloadLength();

  /**
   * Returns an InputStream over the current essence payload.
   *
   * @return InputStream for reading payload data.
   */
  InputStream getUnitPayload();

  /**
   * Returns the TrackInfo for a specific track.
   *
   * @param i index of the track.
   * @return TrackInfo object.
   */
  TrackInfo getTrack(int i);

  /**
   * Returns the number of essence tracks available.
   *
   * @return number of tracks.
   */
  int getTrackCount();

  /**
   * Checks if the stream has reached the end.
   *
   * @return true if done reading, false otherwise.
   */
  boolean isDone();

  /**
   * Returns the Preface metadata object parsed from the MXF file.
   *
   * @return Preface object.
   */
  Preface getPreface();
}
