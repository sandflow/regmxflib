/*
 * Copyright (c) Pierre-Anthony Lemieux (pal@sandflow.com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.sandflow.smpte.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Counts the number of bytes read from an InputStream and allows a maximum
 * number of bytes to be read to be set (optionally)
 */
public class BoundedInputStream extends CountingInputStream {

    long maxCount = -1;

    /**
     * Instantiates a CountingInputStream
     *
     * @param is       InputStream from which data will be read
     * @param maxCount Maximum number of bytes to be read. If set to -1 or less, no
     *                 limit.
     */
    public BoundedInputStream(InputStream is, long maxCount) {
        super(is);
        this.maxCount = Math.max(-1, maxCount);
    }

    @Override
    public long skip(long l) throws IOException {
        long actualSkip = maxCount < 0 ? l : Math.min(Math.max(0, maxCount - getCount()), l);
        return super.skip(actualSkip);
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        int actualLen = maxCount >= 0 ? len : Math.min((int) Math.max(maxCount - getCount(), Integer.MAX_VALUE), len);
        return super.read(bytes, off, actualLen);
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        return this.read(bytes, 0, bytes.length);
    }

    @Override
    public int read() throws IOException {
        if (maxCount >= -1 && maxCount - count < 1) {
            return -1;
        }
        return super.read();
    }

    /**
     * Skips to the end of the stream, if the maximum number of bytes to be read
     * was set to a value greater than -1. Otherwise, it does nothing.
     * 
     * @return Returns the number of bytes skipped, or -1 if no limit was set.
     */
    public long exhaust() throws IOException {
        if (maxCount < 0) {
            return -1;
        }
        long count = maxCount - getCount();
        while (maxCount > getCount())
            this.skip(maxCount - getCount());
        return count;
    }


}
