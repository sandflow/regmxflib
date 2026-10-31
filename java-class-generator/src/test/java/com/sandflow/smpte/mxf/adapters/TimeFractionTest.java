package com.sandflow.smpte.mxf.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.mxf.MXFDataInput;
import com.sandflow.smpte.mxf.MXFEvent;
import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.Event;

public class TimeFractionTest {
  private final List<Event> events = new ArrayList<>();

  private final MXFInputContext ctx = new MXFInputContext() {
    @Override
    public Set getSet(UUID uuid) {
      throw new UnsupportedOperationException("Unimplemented method 'getSet'");
    }

    @Override
    public void handleEvent(Event evt) {
      events.add(evt);
    }
  };

  private static MXFDataInput stream(int... bytes) {
    byte[] b = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      b[i] = (byte) bytes[i];
    }
    return new MXFDataInput(new ByteArrayInputStream(b));
  }

  @Test
  void testTimeAtMaximum() throws IOException {
    assertEquals(LocalTime.of(1, 2, 3, 996_000_000), LocalTimeAdapter.fromStream(stream(1, 2, 3, 249), ctx));
    assertEquals(0, events.size());
  }

  @Test
  void testTimeCapped() throws IOException {
    assertEquals(LocalTime.of(1, 2, 3, 996_000_000), LocalTimeAdapter.fromStream(stream(1, 2, 3, 250), ctx));
    assertEquals(1, events.size());
    assertEquals(MXFEvent.EventCodes.INVALID_TIME_FRACTION, events.get(0).getCode());
  }

  @Test
  void testTimeStampCapped() throws IOException {
    assertEquals(LocalDateTime.of(2026, 10, 31, 1, 2, 3, 996_000_000),
        LocalDateTimeAdapter.fromStream(stream(0x07, 0xEA, 10, 31, 1, 2, 3, 255), ctx));
    assertEquals(1, events.size());
    assertEquals(MXFEvent.EventCodes.INVALID_TIME_FRACTION, events.get(0).getCode());
  }
}
