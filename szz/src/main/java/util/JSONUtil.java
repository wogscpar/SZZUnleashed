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

package util;

import java.io.*;
import java.util.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import parser.Commit;

/**
 * Util to perform operations on JSON objects.
 *
 * @author Oscar Svensson
 */
public class JSONUtil {

  /**
   * Method to save found commits to file.
   *
   * @param commits list of found commits.
   * @param path the path to which the commits will be saved to.(Not filepath but directory)
   */
  public static void saveFoundCommits(List<Commit> commits, String path) {
    JSONObject jCommits = new JSONObject();

    for (Commit commit : commits) {
      jCommits.put(commit.getHashString(), commit.toJson());
    }

    if (path != null) {
      try (FileWriter writer = new FileWriter(path + "/" + "commits.json")) {
        writer.write(jCommits.toJSONString());
        writer.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      System.out.println(jCommits);
    }
  }

  /**
   * Save a list of bugIntroducing commits.
   *
   * @param commits the list of pairs of commits. commits[0]=FIX, commits[1]=INTRODUCER
   */
  public static void saveBugIntroducingCommits(List<String[]> commits, String path) {
    JSONArray jCommits = new JSONArray();

    for (String[] pair : commits) {
      JSONArray jPair = new JSONArray();

      jPair.add(pair[0]);
      jPair.add(pair[1]);

      jCommits.add(jPair);
    }

    if (path != null) {
      try (FileWriter writer = new FileWriter(path + "/" + "fix_and_introducers_pairs.json")) {
        writer.write(jCommits.toJSONString());
        writer.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      System.out.println(jCommits);
    }
  }
}
