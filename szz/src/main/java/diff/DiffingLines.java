/*
 * MIT License
 *
 * Copyright (c) 2018 Axis Communications AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package diff;

import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import util.Configuration;

/**
 * A diff class that gives the diffing lines between two revisions.
 *
 * @author Oscar Svensson
 */
public class DiffingLines {

  private int customContext;

  private boolean omitLineText;

  private Repository repo = null;

  public class DiffLines {
    public List<String[]> insertions = new LinkedList<>();
    public List<String[]> deletions = new LinkedList<>();

    /*
     * Get the lines as a JSON.
     */
    public JSONObject getJSON() {
      JSONObject diffLines = new JSONObject();

      JSONArray added = new JSONArray();
      JSONArray deleted = new JSONArray();

      for (String[] lineNNumber : insertions) {
        added.add(lineNNumber[0]);
        added.add(lineNNumber[1]);
      }
      for (String[] lineNNumber : deletions) {
        deleted.add(lineNNumber[0]);
        deleted.add(lineNNumber[1]);
      }

      diffLines.put("add", added);
      diffLines.put("delete", deleted);
      return diffLines;
    }

    public String toString() {
      return "";
    }
  }

  public DiffingLines(Repository repo, int customContext) {
    this.repo = repo;

    if (customContext < 0) {
      throw new IllegalStateException("Custom Context can't be lower than 0!!");
    }

    this.customContext = customContext;

    Configuration conf = Configuration.getInstance();
    this.omitLineText = conf.getOmitLineText();
  }

  /**
   * Extract the RawText object from a id.
   *
   * @param id the id on the object, which in this case is a commit.
   * @return either null or a RawText object.
   */
  private RawText toRaw(AbbreviatedObjectId id) {
    try {
      ObjectLoader loader = this.repo.open(id.toObjectId());
      return RawText.load(loader, PackConfig.DEFAULT_BIG_FILE_THRESHOLD);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Get the formatted diff between two commits.
   *
   * @param entry the DiffEntry object containing the real diff.
   * @param edits the edited chunks.
   * @return the Diffing lines with line numbers.
   */
  public DiffLines getDiffingLines(DiffEntry entry, EditList edits) throws IOException, GitAPIException {
    /*
     * Access the RawText objects for the old and the new entry.
     */
    RawText old = toRaw(entry.getOldId());
    RawText present = toRaw(entry.getNewId());

    /*
     * If the old file is null, it indicates that a new file has been made.
     */
    if (old == null || present == null) return new DiffLines();

    DiffLines lines = new DiffLines();

    int i = 0;
    /*
     * Loop through all edits.
     */
    while (i < edits.size()) {
        Edit first = edits.get(i);
        int last = last(edits, i);
        Edit second = edits.get(last);

        /*
         * Get the limits for the change in the old file.
         */
        int firstIndex = first.getBeginA() - customContext;
        int firstEnd = second.getEndA() + customContext;

        /*
         * Get the limits for the change in the new file.
         */
        int secondIndex = first.getBeginB() - customContext;
        int secondEnd = second.getEndB() + customContext;

        /*
         * Are they out of boundary?
         */
        firstIndex = 0 > firstIndex ? 0 : firstIndex;
        firstEnd = old.size() < firstEnd ? old.size() : firstEnd;

        secondIndex = 0 > secondIndex ? 0 : secondIndex;
        secondEnd = present.size() < secondEnd ? present.size() : secondEnd;

        /*
         * Loop through both revisions parallel.
         */
        while (firstIndex < firstEnd || secondIndex < secondEnd) {
            String[] info = null;

            if (firstIndex < first.getBeginA() || last + 1 < i) {
                if (this.omitLineText) {
                  info = new String[]{Integer.toString(firstIndex), Integer.toString(firstIndex)};
                } else {
                  info = new String[]{Integer.toString(firstIndex), old.getString(firstIndex)};
                }
                lines.insertions.add(info);
                
                firstIndex+=1;
                secondIndex+=1;
            } else if (firstIndex < first.getEndA()) {
                if (this.omitLineText) {
                  info = new String[]{Integer.toString(firstIndex), Integer.toString(firstIndex)};
                } else {
                  info = new String[]{Integer.toString(firstIndex), old.getString(firstIndex)};
                }
                lines.deletions.add(info);
                firstIndex+=1;
            } else if (secondIndex < first.getEndB()) {
                if (this.omitLineText) {
                  info = new String[]{Integer.toString(secondIndex), Integer.toString(secondIndex)};
                } else {
                  info = new String[]{Integer.toString(secondIndex), present.getString(secondIndex)};
                }
                lines.insertions.add(info);
                secondIndex+=1;
            }

            /*
             * Check if there is a gap between the next diff.
             */
            if (firstIndex >= first.getEndA() &&
                secondIndex >= first.getEndB() &&
                ++i < edits.size()){
                first = edits.get(i);
            }
        }
    }
    return lines;
  }

  /**
   * Get the last index that is common for two edits.
   */
  private int last(List<Edit> edits, int i) {
     Edit first = null;
     Edit second = null;
     int last = i + 1;

     while (true) {
        if (last >= edits.size()) return last - 1;

        first = edits.get(last);
        second = edits.get(last-1);

        if (first.getBeginA() - second.getEndA() > 2*customContext ||
            first.getBeginB() - second.getEndB() > 2*customContext)
            return last - 1;

        last++;
     }
  }
}
