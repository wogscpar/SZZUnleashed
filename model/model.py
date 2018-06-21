""" A collection of scripts for training and evaluating a RandomForestClassifier
on a bug prediction dataset at commit level """
__author__ = "Kristian Berg"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import argparse
import configparser
from sklearn.model_selection import cross_validate
from sklearn.externals import joblib
from imblearn.over_sampling import SMOTE
from imblearn.under_sampling import ClusterCentroids
from imblearn.combine import SMOTETomek
from treeinterpreter import treeinterpreter as ti
import numpy as np
from random_forest_wrapper import RandomForestWrapper
from time_sensitive_split import GitTimeSensitiveSplit

def evaluate(path, datapath, lastcommit, config, debug):
    """ Evaluate model performance """

    data, labels, _, _ = load_data(datapath)
    args = config['args']

    if args['seed'] != 'None':
        np.random.seed(args.getint('seed'))

    sampler = get_sampler(args['sampler'])

    if args['split'] == 'kfold':
        split = int(args['nfolds'])
    elif args['split'] == 'occ':
        split = GitTimeSensitiveSplit(path=path, lastcommit=lastcommit, debug=debug)

    scoring = {'p': 'precision',
               'r': 'recall',
               'f1': 'f1',
              }

    data = data[::-1]
    labels = labels[::-1]
    wrap = RandomForestWrapper(sampler, n_estimators=args.getint('n_estimators'))
    scores = cross_validate(wrap, data, labels, scoring=scoring, cv=split, return_train_score=False)
    for key in sorted(scores.keys()):
        print(key + ': ' + str(scores[key]))
        print(key + ': ' + str(np.average(scores[key])) + ' ± ' +
              str(np.std(scores[key])))

def train(datapath, sampler_arg=None, printfeats=False):
    """ Train model and save in pkl file """
    data, labels, _, names = load_data(datapath)
    sampler = get_sampler(sampler_arg)
    clf = RandomForestWrapper(sampler, n_estimators=200)
    clf.fit(data, labels)

    if printfeats:
        feats = zip(names[1:], clf.feature_importances_)
        feats = sorted(feats, key=lambda yo: yo[1])
        for pair in feats:
            print(pair)

    joblib.dump(clf, 'model.pkl')

def classify(datapath, commithash=None, index=None):
    """ Load model and classify single data point. Also determines
    most significant feature """
    # pylint: disable = too-many-locals
    clf = joblib.load('model.pkl')
    data, _, hashes, names = load_data(datapath)

    if commithash:
        temp, = np.where(hashes == commithash)
        sample = temp[0]
    elif index:
        sample = index
    else:
        sample = 1

    prediction, _, contributions = ti.predict(clf, data[[sample]])
    label1 = np.array(contributions)[0, :, 0]
    label2 = np.array(contributions)[0, :, 1]

    if prediction[0][0] > prediction[0][1]:
        res = label1
        labeltext = 'clean'
    else:
        res = label2
        labeltext = 'buggy'

    top = max(res)
    index, = np.where(res == top)
    feature = names[index[0] + 1]

    print('Predicted result: ' + labeltext)
    print('Top factor: ' + feature)

def get_sampler(arg):
    """ Return sampler based on string argument """
    if arg == 'smote':
        # Oversampling
        return SMOTE()
    elif arg == 'cluster':
        # Undersampling
        return ClusterCentroids()
    elif arg == 'smotetomek':
        # Mixed over- and undersampling
        return SMOTETomek()
    return None

def load_data(datapath):
    """ Load data from label and feature .csv files """

    with open('data/features.csv') as feats:
        names = feats.readline().split(',')
        num_cols = len(names)

    data = np.genfromtxt(datapath + '/features.csv', delimiter=',', skip_header=1,
                         usecols=tuple(range(1, num_cols)))
    labels = np.genfromtxt(datapath + '/labels.csv', delimiter=',', dtype='int',
                           skip_header=1, usecols=(1))
    hashes = np.genfromtxt(datapath + '/features.csv', delimiter=',', dtype='str',
                           skip_header=1, usecols=0)

    return data, labels, hashes, names

def main():
    """ Main method """
    parser = argparse.ArgumentParser(description='Train or evaluate model for '
                                     + 'defect prediction')
    parser.add_argument('method', metavar='m', type=str,
                        help='method to be executed, either "train", ' +
                        '"classify" or "evaluate"')
    parser.add_argument('config', metavar='c', type=str,
                        help='specify .ini config file')
    parser.add_argument('datapath', metavar='d', type=str,
                        help='filepath of features.csv and label.csv files')
    parser.add_argument('--hash', type=str, default=None,
                        help='when method is "classify", specify data point' +
                        ' by hash')
    parser.add_argument('--index', type=int, default=None,
                        help='when method is "classify", specify data point' +
                        ' by index')
    parser.add_argument('--path', type=str, default=None,
                        help='when method is "evaluate", specify path to git' +
                        ' repository')
    parser.add_argument('--lastcommit', type=str, default=None,
                        help='when method is "evaluate", specify last commit' +
                        ' to include')
    parser.add_argument('--significance', type=bool, default=False,
                        help='when method is "train", if True prints feature ' +
                        'significances')
    parser.add_argument('--debug', type=bool, default=False,
                        help='enables debug print output')
    args = parser.parse_args()

    config = configparser.ConfigParser()
    config.read(args.config)

    if args.method == 'evaluate':
        evaluate(args.path, args.datapath, args.lastcommit, config, args.debug)
    elif args.method == 'train':
        train(args.datapath, args.significance)
    elif args.method == 'classify':
        classify(args.datapath, args.hash, args.index)

if __name__ == '__main__':
    main()
