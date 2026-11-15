package com.kp.recapstudio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class OfflineTranscriptCheckpointsTest {
    @Test public void checkpointResumeIncludingSilentChunk() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "kp-asr-resume-test-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            assertNull(OfflineTranscriptCheckpoints.read(dir, 0));
            OfflineTranscriptCheckpoints.write(dir, 0, "မင်္ဂလာပါ");
            OfflineTranscriptCheckpoints.write(dir, 1, "");
            assertEquals("မင်္ဂလာပါ", OfflineTranscriptCheckpoints.read(dir, 0));
            assertEquals("", OfflineTranscriptCheckpoints.read(dir, 1));
            assertNull(OfflineTranscriptCheckpoints.read(dir, 2));
            OfflineTranscriptCheckpoints.write(dir, 1, "အဆင်ပြေပါတယ်");
            assertEquals("အဆင်ပြေပါတယ်", OfflineTranscriptCheckpoints.read(dir, 1));
        } finally {
            File[] all=dir.listFiles();if(all!=null)for(File f:all)f.delete();
            dir.delete();
        }
    }

    @Test(expected = IOException.class)
    public void invalidChunkIndexFails() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "kp-asr-bad-index-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            OfflineTranscriptCheckpoints.write(dir, -1, "bad");
        } finally {
            dir.delete();
        }
    }

    @Test(expected = IOException.class)
    public void oversizedTranscriptIsRejected() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "kp-asr-bad-data-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            StringBuilder large = new StringBuilder();
            for (int i=0;i<250_000;i++) large.append('a');
            OfflineTranscriptCheckpoints.write(dir, 0, large.toString());
        } finally {
            dir.delete();
        }
    }
}
