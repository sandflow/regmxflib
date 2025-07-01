package com.sandflow.smpte.mxf;

import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.UL;

public interface HeaderInfo {

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

  TrackInfo getTrack(int i);

  int getTrackCount();

  Preface getPreface();

  TrackInfo getTrackInfo(UL elementKey);

}