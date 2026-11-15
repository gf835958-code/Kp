package com.kp.recapstudio;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Verifies long input is split without dropping/duplicating PCM frames. */
public class OfflineWavChunkerTest {
    private static void put32(RandomAccessFile f, long n) throws IOException {
        for (int i=0; i<4; i++) f.write((int)(n >>> (8*i)) & 255);
    }
    private static void put16(RandomAccessFile f, int n) throws IOException {
        f.write(n & 255); f.write((n >>> 8)&255);
    }
    private static File makeWav(int seconds) throws IOException {
        File file = File.createTempFile("kp-chunk-test-", ".wav");
        long bytes = seconds * 16000L * 2L;
        try(RandomAccessFile out = new RandomAccessFile(file, "rw")) {
            out.setLength(0);
            out.write("RIFF".getBytes(StandardCharsets.US_ASCII)); put32(out,bytes+36);
            out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
            put32(out,16);put16(out,1);put16(out,1);put32(out,16000);
            put32(out,32000);put16(out,2);put16(out,16);
            out.write("data".getBytes(StandardCharsets.US_ASCII));put32(out,bytes);
            out.setLength(bytes+44);
            out.seek(44+27*32000L);
            if (seconds > 27) out.write(new byte[]{0x56, 0x34});
        }
        return file;
    }
    @Test public void coversFullAudioIncludingLastPart() throws Exception {
        File input=makeWav(28),first=File.createTempFile("kp-first-", ".wav"),
            last=File.createTempFile("kp-last-", ".wav");
        try {
            assertEquals(2,OfflineWavChunker.count(input,27));
            assertEquals(28*16000L,OfflineWavChunker.frames(input));
            OfflineWavChunker.writeChunk(input,first,0,27);
            OfflineWavChunker.writeChunk(input,last,1,27);
            assertEquals(27*16000L,OfflineWavChunker.frames(first));
            assertEquals(16000L,OfflineWavChunker.frames(last));
            try(RandomAccessFile tail=new RandomAccessFile(last,"r")) {
                tail.seek(44);
                assertEquals(0x56,tail.readUnsignedByte());
                assertEquals(0x34,tail.readUnsignedByte());
            }
        } finally {input.delete();first.delete();last.delete();}
    }

    @Test public void exactLengthSingleChunk() throws Exception {
        File src=makeWav(27);
        try{assertEquals(1,OfflineWavChunker.count(src,27));}
        finally{src.delete();}
    }

    @Test(expected=IOException.class)
    public void invalidAudioRejected() throws Exception {
        File src=File.createTempFile("kp-notwav-", ".wav");
        try { OfflineWavChunker.count(src,27); }
        finally {src.delete();}
    }

    @Test(expected=IOException.class)
    public void rejectsOversizedWhisperWindow() throws Exception {
        File src=makeWav(2);
        try { OfflineWavChunker.count(src,30); }
        finally {src.delete();}
    }
}
