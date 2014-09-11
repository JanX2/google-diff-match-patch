/*
 * Diff Match and Patch
 *
 * Copyright 2006 Google Inc.
 * http://code.google.com/p/google-diff-match-patch/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package name.fraser.neil.plaintext;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class DiffMatchPatchTest {

    private static final DiffMatchPatch.Operation DELETE = DiffMatchPatch.Operation.DELETE;
    private static final DiffMatchPatch.Operation EQUAL = DiffMatchPatch.Operation.EQUAL;
    private static final DiffMatchPatch.Operation INSERT = DiffMatchPatch.Operation.INSERT;
    private DiffMatchPatch diffMatchPatch;

    @Before
    public void createDiffUtilInstance() {
        diffMatchPatch = new DiffMatchPatch.Builder().build();
    }

    @Test
    public void diffCommonPrefix() {
        assertThat(diffMatchPatch.diffCommonPrefix("abc", "xyz"), is(0));
        assertThat(diffMatchPatch.diffCommonPrefix("1234abcdef", "1234xyz"), is(4));
        assertThat(diffMatchPatch.diffCommonPrefix("1234", "1234xyz"), is(4));
    }

    @Test
    public void diffCommonSuffix() {
        assertThat(diffMatchPatch.diffCommonSuffix("abc", "xyz"), is(0));
        assertThat(diffMatchPatch.diffCommonSuffix("abcdef1234", "xyz1234"), is(4));
        assertThat(diffMatchPatch.diffCommonSuffix("1234", "xyz1234"), is(4));
    }

    @Test
    public void diffCommonOverlap() {
        assertThat(diffMatchPatch.diffCommonOverlap("", "abcd"), is(0));
        assertThat(diffMatchPatch.diffCommonOverlap("abc", "abcd"), is(3));
        assertThat(diffMatchPatch.diffCommonOverlap("123456", "abcd"), is(0));
        assertThat(diffMatchPatch.diffCommonOverlap("123456xxx", "xxxabcd"), is(3));

        // Some overly clever languages (C#) may treat ligatures as equal to their
        // component letters.  E.g. U+FB01 == 'fi'
        assertThat(diffMatchPatch.diffCommonOverlap("fi", "\ufb01i"), is(0));
    }

    @Test
    public void diffHalfmatch() {
        DiffMatchPatch diffMatchPatch = DiffMatchPatch.builder().patchTimeout(1).build();
        assertThat(diffMatchPatch.diffHalfMatch("1234567890", "abcdef"), is(nullValue()));
        assertThat(diffMatchPatch.diffHalfMatch("12345", "23"), is(nullValue()));
        assertThat(diffMatchPatch.diffHalfMatch("1234567890", "a345678z"), equalTo(new String[]{"12", "90", "a", "z", "345678"}));
        assertThat(diffMatchPatch.diffHalfMatch("a345678z", "1234567890"), equalTo(new String[]{"a", "z", "12", "90", "345678"}));
        assertThat(diffMatchPatch.diffHalfMatch("abc56789z", "1234567890"), equalTo(new String[]{"abc", "z", "1234", "0", "56789"}));
        assertThat(diffMatchPatch.diffHalfMatch("a23456xyz", "1234567890"), equalTo(new String[]{"a", "xyz", "1", "7890", "23456"}));
        assertThat(diffMatchPatch.diffHalfMatch("121231234123451234123121", "a1234123451234z"), equalTo(new String[]{"12123", "123121", "a", "z", "1234123451234"}));
        assertThat(diffMatchPatch.diffHalfMatch("x-=-=-=-=-=-=-=-=-=-=-=-=", "xx-=-=-=-=-=-=-="), equalTo(new String[]{"", "-=-=-=-=-=", "x", "", "x-=-=-=-=-=-=-="}));
        assertThat(diffMatchPatch.diffHalfMatch("-=-=-=-=-=-=-=-=-=-=-=-=y", "-=-=-=-=-=-=-=yy"), equalTo(new String[]{"-=-=-=-=-=", "", "", "y", "-=-=-=-=-=-=-=y"}));
        assertThat(diffMatchPatch.diffHalfMatch("qHilloHelloHew", "xHelloHeHulloy"), equalTo(new String[]{"qHillo", "w", "x", "Hulloy", "HelloHe"}));
        diffMatchPatch = DiffMatchPatch.builder().patchTimeout(0).build();
        assertThat(diffMatchPatch.diffHalfMatch("qHilloHelloHew", "xHelloHeHulloy"), is(nullValue()));
    }

    @Test
    public void diffLinesToChars() {
        // Convert lines down to characters.
        List<String> tmpVector = new ArrayList<String>();
        tmpVector.add("");
        tmpVector.add("alpha\n");
        tmpVector.add("beta\n");
        assertLinesToCharsResultEquals("diffLinesToChars: Shared lines.", new DiffMatchPatch.LinesToCharsResult("\u0001\u0002\u0001", "\u0002\u0001\u0002", tmpVector), diffMatchPatch.diffLinesToChars("alpha\nbeta\nalpha\n", "beta\nalpha\nbeta\n"));

        tmpVector.clear();
        tmpVector.add("");
        tmpVector.add("alpha\r\n");
        tmpVector.add("beta\r\n");
        tmpVector.add("\r\n");
        assertLinesToCharsResultEquals("diffLinesToChars: Empty string and blank lines.", new DiffMatchPatch.LinesToCharsResult("", "\u0001\u0002\u0003\u0003", tmpVector), diffMatchPatch.diffLinesToChars("", "alpha\r\nbeta\r\n\r\n\r\n"));

        tmpVector.clear();
        tmpVector.add("");
        tmpVector.add("a");
        tmpVector.add("b");
        assertLinesToCharsResultEquals("diffLinesToChars: No linebreaks.", new DiffMatchPatch.LinesToCharsResult("\u0001", "\u0002", tmpVector), diffMatchPatch.diffLinesToChars("a", "b"));

        // More than 256 to reveal any 8-bit limitations.
        int n = 300;
        tmpVector.clear();
        StringBuilder lineList = new StringBuilder();
        StringBuilder charList = new StringBuilder();
        for (int x = 1; x < n + 1; x++) {
            tmpVector.add(x + "\n");
            lineList.append(x + "\n");
            charList.append((char) x);
        }
        assertEquals(n, tmpVector.size());
        String lines = lineList.toString();
        String chars = charList.toString();
        assertEquals(n, chars.length());
        tmpVector.add(0, "");
        assertLinesToCharsResultEquals("diffLinesToChars: More than 256.", new DiffMatchPatch.LinesToCharsResult(chars, "", tmpVector), diffMatchPatch.diffLinesToChars(lines, ""));
    }

    @Test
    public void testDiffCharsToLines() {
        // First check that Diff equality works.
        assertTrue("diffCharsToLines: Equality #1.", new DiffMatchPatch.Diff(EQUAL, "a").equals(new DiffMatchPatch.Diff(EQUAL, "a")));

        assertEquals("diffCharsToLines: Equality #2.", new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(EQUAL, "a"));

        // Convert chars up to lines.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "\u0001\u0002\u0001"), new DiffMatchPatch.Diff(INSERT, "\u0002\u0001\u0002"));
        List<String> tmpVector = new ArrayList<String>();
        tmpVector.add("");
        tmpVector.add("alpha\n");
        tmpVector.add("beta\n");
        diffMatchPatch.diffCharsToLines(diffs, tmpVector);
        assertEquals("diffCharsToLines: Shared lines.", diffList(new DiffMatchPatch.Diff(EQUAL, "alpha\nbeta\nalpha\n"), new DiffMatchPatch.Diff(INSERT, "beta\nalpha\nbeta\n")), diffs);

        // More than 256 to reveal any 8-bit limitations.
        int n = 300;
        tmpVector.clear();
        StringBuilder lineList = new StringBuilder();
        StringBuilder charList = new StringBuilder();
        for (int x = 1; x < n + 1; x++) {
            tmpVector.add(x + "\n");
            lineList.append(x + "\n");
            charList.append((char) x);
        }
        assertEquals(n, tmpVector.size());
        String lines = lineList.toString();
        String chars = charList.toString();
        assertEquals(n, chars.length());
        tmpVector.add(0, "");
        diffs = diffList(new DiffMatchPatch.Diff(DELETE, chars));
        diffMatchPatch.diffCharsToLines(diffs, tmpVector);
        assertEquals("diffCharsToLines: More than 256.", diffList(new DiffMatchPatch.Diff(DELETE, lines)), diffs);
    }

    @Test
    public void testDiffCleanupMerge() {
        // Cleanup a messy diff.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList();
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Null case.", diffList(), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "b"), new DiffMatchPatch.Diff(INSERT, "c"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: No change case.", diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "b"), new DiffMatchPatch.Diff(INSERT, "c")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(EQUAL, "b"), new DiffMatchPatch.Diff(EQUAL, "c"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Merge equalities.", diffList(new DiffMatchPatch.Diff(EQUAL, "abc")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(DELETE, "b"), new DiffMatchPatch.Diff(DELETE, "c"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Merge deletions.", diffList(new DiffMatchPatch.Diff(DELETE, "abc")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(INSERT, "a"), new DiffMatchPatch.Diff(INSERT, "b"), new DiffMatchPatch.Diff(INSERT, "c"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Merge insertions.", diffList(new DiffMatchPatch.Diff(INSERT, "abc")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(INSERT, "b"), new DiffMatchPatch.Diff(DELETE, "c"), new DiffMatchPatch.Diff(INSERT, "d"), new DiffMatchPatch.Diff(EQUAL, "e"), new DiffMatchPatch.Diff(EQUAL, "f"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Merge interweave.", diffList(new DiffMatchPatch.Diff(DELETE, "ac"), new DiffMatchPatch.Diff(INSERT, "bd"), new DiffMatchPatch.Diff(EQUAL, "ef")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(INSERT, "abc"), new DiffMatchPatch.Diff(DELETE, "dc"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Prefix and suffix detection.", diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "d"), new DiffMatchPatch.Diff(INSERT, "b"), new DiffMatchPatch.Diff(EQUAL, "c")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "x"), new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(INSERT, "abc"), new DiffMatchPatch.Diff(DELETE, "dc"), new DiffMatchPatch.Diff(EQUAL, "y"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Prefix and suffix detection with equalities.", diffList(new DiffMatchPatch.Diff(EQUAL, "xa"), new DiffMatchPatch.Diff(DELETE, "d"), new DiffMatchPatch.Diff(INSERT, "b"), new DiffMatchPatch.Diff(EQUAL, "cy")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(INSERT, "ba"), new DiffMatchPatch.Diff(EQUAL, "c"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Slide edit left.", diffList(new DiffMatchPatch.Diff(INSERT, "ab"), new DiffMatchPatch.Diff(EQUAL, "ac")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "c"), new DiffMatchPatch.Diff(INSERT, "ab"), new DiffMatchPatch.Diff(EQUAL, "a"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Slide edit right.", diffList(new DiffMatchPatch.Diff(EQUAL, "ca"), new DiffMatchPatch.Diff(INSERT, "ba")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "b"), new DiffMatchPatch.Diff(EQUAL, "c"), new DiffMatchPatch.Diff(DELETE, "ac"), new DiffMatchPatch.Diff(EQUAL, "x"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Slide edit left recursive.", diffList(new DiffMatchPatch.Diff(DELETE, "abc"), new DiffMatchPatch.Diff(EQUAL, "acx")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "x"), new DiffMatchPatch.Diff(DELETE, "ca"), new DiffMatchPatch.Diff(EQUAL, "c"), new DiffMatchPatch.Diff(DELETE, "b"), new DiffMatchPatch.Diff(EQUAL, "a"));
        diffMatchPatch.diffCleanupMerge(diffs);
        assertEquals("diffCleanupMerge: Slide edit right recursive.", diffList(new DiffMatchPatch.Diff(EQUAL, "xca"), new DiffMatchPatch.Diff(DELETE, "cba")), diffs);
    }

    @Test
    public void testDiffCleanupSemanticLossless() {
        // Slide diffs to match logical boundaries.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList();
        diffMatchPatch.diffCleanupSemanticLossLess(diffs);
        assertEquals("diffCleanupSemanticLossLess: Null case.", diffList(), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "AAA\r\n\r\nBBB"), new DiffMatchPatch.Diff(INSERT, "\r\nDDD\r\n\r\nBBB"), new DiffMatchPatch.Diff(EQUAL, "\r\nEEE"));
        diffMatchPatch.diffCleanupSemanticLossLess(diffs);
        assertEquals("diffCleanupSemanticLossLess: Blank lines.", diffList(new DiffMatchPatch.Diff(EQUAL, "AAA\r\n\r\n"), new DiffMatchPatch.Diff(INSERT, "BBB\r\nDDD\r\n\r\n"), new DiffMatchPatch.Diff(EQUAL, "BBB\r\nEEE")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "AAA\r\nBBB"), new DiffMatchPatch.Diff(INSERT, " DDD\r\nBBB"), new DiffMatchPatch.Diff(EQUAL, " EEE"));
        diffMatchPatch.diffCleanupSemanticLossLess(diffs);
        assertEquals("diffCleanupSemanticLossLess: Line boundaries.", diffList(new DiffMatchPatch.Diff(EQUAL, "AAA\r\n"), new DiffMatchPatch.Diff(INSERT, "BBB DDD\r\n"), new DiffMatchPatch.Diff(EQUAL, "BBB EEE")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "The c"), new DiffMatchPatch.Diff(INSERT, "ow and the c"), new DiffMatchPatch.Diff(EQUAL, "at."));
        diffMatchPatch.diffCleanupSemanticLossLess(diffs);
        assertEquals("diffCleanupSemanticLossLess: Word boundaries.", diffList(new DiffMatchPatch.Diff(EQUAL, "The "), new DiffMatchPatch.Diff(INSERT, "cow and the "), new DiffMatchPatch.Diff(EQUAL, "cat.")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "The-c"), new DiffMatchPatch.Diff(INSERT, "ow-and-the-c"), new DiffMatchPatch.Diff(EQUAL, "at."));
        diffMatchPatch.diffCleanupSemanticLossLess(diffs);
        assertEquals("diffCleanupSemanticLossLess: Alphanumeric boundaries.", diffList(new DiffMatchPatch.Diff(EQUAL, "The-"), new DiffMatchPatch.Diff(INSERT, "cow-and-the-"), new DiffMatchPatch.Diff(EQUAL, "cat.")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(EQUAL, "ax"));
        diffMatchPatch.diffCleanupSemanticLossLess(diffs);
        assertEquals("diffCleanupSemanticLossLess: Hitting the start.", diffList(new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(EQUAL, "aax")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "xa"), new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(EQUAL, "a"));
        diffMatchPatch.diffCleanupSemanticLossLess(diffs);
        assertEquals("diffCleanupSemanticLossLess: Hitting the end.", diffList(new DiffMatchPatch.Diff(EQUAL, "xaa"), new DiffMatchPatch.Diff(DELETE, "a")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "The xxx. The "), new DiffMatchPatch.Diff(INSERT, "zzz. The "), new DiffMatchPatch.Diff(EQUAL, "yyy."));
        diffMatchPatch.diffCleanupSemanticLossLess(diffs);
        assertEquals("diffCleanupSemanticLossLess: Sentence boundaries.", diffList(new DiffMatchPatch.Diff(EQUAL, "The xxx."), new DiffMatchPatch.Diff(INSERT, " The zzz."), new DiffMatchPatch.Diff(EQUAL, " The yyy.")), diffs);
    }

    @Test
    public void testDiffCleanupSemantic() {
        // Cleanup semantically trivial equalities.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList();
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: Null case.", diffList(), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "ab"), new DiffMatchPatch.Diff(INSERT, "cd"), new DiffMatchPatch.Diff(EQUAL, "12"), new DiffMatchPatch.Diff(DELETE, "e"));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: No elimination #1.", diffList(new DiffMatchPatch.Diff(DELETE, "ab"), new DiffMatchPatch.Diff(INSERT, "cd"), new DiffMatchPatch.Diff(EQUAL, "12"), new DiffMatchPatch.Diff(DELETE, "e")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "abc"), new DiffMatchPatch.Diff(INSERT, "ABC"), new DiffMatchPatch.Diff(EQUAL, "1234"), new DiffMatchPatch.Diff(DELETE, "wxyz"));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: No elimination #2.", diffList(new DiffMatchPatch.Diff(DELETE, "abc"), new DiffMatchPatch.Diff(INSERT, "ABC"), new DiffMatchPatch.Diff(EQUAL, "1234"), new DiffMatchPatch.Diff(DELETE, "wxyz")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(EQUAL, "b"), new DiffMatchPatch.Diff(DELETE, "c"));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: Simple elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "abc"), new DiffMatchPatch.Diff(INSERT, "b")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "ab"), new DiffMatchPatch.Diff(EQUAL, "cd"), new DiffMatchPatch.Diff(DELETE, "e"), new DiffMatchPatch.Diff(EQUAL, "f"), new DiffMatchPatch.Diff(INSERT, "g"));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: Backpass elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "abcdef"), new DiffMatchPatch.Diff(INSERT, "cdfg")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(INSERT, "1"), new DiffMatchPatch.Diff(EQUAL, "A"), new DiffMatchPatch.Diff(DELETE, "B"), new DiffMatchPatch.Diff(INSERT, "2"), new DiffMatchPatch.Diff(EQUAL, "_"), new DiffMatchPatch.Diff(INSERT, "1"), new DiffMatchPatch.Diff(EQUAL, "A"), new DiffMatchPatch.Diff(DELETE, "B"), new DiffMatchPatch.Diff(INSERT, "2"));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: Multiple elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "AB_AB"), new DiffMatchPatch.Diff(INSERT, "1A2_1A2")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "The c"), new DiffMatchPatch.Diff(DELETE, "ow and the c"), new DiffMatchPatch.Diff(EQUAL, "at."));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: Word boundaries.", diffList(new DiffMatchPatch.Diff(EQUAL, "The "), new DiffMatchPatch.Diff(DELETE, "cow and the "), new DiffMatchPatch.Diff(EQUAL, "cat.")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "abcxx"), new DiffMatchPatch.Diff(INSERT, "xxdef"));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: No overlap elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "abcxx"), new DiffMatchPatch.Diff(INSERT, "xxdef")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "abcxxx"), new DiffMatchPatch.Diff(INSERT, "xxxdef"));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: Overlap elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "abc"), new DiffMatchPatch.Diff(EQUAL, "xxx"), new DiffMatchPatch.Diff(INSERT, "def")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "xxxabc"), new DiffMatchPatch.Diff(INSERT, "defxxx"));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: Reverse overlap elimination.", diffList(new DiffMatchPatch.Diff(INSERT, "def"), new DiffMatchPatch.Diff(EQUAL, "xxx"), new DiffMatchPatch.Diff(DELETE, "abc")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "abcd1212"), new DiffMatchPatch.Diff(INSERT, "1212efghi"), new DiffMatchPatch.Diff(EQUAL, "----"), new DiffMatchPatch.Diff(DELETE, "A3"), new DiffMatchPatch.Diff(INSERT, "3BC"));
        diffMatchPatch.diff_cleanupSemantic(diffs);
        assertEquals("diff_cleanupSemantic: Two overlap eliminations.", diffList(new DiffMatchPatch.Diff(DELETE, "abcd"), new DiffMatchPatch.Diff(EQUAL, "1212"), new DiffMatchPatch.Diff(INSERT, "efghi"), new DiffMatchPatch.Diff(EQUAL, "----"), new DiffMatchPatch.Diff(DELETE, "A"), new DiffMatchPatch.Diff(EQUAL, "3"), new DiffMatchPatch.Diff(INSERT, "BC")), diffs);
    }

    @Test
    public void testDiffCleanupEfficiency() {
        // Cleanup operationally trivial equalities.
        diffMatchPatch = DiffMatchPatch.builder().diffEditCost(4).build();
        LinkedList<DiffMatchPatch.Diff> diffs = diffList();
        diffMatchPatch.diffCleanupEfficiency(diffs);
        assertEquals("diffCleanupEfficiency: Null case.", diffList(), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "ab"), new DiffMatchPatch.Diff(INSERT, "12"), new DiffMatchPatch.Diff(EQUAL, "wxyz"), new DiffMatchPatch.Diff(DELETE, "cd"), new DiffMatchPatch.Diff(INSERT, "34"));
        diffMatchPatch.diffCleanupEfficiency(diffs);
        assertEquals("diffCleanupEfficiency: No elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "ab"), new DiffMatchPatch.Diff(INSERT, "12"), new DiffMatchPatch.Diff(EQUAL, "wxyz"), new DiffMatchPatch.Diff(DELETE, "cd"), new DiffMatchPatch.Diff(INSERT, "34")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "ab"), new DiffMatchPatch.Diff(INSERT, "12"), new DiffMatchPatch.Diff(EQUAL, "xyz"), new DiffMatchPatch.Diff(DELETE, "cd"), new DiffMatchPatch.Diff(INSERT, "34"));
        diffMatchPatch.diffCleanupEfficiency(diffs);
        assertEquals("diffCleanupEfficiency: Four-edit elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "abxyzcd"), new DiffMatchPatch.Diff(INSERT, "12xyz34")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(INSERT, "12"), new DiffMatchPatch.Diff(EQUAL, "x"), new DiffMatchPatch.Diff(DELETE, "cd"), new DiffMatchPatch.Diff(INSERT, "34"));
        diffMatchPatch.diffCleanupEfficiency(diffs);
        assertEquals("diffCleanupEfficiency: Three-edit elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "xcd"), new DiffMatchPatch.Diff(INSERT, "12x34")), diffs);

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "ab"), new DiffMatchPatch.Diff(INSERT, "12"), new DiffMatchPatch.Diff(EQUAL, "xy"), new DiffMatchPatch.Diff(INSERT, "34"), new DiffMatchPatch.Diff(EQUAL, "z"), new DiffMatchPatch.Diff(DELETE, "cd"), new DiffMatchPatch.Diff(INSERT, "56"));
        diffMatchPatch.diffCleanupEfficiency(diffs);
        assertEquals("diffCleanupEfficiency: Backpass elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "abxyzcd"), new DiffMatchPatch.Diff(INSERT, "12xy34z56")), diffs);

        diffMatchPatch = DiffMatchPatch.builder().diffEditCost(5).build();
        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "ab"), new DiffMatchPatch.Diff(INSERT, "12"), new DiffMatchPatch.Diff(EQUAL, "wxyz"), new DiffMatchPatch.Diff(DELETE, "cd"), new DiffMatchPatch.Diff(INSERT, "34"));
        diffMatchPatch.diffCleanupEfficiency(diffs);
        assertEquals("diffCleanupEfficiency: High cost elimination.", diffList(new DiffMatchPatch.Diff(DELETE, "abwxyzcd"), new DiffMatchPatch.Diff(INSERT, "12wxyz34")), diffs);
    }

    @Test
    public void testDiffPrettyHtml() {
        // Pretty print.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a\n"), new DiffMatchPatch.Diff(DELETE, "<B>b</B>"), new DiffMatchPatch.Diff(INSERT, "c&d"));
        assertEquals("diffPretyyHtml:", "<span>a&para;<br></span><del style=\"background:#ffe6e6;\">&lt;B&gt;b&lt;/B&gt;</del><ins style=\"background:#e6ffe6;\">c&amp;d</ins>", diffMatchPatch.diffPretyyHtml(diffs));
    }

    @Test
    public void testDiffText() {
        // Compute the source and destination texts.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "jump"), new DiffMatchPatch.Diff(DELETE, "s"), new DiffMatchPatch.Diff(INSERT, "ed"), new DiffMatchPatch.Diff(EQUAL, " over "), new DiffMatchPatch.Diff(DELETE, "the"), new DiffMatchPatch.Diff(INSERT, "a"), new DiffMatchPatch.Diff(EQUAL, " lazy"));
        assertEquals("diffText1:", "jumps over the lazy", diffMatchPatch.diffText1(diffs));
        assertEquals("diffText2:", "jumped over a lazy", diffMatchPatch.diffText2(diffs));
    }

    @Test
    public void testDiffDelta() {
        // Convert a diff into delta string.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "jump"), new DiffMatchPatch.Diff(DELETE, "s"), new DiffMatchPatch.Diff(INSERT, "ed"), new DiffMatchPatch.Diff(EQUAL, " over "), new DiffMatchPatch.Diff(DELETE, "the"), new DiffMatchPatch.Diff(INSERT, "a"), new DiffMatchPatch.Diff(EQUAL, " lazy"), new DiffMatchPatch.Diff(INSERT, "old dog"));
        String text1 = diffMatchPatch.diffText1(diffs);
        assertEquals("diffText1: Base text.", "jumps over the lazy", text1);

        String delta = diffMatchPatch.diffToDelta(diffs);
        assertEquals("diffToDelta:", "=4\t-1\t+ed\t=6\t-3\t+a\t=5\t+old dog", delta);

        // Convert delta string into a diff.
        assertEquals("diffFromDelta: Normal.", diffs, diffMatchPatch.diffFromDelta(text1, delta));

        // Generates error (19 < 20).
        try {
            diffMatchPatch.diffFromDelta(text1 + 'x', delta);
            fail("diffFromDelta: Too long.");
        } catch (IllegalArgumentException ex) {
            // Exception expected.
        }

        // Generates error (19 > 18).
        try {
            diffMatchPatch.diffFromDelta(text1.substring(1), delta);
            fail("diffFromDelta: Too short.");
        } catch (IllegalArgumentException ex) {
            // Exception expected.
        }

        // Generates error (%c3%xy invalid Unicode).
        try {
            diffMatchPatch.diffFromDelta("", "+%c3%xy");
            fail("diffFromDelta: Invalid character.");
        } catch (IllegalArgumentException ex) {
            // Exception expected.
        }

        // Test deltas with special characters.
        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "\u0680 \000 \t %"), new DiffMatchPatch.Diff(DELETE, "\u0681 \001 \n ^"), new DiffMatchPatch.Diff(INSERT, "\u0682 \002 \\ |"));
        text1 = diffMatchPatch.diffText1(diffs);
        assertEquals("diffText1: Unicode text.", "\u0680 \000 \t %\u0681 \001 \n ^", text1);

        delta = diffMatchPatch.diffToDelta(diffs);
        assertEquals("diffToDelta: Unicode.", "=7\t-7\t+%DA%82 %02 %5C %7C", delta);

        assertEquals("diffFromDelta: Unicode.", diffs, diffMatchPatch.diffFromDelta(text1, delta));

        // Verify pool of unchanged characters.
        diffs = diffList(new DiffMatchPatch.Diff(INSERT, "A-Z a-z 0-9 - _ . ! ~ * ' ( ) ; / ? : @ & = + $ , # "));
        String text2 = diffMatchPatch.diffText2(diffs);
        assertEquals("diffText2: Unchanged characters.", "A-Z a-z 0-9 - _ . ! ~ * \' ( ) ; / ? : @ & = + $ , # ", text2);

        delta = diffMatchPatch.diffToDelta(diffs);
        assertEquals("diffToDelta: Unchanged characters.", "+A-Z a-z 0-9 - _ . ! ~ * \' ( ) ; / ? : @ & = + $ , # ", delta);

        // Convert delta string into a diff.
        assertEquals("diffFromDelta: Unchanged characters.", diffs, diffMatchPatch.diffFromDelta("", delta));
    }

    @Test
    public void testDiffXIndex() {
        // Translate a location in text1 to text2.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList(new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(INSERT, "1234"), new DiffMatchPatch.Diff(EQUAL, "xyz"));
        assertEquals("diffXIndex: Translation on equality.", 5, diffMatchPatch.diffXIndex(diffs, 2));

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "1234"), new DiffMatchPatch.Diff(EQUAL, "xyz"));
        assertEquals("diffXIndex: Translation on deletion.", 1, diffMatchPatch.diffXIndex(diffs, 3));
    }

    @Test
    public void testDiffLevenshtein() {
        LinkedList<DiffMatchPatch.Diff> diffs = diffList(new DiffMatchPatch.Diff(DELETE, "abc"), new DiffMatchPatch.Diff(INSERT, "1234"), new DiffMatchPatch.Diff(EQUAL, "xyz"));
        assertEquals("Levenshtein with trailing equality.", 4, diffMatchPatch.diffLevenshtein(diffs));

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "xyz"), new DiffMatchPatch.Diff(DELETE, "abc"), new DiffMatchPatch.Diff(INSERT, "1234"));
        assertEquals("Levenshtein with leading equality.", 4, diffMatchPatch.diffLevenshtein(diffs));

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "abc"), new DiffMatchPatch.Diff(EQUAL, "xyz"), new DiffMatchPatch.Diff(INSERT, "1234"));
        assertEquals("Levenshtein with middle equality.", 7, diffMatchPatch.diffLevenshtein(diffs));
    }

    @Test
    public void testDiffBisect() {
        // Normal.
        String a = "cat";
        String b = "map";
        // Since the resulting diff hasn't been normalized, it would be ok if
        // the insertion and deletion pairs are swapped.
        // If the order changes, tweak this test as required.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList(new DiffMatchPatch.Diff(DELETE, "c"), new DiffMatchPatch.Diff(INSERT, "m"), new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "t"), new DiffMatchPatch.Diff(INSERT, "p"));
        assertEquals("diffBisect: Normal.", diffs, diffMatchPatch.diffBisect(a, b, Long.MAX_VALUE));

        // Timeout.
        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "cat"), new DiffMatchPatch.Diff(INSERT, "map"));
        assertEquals("diffBisect: Timeout.", diffs, diffMatchPatch.diffBisect(a, b, 0));
    }

    @Test
    public void testDiffMain() {
        // Perform a trivial diff.
        LinkedList<DiffMatchPatch.Diff> diffs = diffList();
        assertEquals("diffMain: Null case.", diffs, diffMatchPatch.diffMain("", "", false));

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "abc"));
        assertEquals("diffMain: Equality.", diffs, diffMatchPatch.diffMain("abc", "abc", false));

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "ab"), new DiffMatchPatch.Diff(INSERT, "123"), new DiffMatchPatch.Diff(EQUAL, "c"));
        assertEquals("diffMain: Simple insertion.", diffs, diffMatchPatch.diffMain("abc", "ab123c", false));

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "123"), new DiffMatchPatch.Diff(EQUAL, "bc"));
        assertEquals("diffMain: Simple deletion.", diffs, diffMatchPatch.diffMain("a123bc", "abc", false));

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(INSERT, "123"), new DiffMatchPatch.Diff(EQUAL, "b"), new DiffMatchPatch.Diff(INSERT, "456"), new DiffMatchPatch.Diff(EQUAL, "c"));
        assertEquals("diffMain: Two insertions.", diffs, diffMatchPatch.diffMain("abc", "a123b456c", false));

        diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "123"), new DiffMatchPatch.Diff(EQUAL, "b"), new DiffMatchPatch.Diff(DELETE, "456"), new DiffMatchPatch.Diff(EQUAL, "c"));
        assertEquals("diffMain: Two deletions.", diffs, diffMatchPatch.diffMain("a123b456c", "abc", false));

        // Perform a real diff.
        // Switch off the patchTimeout.
        diffMatchPatch = DiffMatchPatch.builder().patchTimeout(0).build();
        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(INSERT, "b"));
        assertEquals("diffMain: Simple case #1.", diffs, diffMatchPatch.diffMain("a", "b", false));

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "Apple"), new DiffMatchPatch.Diff(INSERT, "Banana"), new DiffMatchPatch.Diff(EQUAL, "s are a"), new DiffMatchPatch.Diff(INSERT, "lso"), new DiffMatchPatch.Diff(EQUAL, " fruit."));
        assertEquals("diffMain: Simple case #2.", diffs, diffMatchPatch.diffMain("Apples are a fruit.", "Bananas are also fruit.", false));

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "a"), new DiffMatchPatch.Diff(INSERT, "\u0680"), new DiffMatchPatch.Diff(EQUAL, "x"), new DiffMatchPatch.Diff(DELETE, "\t"), new DiffMatchPatch.Diff(INSERT, "\000"));
        assertEquals("diffMain: Simple case #3.", diffs, diffMatchPatch.diffMain("ax\t", "\u0680x\000", false));

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "1"), new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "y"), new DiffMatchPatch.Diff(EQUAL, "b"), new DiffMatchPatch.Diff(DELETE, "2"), new DiffMatchPatch.Diff(INSERT, "xab"));
        assertEquals("diffMain: Overlap #1.", diffs, diffMatchPatch.diffMain("1ayb2", "abxab", false));

        diffs = diffList(new DiffMatchPatch.Diff(INSERT, "xaxcx"), new DiffMatchPatch.Diff(EQUAL, "abc"), new DiffMatchPatch.Diff(DELETE, "y"));
        assertEquals("diffMain: Overlap #2.", diffs, diffMatchPatch.diffMain("abcy", "xaxcxabc", false));

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "ABCD"), new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(DELETE, "="), new DiffMatchPatch.Diff(INSERT, "-"), new DiffMatchPatch.Diff(EQUAL, "bcd"), new DiffMatchPatch.Diff(DELETE, "="), new DiffMatchPatch.Diff(INSERT, "-"), new DiffMatchPatch.Diff(EQUAL, "efghijklmnopqrs"), new DiffMatchPatch.Diff(DELETE, "EFGHIJKLMNOefg"));
        assertEquals("diffMain: Overlap #3.", diffs, diffMatchPatch.diffMain("ABCDa=bcd=efghijklmnopqrsEFGHIJKLMNOefg", "a-bcd-efghijklmnopqrs", false));

        diffs = diffList(new DiffMatchPatch.Diff(INSERT, " "), new DiffMatchPatch.Diff(EQUAL, "a"), new DiffMatchPatch.Diff(INSERT, "nd"), new DiffMatchPatch.Diff(EQUAL, " [[Pennsylvania]]"), new DiffMatchPatch.Diff(DELETE, " and [[New"));
        assertEquals("diffMain: Large equality.", diffs, diffMatchPatch.diffMain("a [[Pennsylvania]] and [[New", " and [[Pennsylvania]]", false));

        diffMatchPatch = DiffMatchPatch.builder().patchTimeout(0.1f).build();
        String a = "`Twas brillig, and the slithy toves\nDid gyre and gimble in the wabe:\nAll mimsy were the borogoves,\nAnd the mome raths outgrabe.\n";
        String b = "I am the very model of a modern major general,\nI've information vegetable, animal, and mineral,\nI know the kings of England, and I quote the fights historical,\nFrom Marathon to Waterloo, in order categorical.\n";
        // Increase the text lengths by 1024 times to ensure a patchTimeout.
        for (int x = 0; x < 10; x++) {
            a = a + a;
            b = b + b;
        }
        long startTime = System.currentTimeMillis();
        diffMatchPatch.diffMain(a, b);
        long endTime = System.currentTimeMillis();
        // Test that we took at least the patchTimeout period.
        assertTrue("diffMain: Timeout min.", diffMatchPatch.diffTimeout() * 1000 <= endTime - startTime);
        // Test that we didn't take forever (be forgiving).
        // Theoretically this test could fail very occasionally if the
        // OS task swaps or locks up for a second at the wrong moment.
        assertTrue("diffMain: Timeout max.", diffMatchPatch.diffTimeout() * 1000 * 2 > endTime - startTime);
        diffMatchPatch = DiffMatchPatch.builder().patchTimeout(0).build();

        // Test the linemode speedup.
        // Must be long to pass the 100 char cutoff.
        a = "1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n";
        b = "abcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\n";
        assertEquals("diffMain: Simple line-mode.", diffMatchPatch.diffMain(a, b, true), diffMatchPatch.diffMain(a, b, false));

        a = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";
        b = "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghij";
        assertEquals("diffMain: Single line-mode.", diffMatchPatch.diffMain(a, b, true), diffMatchPatch.diffMain(a, b, false));

        a = "1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n";
        b = "abcdefghij\n1234567890\n1234567890\n1234567890\nabcdefghij\n1234567890\n1234567890\n1234567890\nabcdefghij\n1234567890\n1234567890\n1234567890\nabcdefghij\n";
        String[] texts_linemode = diff_rebuildtexts(diffMatchPatch.diffMain(a, b, true));
        String[] texts_textmode = diff_rebuildtexts(diffMatchPatch.diffMain(a, b, false));
        assertArrayEquals("diffMain: Overlap line-mode.", texts_textmode, texts_linemode);

        // Test null inputs.
        try {
            diffMatchPatch.diffMain(null, null);
            fail("diffMain: Null inputs.");
        } catch (IllegalArgumentException ex) {
            // Error expected.
        }
    }


    //  MATCH TEST FUNCTIONS


    @Test
    public void testMatchAlphabet() {
        // Initialise the bitmasks for Bitap.
        Map<Character, Integer> bitmask;
        bitmask = new HashMap<Character, Integer>();
        bitmask.put('a', 4); bitmask.put('b', 2); bitmask.put('c', 1);
        assertEquals("matchAlpabet: Unique.", bitmask, diffMatchPatch.matchAlpabet("abc"));

        bitmask = new HashMap<Character, Integer>();
        bitmask.put('a', 37); bitmask.put('b', 18); bitmask.put('c', 8);
        assertEquals("matchAlpabet: Duplicates.", bitmask, diffMatchPatch.matchAlpabet("abcaba"));
    }

    @Test
    public void testMatchBitap() {
        // Bitap algorithm.
        diffMatchPatch = DiffMatchPatch.builder().matchDistance(100).matchThreshold(0.5f).build();
        assertEquals("matchBitmap: Exact match #1.", 5, diffMatchPatch.matchBitmap("abcdefghijk", "fgh", 5));

        assertEquals("matchBitmap: Exact match #2.", 5, diffMatchPatch.matchBitmap("abcdefghijk", "fgh", 0));

        assertEquals("matchBitmap: Fuzzy match #1.", 4, diffMatchPatch.matchBitmap("abcdefghijk", "efxhi", 0));

        assertEquals("matchBitmap: Fuzzy match #2.", 2, diffMatchPatch.matchBitmap("abcdefghijk", "cdefxyhijk", 5));

        assertEquals("matchBitmap: Fuzzy match #3.", -1, diffMatchPatch.matchBitmap("abcdefghijk", "bxy", 1));

        assertEquals("matchBitmap: Overflow.", 2, diffMatchPatch.matchBitmap("123456789xx0", "3456789x0", 2));

        assertEquals("matchBitmap: Before start match.", 0, diffMatchPatch.matchBitmap("abcdef", "xxabc", 4));

        assertEquals("matchBitmap: Beyond end match.", 3, diffMatchPatch.matchBitmap("abcdef", "defyy", 4));

        assertEquals("matchBitmap: Oversized pattern.", 0, diffMatchPatch.matchBitmap("abcdef", "xabcdefy", 0));

        diffMatchPatch = DiffMatchPatch.builder().matchDistance(100).matchThreshold(0.4f).build();
        assertEquals("matchBitmap: Threshold #1.", 4, diffMatchPatch.matchBitmap("abcdefghijk", "efxyhi", 1));

        diffMatchPatch = DiffMatchPatch.builder().matchDistance(100).matchThreshold(0.3f).build();
        assertEquals("matchBitmap: Threshold #2.", -1, diffMatchPatch.matchBitmap("abcdefghijk", "efxyhi", 1));

        diffMatchPatch = DiffMatchPatch.builder().matchDistance(100).matchThreshold(0.0f).build();
        assertEquals("matchBitmap: Threshold #3.", 1, diffMatchPatch.matchBitmap("abcdefghijk", "bcdef", 1));

        diffMatchPatch = DiffMatchPatch.builder().matchDistance(100).matchThreshold(0.5f).build();
        assertEquals("matchBitmap: Multiple select #1.", 0, diffMatchPatch.matchBitmap("abcdexyzabcde", "abccde", 3));

        assertEquals("matchBitmap: Multiple select #2.", 8, diffMatchPatch.matchBitmap("abcdexyzabcde", "abccde", 5));

        diffMatchPatch = DiffMatchPatch.builder().matchDistance(10).matchThreshold(0.5f).build();
        assertEquals("matchBitmap: Distance test #1.", -1, diffMatchPatch.matchBitmap("abcdefghijklmnopqrstuvwxyz", "abcdefg", 24));

        assertEquals("matchBitmap: Distance test #2.", 0, diffMatchPatch.matchBitmap("abcdefghijklmnopqrstuvwxyz", "abcdxxefg", 1));

        diffMatchPatch = DiffMatchPatch.builder().matchDistance(1000).matchThreshold(0.5f).build();
        assertEquals("matchBitmap: Distance test #3.", 0, diffMatchPatch.matchBitmap("abcdefghijklmnopqrstuvwxyz", "abcdefg", 24));
    }

    @Test
    public void testMatchMain() {
        // Full match.
        assertEquals("matchMain: Equality.", 0, diffMatchPatch.matchMain("abcdef", "abcdef", 1000));

        assertEquals("matchMain: Null text.", -1, diffMatchPatch.matchMain("", "abcdef", 1));

        assertEquals("matchMain: Null pattern.", 3, diffMatchPatch.matchMain("abcdef", "", 3));

        assertEquals("matchMain: Exact match.", 3, diffMatchPatch.matchMain("abcdef", "de", 3));

        assertEquals("matchMain: Beyond end match.", 3, diffMatchPatch.matchMain("abcdef", "defy", 4));

        assertEquals("matchMain: Oversized pattern.", 0, diffMatchPatch.matchMain("abcdef", "abcdefy", 0));

        diffMatchPatch = DiffMatchPatch.builder().matchThreshold(0.7f).build();
        //diffMatchPatch.Match_Threshold = 0.7f;
        assertEquals("matchMain: Complex match.", 4, diffMatchPatch.matchMain("I am the very model of a modern major general.", " that berry ", 5));
        diffMatchPatch = DiffMatchPatch.builder().matchThreshold(0.5f).build();
        //diffMatchPatch.Match_Threshold = 0.5f;

        // Test null inputs.
        try {
            diffMatchPatch.matchMain(null, null, 0);
            fail("matchMain: Null inputs.");
        } catch (IllegalArgumentException ex) {
            // Error expected.
        }
    }


    //  PATCH TEST FUNCTIONS


    @Test
    public void testPatchObj() {
        // Patch Object.
        DiffMatchPatch.Patch p = new DiffMatchPatch.Patch();
        p.start1 = 20;
        p.start2 = 21;
        p.length1 = 18;
        p.length2 = 17;
        p.diffs = diffList(new DiffMatchPatch.Diff(EQUAL, "jump"), new DiffMatchPatch.Diff(DELETE, "s"), new DiffMatchPatch.Diff(INSERT, "ed"), new DiffMatchPatch.Diff(EQUAL, " over "), new DiffMatchPatch.Diff(DELETE, "the"), new DiffMatchPatch.Diff(INSERT, "a"), new DiffMatchPatch.Diff(EQUAL, "\nlaz"));
        String strp = "@@ -21,18 +22,17 @@\n jump\n-s\n+ed\n  over \n-the\n+a\n %0Alaz\n";
        assertEquals("Patch: toString.", strp, p.toString());
    }

    @Test
    public void testPatchFromText() {
        assertTrue("patchFromText: #0.", diffMatchPatch.patchFromText("").isEmpty());

        String strp = "@@ -21,18 +22,17 @@\n jump\n-s\n+ed\n  over \n-the\n+a\n %0Alaz\n";
        assertEquals("patchFromText: #1.", strp, diffMatchPatch.patchFromText(strp).get(0).toString());

        assertEquals("patchFromText: #2.", "@@ -1 +1 @@\n-a\n+b\n", diffMatchPatch.patchFromText("@@ -1 +1 @@\n-a\n+b\n").get(0).toString());

        assertEquals("patchFromText: #3.", "@@ -1,3 +0,0 @@\n-abc\n", diffMatchPatch.patchFromText("@@ -1,3 +0,0 @@\n-abc\n").get(0).toString());

        assertEquals("patchFromText: #4.", "@@ -0,0 +1,3 @@\n+abc\n", diffMatchPatch.patchFromText("@@ -0,0 +1,3 @@\n+abc\n").get(0).toString());

        // Generates error.
        try {
            diffMatchPatch.patchFromText("Bad\nPatch\n");
            fail("patchFromText: #5.");
        } catch (IllegalArgumentException ex) {
            // Exception expected.
        }
    }

    @Test
    public void testPatchToText() {
        String strp = "@@ -21,18 +22,17 @@\n jump\n-s\n+ed\n  over \n-the\n+a\n  laz\n";
        List<DiffMatchPatch.Patch> patches;
        patches = diffMatchPatch.patchFromText(strp);
        assertEquals("patchToText: Single.", strp, diffMatchPatch.patchToText(patches));

        strp = "@@ -1,9 +1,9 @@\n-f\n+F\n oo+fooba\n@@ -7,9 +7,9 @@\n obar\n-,\n+.\n  tes\n";
        patches = diffMatchPatch.patchFromText(strp);
        assertEquals("patchToText: Dual.", strp, diffMatchPatch.patchToText(patches));
    }

    @Test
    public void testPatchAddContext() {
        diffMatchPatch = DiffMatchPatch.builder().patchMargin(4).build();
        //diffMatchPatch.Patch_Margin = 4;
        DiffMatchPatch.Patch p;
        p = diffMatchPatch.patchFromText("@@ -21,4 +21,10 @@\n-jump\n+somersault\n").get(0);
        diffMatchPatch.patchAddContext(p, "The quick brown fox jumps over the lazy dog.");
        assertEquals("patchAddContext: Simple case.", "@@ -17,12 +17,18 @@\n fox \n-jump\n+somersault\n s ov\n", p.toString());

        p = diffMatchPatch.patchFromText("@@ -21,4 +21,10 @@\n-jump\n+somersault\n").get(0);
        diffMatchPatch.patchAddContext(p, "The quick brown fox jumps.");
        assertEquals("patchAddContext: Not enough trailing context.", "@@ -17,10 +17,16 @@\n fox \n-jump\n+somersault\n s.\n", p.toString());

        p = diffMatchPatch.patchFromText("@@ -3 +3,2 @@\n-e\n+at\n").get(0);
        diffMatchPatch.patchAddContext(p, "The quick brown fox jumps.");
        assertEquals("patchAddContext: Not enough leading context.", "@@ -1,7 +1,8 @@\n Th\n-e\n+at\n  qui\n", p.toString());

        p = diffMatchPatch.patchFromText("@@ -3 +3,2 @@\n-e\n+at\n").get(0);
        diffMatchPatch.patchAddContext(p, "The quick brown fox jumps.  The quick brown fox crashes.");
        assertEquals("patchAddContext: Ambiguity.", "@@ -1,27 +1,28 @@\n Th\n-e\n+at\n  quick brown fox jumps. \n", p.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testPatchMake() {
        LinkedList<DiffMatchPatch.Patch> patches;
        patches = diffMatchPatch.patchMake("", "");
        assertEquals("patchMake: Null case.", "", diffMatchPatch.patchToText(patches));

        String text1 = "The quick brown fox jumps over the lazy dog.";
        String text2 = "That quick brown fox jumped over a lazy dog.";
        String expectedPatch = "@@ -1,8 +1,7 @@\n Th\n-at\n+e\n  qui\n@@ -21,17 +21,18 @@\n jump\n-ed\n+s\n  over \n-a\n+the\n  laz\n";
        // The second patch must be "-21,17 +21,18", not "-22,17 +21,18" due to rolling context.
        patches = diffMatchPatch.patchMake(text2, text1);
        assertEquals("patchMake: Text2+Text1 inputs.", expectedPatch, diffMatchPatch.patchToText(patches));

        expectedPatch = "@@ -1,11 +1,12 @@\n Th\n-e\n+at\n  quick b\n@@ -22,18 +22,17 @@\n jump\n-s\n+ed\n  over \n-the\n+a\n  laz\n";
        patches = diffMatchPatch.patchMake(text1, text2);
        assertEquals("patchMake: Text1+Text2 inputs.", expectedPatch, diffMatchPatch.patchToText(patches));

        LinkedList<DiffMatchPatch.Diff> diffs = diffMatchPatch.diffMain(text1, text2, false);
        patches = diffMatchPatch.patchMake(diffs);
        assertEquals("patchMake: Diff input.", expectedPatch, diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake(text1, diffs);
        assertEquals("patchMake: Text1+Diff inputs.", expectedPatch, diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake(text1, text2, diffs);
        assertEquals("patchMake: Text1+Text2+Diff inputs (deprecated).", expectedPatch, diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake("`1234567890-=[]\\;',./", "~!@#$%^&*()_+{}|:\"<>?");
        assertEquals("patchToText: Character encoding.", "@@ -1,21 +1,21 @@\n-%601234567890-=%5B%5D%5C;',./\n+~!@#$%25%5E&*()_+%7B%7D%7C:%22%3C%3E?\n", diffMatchPatch.patchToText(patches));

        diffs = diffList(new DiffMatchPatch.Diff(DELETE, "`1234567890-=[]\\;',./"), new DiffMatchPatch.Diff(INSERT, "~!@#$%^&*()_+{}|:\"<>?"));
        assertEquals("patchFromText: Character decoding.", diffs, diffMatchPatch.patchFromText("@@ -1,21 +1,21 @@\n-%601234567890-=%5B%5D%5C;',./\n+~!@#$%25%5E&*()_+%7B%7D%7C:%22%3C%3E?\n").get(0).diffs);

        text1 = "";
        for (int x = 0; x < 100; x++) {
            text1 += "abcdef";
        }
        text2 = text1 + "123";
        expectedPatch = "@@ -573,28 +573,31 @@\n cdefabcdefabcdefabcdefabcdef\n+123\n";
        patches = diffMatchPatch.patchMake(text1, text2);
        assertEquals("patchMake: Long string with repeats.", expectedPatch, diffMatchPatch.patchToText(patches));

        // Test null inputs.
        try {
            diffMatchPatch.patchMake(null);
            fail("patchMake: Null inputs.");
        } catch (IllegalArgumentException ex) {
            // Error expected.
        }
    }

    @Test
    public void testPatchSplitMax() {
        // Assumes that Match_MaxBits is 32.
        LinkedList<DiffMatchPatch.Patch> patches;
        patches = diffMatchPatch.patchMake("abcdefghijklmnopqrstuvwxyz01234567890", "XabXcdXefXghXijXklXmnXopXqrXstXuvXwxXyzX01X23X45X67X89X0");
        diffMatchPatch.patchSplitMax(patches);
        assertEquals("patchSplitMax: #1.", "@@ -1,32 +1,46 @@\n+X\n ab\n+X\n cd\n+X\n ef\n+X\n gh\n+X\n ij\n+X\n kl\n+X\n mn\n+X\n op\n+X\n qr\n+X\n st\n+X\n uv\n+X\n wx\n+X\n yz\n+X\n 012345\n@@ -25,13 +39,18 @@\n zX01\n+X\n 23\n+X\n 45\n+X\n 67\n+X\n 89\n+X\n 0\n", diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake("abcdef1234567890123456789012345678901234567890123456789012345678901234567890uvwxyz", "abcdefuvwxyz");
        String oldToText = diffMatchPatch.patchToText(patches);
        diffMatchPatch.patchSplitMax(patches);
        assertEquals("patchSplitMax: #2.", oldToText, diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake("1234567890123456789012345678901234567890123456789012345678901234567890", "abc");
        diffMatchPatch.patchSplitMax(patches);
        assertEquals("patchSplitMax: #3.", "@@ -1,32 +1,4 @@\n-1234567890123456789012345678\n 9012\n@@ -29,32 +1,4 @@\n-9012345678901234567890123456\n 7890\n@@ -57,14 +1,3 @@\n-78901234567890\n+abc\n", diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake("abcdefghij , h : 0 , t : 1 abcdefghij , h : 0 , t : 1 abcdefghij , h : 0 , t : 1", "abcdefghij , h : 1 , t : 1 abcdefghij , h : 1 , t : 1 abcdefghij , h : 0 , t : 1");
        diffMatchPatch.patchSplitMax(patches);
        assertEquals("patchSplitMax: #4.", "@@ -2,32 +2,32 @@\n bcdefghij , h : \n-0\n+1\n  , t : 1 abcdef\n@@ -29,32 +29,32 @@\n bcdefghij , h : \n-0\n+1\n  , t : 1 abcdef\n", diffMatchPatch.patchToText(patches));
    }

    @Test
    public void testPatchAddPadding() {
        LinkedList<DiffMatchPatch.Patch> patches;
        patches = diffMatchPatch.patchMake("", "test");
        assertEquals("patchAddPadding: Both edges full.", "@@ -0,0 +1,4 @@\n+test\n", diffMatchPatch.patchToText(patches));
        diffMatchPatch.patchAddPadding(patches);
        assertEquals("patchAddPadding: Both edges full.", "@@ -1,8 +1,12 @@\n %01%02%03%04\n+test\n %01%02%03%04\n", diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake("XY", "XtestY");
        assertEquals("patchAddPadding: Both edges partial.", "@@ -1,2 +1,6 @@\n X\n+test\n Y\n", diffMatchPatch.patchToText(patches));
        diffMatchPatch.patchAddPadding(patches);
        assertEquals("patchAddPadding: Both edges partial.", "@@ -2,8 +2,12 @@\n %02%03%04X\n+test\n Y%01%02%03\n", diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake("XXXXYYYY", "XXXXtestYYYY");
        assertEquals("patchAddPadding: Both edges none.", "@@ -1,8 +1,12 @@\n XXXX\n+test\n YYYY\n", diffMatchPatch.patchToText(patches));
        diffMatchPatch.patchAddPadding(patches);
        assertEquals("patchAddPadding: Both edges none.", "@@ -5,8 +5,12 @@\n XXXX\n+test\n YYYY\n", diffMatchPatch.patchToText(patches));
    }

    @Test
    public void testPatchApply() {
        diffMatchPatch = DiffMatchPatch.builder().matchDistance(1000).matchThreshold(0.5f).patchDeleteThreshold(0.5f).build();
        //diffMatchPatch.Match_Distance = 1000;
        //diffMatchPatch.Match_Threshold = 0.5f;
        //diffMatchPatch.Patch_DeleteThreshold = 0.5f;
        LinkedList<DiffMatchPatch.Patch> patches;
        patches = diffMatchPatch.patchMake("", "");
        Object[] results = diffMatchPatch.patchApply(patches, "Hello world.");
        boolean[] boolArray = (boolean[]) results[1];
        String resultStr = results[0] + "\t" + boolArray.length;
        assertEquals("patchApply: Null case.", "Hello world.\t0", resultStr);

        patches = diffMatchPatch.patchMake("The quick brown fox jumps over the lazy dog.", "That quick brown fox jumped over a lazy dog.");
        results = diffMatchPatch.patchApply(patches, "The quick brown fox jumps over the lazy dog.");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0] + '\t' + boolArray[1];
        assertEquals("patchApply: Exact match.", "That quick brown fox jumped over a lazy dog.\ttrue\ttrue", resultStr);

        results = diffMatchPatch.patchApply(patches, "The quick red rabbit jumps over the tired tiger.");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0] + '\t' + boolArray[1];
        assertEquals("patchApply: Partial match.", "That quick red rabbit jumped over a tired tiger.\ttrue\ttrue", resultStr);

        results = diffMatchPatch.patchApply(patches, "I am the very model of a modern major general.");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0] + '\t' + boolArray[1];
        assertEquals("patchApply: Failed match.", "I am the very model of a modern major general.\tfalse\tfalse", resultStr);

        patches = diffMatchPatch.patchMake("x1234567890123456789012345678901234567890123456789012345678901234567890y", "xabcy");
        results = diffMatchPatch.patchApply(patches, "x123456789012345678901234567890-----++++++++++-----123456789012345678901234567890y");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0] + '\t' + boolArray[1];
        assertEquals("patchApply: Big delete, small change.", "xabcy\ttrue\ttrue", resultStr);

        patches = diffMatchPatch.patchMake("x1234567890123456789012345678901234567890123456789012345678901234567890y", "xabcy");
        results = diffMatchPatch.patchApply(patches, "x12345678901234567890---------------++++++++++---------------12345678901234567890y");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0] + '\t' + boolArray[1];
        assertEquals("patchApply: Big delete, big change 1.", "xabc12345678901234567890---------------++++++++++---------------12345678901234567890y\tfalse\ttrue", resultStr);

        diffMatchPatch = DiffMatchPatch.builder().matchDistance(1000).matchThreshold(0.5f).patchDeleteThreshold(0.6f).build();
        //diffMatchPatch.Patch_DeleteThreshold = 0.6f;
        patches = diffMatchPatch.patchMake("x1234567890123456789012345678901234567890123456789012345678901234567890y", "xabcy");
        results = diffMatchPatch.patchApply(patches, "x12345678901234567890---------------++++++++++---------------12345678901234567890y");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0] + '\t' + boolArray[1];
        assertEquals("patchApply: Big delete, big change 2.", "xabcy\ttrue\ttrue", resultStr);

        diffMatchPatch = DiffMatchPatch.builder().matchDistance(0).matchThreshold(0.0f).patchDeleteThreshold(0.5f).build();
        //diffMatchPatch.Patch_DeleteThreshold = 0.5f;
        // Compensate for failed patch.
        //diffMatchPatch.Match_Threshold = 0.0f;
        //diffMatchPatch.Match_Distance = 0;
        patches = diffMatchPatch.patchMake("abcdefghijklmnopqrstuvwxyz--------------------1234567890", "abcXXXXXXXXXXdefghijklmnopqrstuvwxyz--------------------1234567YYYYYYYYYY890");
        results = diffMatchPatch.patchApply(patches, "ABCDEFGHIJKLMNOPQRSTUVWXYZ--------------------1234567890");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0] + '\t' + boolArray[1];
        assertEquals("patchApply: Compensate for failed patch.", "ABCDEFGHIJKLMNOPQRSTUVWXYZ--------------------1234567YYYYYYYYYY890\tfalse\ttrue", resultStr);
        diffMatchPatch = DiffMatchPatch.builder().matchDistance(1000).matchThreshold(0.5f).patchDeleteThreshold(0.5f).build();
        //diffMatchPatch.Match_Threshold = 0.5f;
        //diffMatchPatch.Match_Distance = 1000;

        patches = diffMatchPatch.patchMake("", "test");
        String patchStr = diffMatchPatch.patchToText(patches);
        diffMatchPatch.patchApply(patches, "");
        assertEquals("patchApply: No side effects.", patchStr, diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake("The quick brown fox jumps over the lazy dog.", "Woof");
        patchStr = diffMatchPatch.patchToText(patches);
        diffMatchPatch.patchApply(patches, "The quick brown fox jumps over the lazy dog.");
        assertEquals("patchApply: No side effects with major delete.", patchStr, diffMatchPatch.patchToText(patches));

        patches = diffMatchPatch.patchMake("", "test");
        results = diffMatchPatch.patchApply(patches, "");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0];
        assertEquals("patchApply: Edge exact match.", "test\ttrue", resultStr);

        patches = diffMatchPatch.patchMake("XY", "XtestY");
        results = diffMatchPatch.patchApply(patches, "XY");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0];
        assertEquals("patchApply: Near edge exact match.", "XtestY\ttrue", resultStr);

        patches = diffMatchPatch.patchMake("y", "y123");
        results = diffMatchPatch.patchApply(patches, "x");
        boolArray = (boolean[]) results[1];
        resultStr = results[0] + "\t" + boolArray[0];
        assertEquals("patchApply: Edge partial match.", "x123\ttrue", resultStr);
    }

    private static void assertArrayEquals(String error_msg, Object[] a, Object[] b) {
        List<Object> list_a = Arrays.asList(a);
        List<Object> list_b = Arrays.asList(b);
        assertEquals(error_msg, list_a, list_b);
    }

    private static void assertLinesToCharsResultEquals(String error_msg,
                                                DiffMatchPatch.LinesToCharsResult a, DiffMatchPatch.LinesToCharsResult b) {
        assertEquals(error_msg, a.chars1, b.chars1);
        assertEquals(error_msg, a.chars2, b.chars2);
        assertEquals(error_msg, a.lineArray, b.lineArray);
    }

    // Construct the two texts which made up the diff originally.
    private static String[] diff_rebuildtexts(LinkedList<DiffMatchPatch.Diff> diffs) {
        String[] text = {"", ""};
        for (DiffMatchPatch.Diff myDiff : diffs) {
            if (myDiff.operation != DiffMatchPatch.Operation.INSERT) {
                text[0] += myDiff.text;
            }
            if (myDiff.operation != DiffMatchPatch.Operation.DELETE) {
                text[1] += myDiff.text;
            }
        }
        return text;
    }

    private static LinkedList<DiffMatchPatch.Diff> diffList(DiffMatchPatch.Diff... diffs) {
        return new LinkedList<DiffMatchPatch.Diff>(Arrays.asList(diffs));
    }
}

