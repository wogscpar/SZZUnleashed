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

package parser;

import diff.DiffingLines.DiffLines;
import java.util.*;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Commit abstraction. Contains a reference to a RevCommit, a Map containing the diffing lines with
 * its parent commit and a map with each change type for each changed file.
 *
 * @author Oscar Svensson
 */
public class Commit {
  public RevCommit commit;
  public Map<String, DiffLines> diffWithParent;
  public Map<String, DiffEntry.ChangeType> changeTypes;
  public Map<String, Collection<RevCommit>> fileAnnotations;

  /**
   * Constructor for a commit.
   *
   * @param commit the reference to a commit.
   */
  public Commit(RevCommit commit) {
    this.commit = commit;

    diffWithParent = new HashMap<>();
    changeTypes = new HashMap<>();
  }
  /**
   * Return the hash representation of the commit.
   *
   * @return a hash representation as a string.
   */
  public String getHashString() {
    return ObjectId.toString(commit.toObjectId());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    if (diffWithParent != null) {
      for (Map.Entry<String, DiffLines> entry : diffWithParent.entrySet()) {
        String path = entry.getKey();
        sb.append(path);
        sb.append("  " + changeTypes.get(path));
        sb.append("\n");

        /*
         * TODO: ADD CustomFormatter.DiffLines toString()
         */

        sb.append(entry.getValue().getJSON());
        sb.append("\n");
      }
    }
    if (fileAnnotations != null) {
      sb.append("Annotation graph\n");
      for (Map.Entry<String, Collection<RevCommit>> entry : fileAnnotations.entrySet()) {
        String path = entry.getKey();

        sb.append(path);

        sb.append("\n");
        sb.append("[");
        for (RevCommit c : entry.getValue()) {
          sb.append(c + "->");
        }
        sb.append("]\n");
      }
    }
    return commit.toString() + "\n\n" + sb.toString();
  }

  /**
   * Helper method to convert a Commit object to a JSON object.
   *
   * @return a JSONObject containing the commit. Omits the RevCommit.
   */
  public JSONObject toJson() {
    JSONObject tree = new JSONObject();

    JSONObject diffing = new JSONObject();
    for (Map.Entry<String, DiffLines> diff : diffWithParent.entrySet()) {
      String file = diff.getKey();

      JSONArray lines = new JSONArray();
      DiffLines line = diff.getValue();

      lines.add(line.getJSON());

      diffing.put(file, lines);
    }
    tree.put("diff", diffing);

    JSONObject changes = new JSONObject();
    for (Map.Entry<String, DiffEntry.ChangeType> changeType : changeTypes.entrySet()) {
      changes.put(changeType.getKey(), changeType.getValue().toString());
    }

    tree.put("changes", changes);

    return tree;
  }
}
