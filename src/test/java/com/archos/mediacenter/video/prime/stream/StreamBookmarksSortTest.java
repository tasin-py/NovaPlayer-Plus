package com.archos.mediacenter.video.prime.stream;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Bookmark names sort A to Z with numbers in number order. */
public class StreamBookmarksSortTest {

    @Test
    public void numbersSortAsNumbers() {
        List<String> names = Arrays.asList("Season 10", "season 2", "Movies", "Season 1", "Season 02x", "Season 100", "anime");
        names.sort(StreamBookmarks::compareNames);
        assertEquals(Arrays.asList("anime", "Movies", "Season 1", "season 2", "Season 02x", "Season 10", "Season 100"), names);
    }

    @Test
    public void hugeNumbersDoNotOverflow() {
        List<String> names = Arrays.asList("Show 99999999999999999999", "Show 3");
        names.sort(StreamBookmarks::compareNames);
        assertEquals(Arrays.asList("Show 3", "Show 99999999999999999999"), names);
    }
}
