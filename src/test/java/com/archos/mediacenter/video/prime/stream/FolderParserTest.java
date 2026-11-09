package com.archos.mediacenter.video.prime.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Listings captured from a real h5ai server (DHAKA-FLIX) plus nginx and Apache samples. */
public class FolderParserTest {

    private static final String MCU = "http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20%281080p%29/"
            + "Marvel%20Cinematic%20Universe-MCU%20Collection%201080p%20%5BDual%20Audio%5D/";

    private static String fixture(String name) throws Exception {
        try (InputStream in = FolderParserTest.class.getResourceAsStream("/prime/" + name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void h5aiFolderOfFolders() throws Exception {
        List<FolderParser.Item> items = FolderParser.parse(fixture("h5ai_folders.html"), MCU);
        assertEquals(38, items.size()); // "..", page assets and the 2 posters are skipped
        FolderParser.Item first = items.get(0);
        assertTrue(first.folder);
        assertEquals("01 - Iron Man (2008) 1080p [Dual Audio]", first.name);
        assertEquals(MCU + "01%20-%20Iron%20Man%20%282008%29%201080p%20%5BDual%20Audio%5D/", first.url);
    }

    @Test
    public void h5aiMovieFolder() throws Exception {
        String base = MCU + "35%20-%20Captain%20America-Brave%20New%20World%20%282025%29%201080p%20%5BDual%20Audio%5D/";
        List<FolderParser.Item> items = FolderParser.parse(fixture("h5ai_movie.html"), base);
        assertEquals(1, items.size()); // the .jpg poster is not a video
        FolderParser.Item movie = items.get(0);
        assertFalse(movie.folder);
        assertEquals("Captain America Brave New World (2025) 1080p BluRay x265 HEVC ESub [Dual Audio][Hindi 5.1+English 5.1] -mkvC.mkv",
                movie.name);
        assertEquals(2181297L * 1000, movie.size);
        assertEquals("2.0 GB", FolderParser.formatSize(movie.size));
    }

    @Test
    public void baseWithoutTrailingSlashOrDifferentEncoding() throws Exception {
        String unencoded = "http://172.16.50.14/DHAKA-FLIX-14/English Movies (1080p)/".replace(" ", "%20");
        String html = "<a href=\"/DHAKA-FLIX-14/English%20Movies%20%281080p%29/Thor.mkv\">Thor.mkv</a>";
        assertEquals(1, FolderParser.parse(html, unencoded).size());
        assertEquals(1, FolderParser.parse(html, "http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20%281080p%29").size());
    }

    @Test
    public void unsafeCharactersAreEscaped() {
        assertEquals("http://h/English%20Movies%20(1080p)/%5BDual%5D/", FolderParser.encodeUnsafe("http://h/English Movies (1080p)/[Dual]/"));
        assertEquals("http://h/a%2Bb%20c.mkv", FolderParser.encodeUnsafe("http://h/a%2Bb c.mkv"));
    }

    @Test
    public void nginxAutoindex() {
        String html = "<html><body><h1>Index of /movies/</h1><hr><pre><a href=\"../\">../</a>\n"
                + "<a href=\"Series/\">Series/</a>                 28-May-2025 20:43       -\n"
                + "<a href=\"Thor%20%282011%29.mkv\">Thor (2011).mkv</a>     28-May-2025 20:43     2181297903\n"
                + "<a href=\"notes.txt\">notes.txt</a>                  28-May-2025 20:43     12\n"
                + "</pre><hr></body></html>";
        List<FolderParser.Item> items = FolderParser.parse(html, "http://example.com/movies/");
        assertEquals(2, items.size());
        assertTrue(items.get(0).folder);
        assertEquals("Series", items.get(0).name);
        assertEquals("Thor (2011).mkv", items.get(1).name);
        assertEquals(2181297903L, items.get(1).size);
    }

    @Test
    public void apacheIndexSkipsSortLinksAndOtherSites() {
        String html = "<table><tr><th><a href=\"?C=N;O=D\">Name</a></th></tr>"
                + "<tr><td><a href=\"/\">Parent Directory</a></td></tr>"
                + "<tr><td><a href=\"Movie.mp4\">Movie.mp4</a></td><td>2024-01-01 10:00</td><td>700M</td></tr>"
                + "<tr><td><a href=\"https://other.site/x.mp4\">x</a></td></tr></table>";
        List<FolderParser.Item> items = FolderParser.parse(html, "http://example.com/dir/");
        assertEquals(1, items.size());
        assertEquals(700L * 1024 * 1024, items.get(0).size);
    }
}
