package com.archos.mediacenter.video.prime.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StreamLinkTest {

    @Test
    public void directHttpFile() {
        StreamLink l = StreamLink.parse("http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20%281080p%29/Thor%20Ragnarok%20%282017%29%201080p.mkv");
        assertEquals(StreamLink.Kind.DIRECT, l.kind);
        assertEquals("video/x-matroska", l.mimeType);
        assertEquals("Thor Ragnarok (2017) 1080p", l.guessedName);
        assertFalse(l.looksLikeFolder());
        assertFalse(l.needsNameLookup());
    }

    @Test
    public void folderLink() {
        StreamLink l = StreamLink.parse("http://172.16.50.14/DHAKA-FLIX-14/English%20Movies/");
        assertTrue(l.looksLikeFolder());
    }

    @Test
    public void googleDrive() {
        StreamLink l = StreamLink.parse("https://drive.google.com/file/d/1AbCdEfGhIjKlMnOp_qrs-TUV/view?usp=sharing");
        assertEquals(StreamLink.Kind.GOOGLE_DRIVE, l.kind);
        assertEquals("https://drive.usercontent.google.com/download?id=1AbCdEfGhIjKlMnOp_qrs-TUV&export=download&confirm=t",
                l.playUri.toString());
        assertTrue(l.needsNameLookup());
        StreamLink open = StreamLink.parse("https://drive.google.com/open?id=1AbCdEfGhIjKlMnOp_qrs-TUV");
        assertEquals(l.playUri, open.playUri);
    }

    @Test
    public void dropbox() {
        StreamLink l = StreamLink.parse("https://www.dropbox.com/scl/fi/abc/My%20Movie.mp4?rlkey=xyz&dl=0");
        assertEquals(StreamLink.Kind.DROPBOX, l.kind);
        assertEquals("1", l.playUri.getQueryParameter("dl"));
        assertEquals("xyz", l.playUri.getQueryParameter("rlkey"));
        assertEquals("My Movie", l.guessedName);
    }

    @Test
    public void hlsAndTextAroundLink() {
        StreamLink l = StreamLink.parse("Watch this: https://example.com/live/index.m3u8 enjoy");
        assertEquals("https://example.com/live/index.m3u8", l.original);
        assertEquals("application/x-mpegurl", l.mimeType);
    }

    @Test
    public void noScheme() {
        StreamLink l = StreamLink.parse("example.com/video.mp4");
        assertEquals("http://example.com/video.mp4", l.original);
    }

    @Test
    public void garbage() {
        assertNull(StreamLink.parse("hello world"));
        assertNull(StreamLink.parse(""));
        assertNull(StreamLink.parse(null));
    }

    @Test
    public void contentDisposition() {
        assertEquals("Thor (2017).mkv", StreamLink.fileNameFromContentDisposition("attachment; filename=\"Thor (2017).mkv\""));
        assertEquals("Thor (2017).mkv", StreamLink.fileNameFromContentDisposition("attachment; filename*=UTF-8''Thor%20(2017).mkv"));
    }
}
