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

package graph;

import java.util.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A class that represents a Annotation graph.
 *
 * <p>For a source revision and given a filepath, it stores all revisions that have made a change on
 * that file before the source revision.
 *
 * @author Oscar Svensson
 */
public class FileAnnotationGraph {
  public String filePath;

  public LinkedList<String> revisions;
  public Map<String, Map<Integer, Integer>> mappings;
  public Map<String, FileAnnotationGraph> sub_graphs;

  /**
   * Get line mapping for a specific revison to its successor. Returns an empty map if the given
   * revision is the first aka the one that was used to create this grap.
   *
   * @param revision the revision which should be mapped to its successor.
   * @return a map containing all line numbers that corresponds to the lines in the successor.
   */
  public Map<Integer, Integer> getLineMapping(String revision) {
    if (revision == revisions.getFirst()) {
      System.err.println("Revision must have a successor! Return empty map..");
      return new LinkedHashMap<>();
    }
    return this.mappings.get(revision);
  }

  /**
   * Return the revisions that have made changes to this file.
   *
   * @return a LinkedList containing the revisions in order.
   */
  public LinkedList<String> getRevisions() {
    return this.revisions;
  }

  /**
   * Get the associated file path. This symbolises the file that all revisions in this graph have in
   * common.
   *
   * @return a String containing the file path to the annotated file.
   */
  public String getFilePath() {
    return this.filePath;
  }

  public JSONObject getGraphJSON() {
    JSONObject tree = new JSONObject();

    tree.put("filePath", this.filePath);

    JSONArray revisionArray = new JSONArray();
    for (String rev : this.revisions) {
      revisionArray.add(rev);
    }

    tree.put("revisions", revisionArray);

    JSONObject jsonLineMappings = new JSONObject();
    Map<Integer, Integer> lineMappings = null;

    JSONObject lineMappingsObject = null;
    for (String rev : this.mappings.keySet()) {
      lineMappings = this.mappings.get(rev);
      lineMappingsObject = new JSONObject();

      for (Map.Entry<Integer, Integer> lineMapping : lineMappings.entrySet()) {
        lineMappingsObject.put(lineMapping.getKey(), lineMapping.getValue());
      }
      jsonLineMappings.put(rev, lineMappingsObject);
    }

    tree.put("mappings", jsonLineMappings);

    JSONObject subGraphs = new JSONObject();
    for (Map.Entry<String, FileAnnotationGraph> entry : this.sub_graphs.entrySet()) {
      subGraphs.put(entry.getKey(), entry.getValue().getGraphJSON());
    }

    tree.put("subgraphs", subGraphs);

    return tree;
  }
}
