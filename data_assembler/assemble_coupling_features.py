"""
Script to extract coupling features from code maat analysis files.
"""
__author__ = "Oscar Svensson"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import csv
import os

from git import Repo
import numpy as np
from tqdm import tqdm

def save_features(features, res_path):
    """
    Save the coupling features to a csv file.
    """
    print("Saving to {}".format(os.path.abspath(res_path)))
    with open(os.path.abspath(res_path), 'w') as feat_file:
        feat_writer = csv.writer(feat_file)

        feat_writer.writerow([
            "commit", "number_of_cruical_files",
            "number_of_moderate_risk_cruical_files",
            "number_of_high_risk_cruical_files",
            "number_of_non_modified_change_couplings"
        ])
        for feature in features:
            feat_writer.writerow(feature)


def get_features():
    """
    Get the coupling features from a number of files.
    """
    commits = list(REPO.iter_commits('master'))

    couplings = {}
    features = []

    for hexsha in os.listdir("/h/oskars/data_all"):
        couplings[hexsha] = os.path.join(
            os.path.join("/h/oskars/data_all", hexsha),
            "{}_coupling.log.res".format(hexsha))

    features.append([commits[0].hexsha, 0, 0, 0])
    for i in tqdm(range(1, len(commits))):
        first = commits[i - 1]
        second = commits[i]

        diff = first.diff(second)

        paths = [d.b_path for d in diff]

        cruical_moderate = 0
        cruical_high = 0
        cruical_files = 0
        cruical_non_modified_couplings = 0

        if second.hexsha in couplings:
            cruical_commits = 0
            cruical_degrees = []

            with open(couplings[second.hexsha], 'r') as csvfile:
                coup_rows = csv.reader(csvfile)
                files = {}
                file_coupling_graph = {}

                next(coup_rows)
                for row in coup_rows:
                    degree = float(row[2])

                    # Is this correct?
                    in_files = bool(row[0] in files)
                    if in_files and files[row[0]] > degree:
                        files[row[0]] = degree
                    elif not in_files:
                        files[row[0]] = degree

                    is_in_coupling_graph = bool(row[0] in file_coupling_graph)
                    if is_in_coupling_graph and degree >= 75:
                        file_coupling_graph[row[0]].append(row[1])
                    elif degree >= 50:
                        file_coupling_graph[row[0]] = [row[1]]

                    # Is this correct?
                    in_files = bool(row[1] in files)
                    if in_files and files[row[1]] > degree:
                        files[row[1]] = degree
                    elif not in_files:
                        files[row[1]] = degree

                    is_in_coupling_graph = bool(row[1] in file_coupling_graph)
                    if is_in_coupling_graph and degree >= 75:
                        file_coupling_graph[row[1]].append(row[0])
                    elif degree >= 50:
                        file_coupling_graph[row[1]] = [row[0]]

                for path in paths:
                    if path in files:
                        cruical_commits = cruical_commits + 1
                        cruical_degrees.append(files[path])
                        cruical_files = cruical_files + 1

                # Check for all non modified cruical non coupled files.
                set_path = set(paths)
                for path in paths:
                    if path in file_coupling_graph:
                        file_couplings = set(file_coupling_graph[path])
                        cruical_non_modified_couplings = cruical_non_modified_couplings + len(
                            file_couplings - set_path)

                inds = np.digitize(cruical_degrees, [25, 50, 75, 100])
                cruical_moderate = sum([1 for i in inds if i == 3])
                cruical_high = sum([1 for i in inds if i == 4])

        features.append([
            second.hexsha,
            str(cruical_files),
            str(cruical_moderate),
            str(cruical_high),
            str(cruical_non_modified_couplings)
        ])

    return features


if __name__ == "__main__":
    global REPO
    REPO = Repo("../../jenkins")
    REPO = Repo("./repos/jenkins")

    FEATURES = get_features()
    save_features(FEATURES, './results/coupling_features.csv')
