""" Time sensitive split for Git repository data based on Tan et al.'s Online
Change Classification """
__author__ = "Kristian Berg"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import subprocess
from datetime import datetime, timedelta
from utils import datetime_of_commit

class GitTimeSensitiveSplit:
    """ Time sensitive split for Git repository data based on Tan et al.'s Online
    Change Classification """
    def __init__(self, path, sgap=timedelta(days=331), gap=timedelta(days=73),
                 egap=timedelta(days=781), update=timedelta(days=200),
                 traindur=timedelta(days=1700), testdur=timedelta(days=400),
                 lastcommit=None, debug=False):
        self.path = path
        self.gap = gap
        self.update = update
        self.testdur = testdur
        self.traindur = traindur
        self.debug = debug

        # Determine date of oldest commit in repository
        command = ['git', 'log', '--reverse', '--date=iso']
        self.startdate = datetime_of_commit(path, command=command)

        # Determine date of newest commit in repository
        if lastcommit:
            self.enddate = datetime_of_commit(path, lastcommit)
        else:
            command = ['git', 'log', '--date=iso']
            self.enddate = datetime_of_commit(path, command=command)

        # Add start and end gaps
        self.startdate += sgap
        self.enddate -= egap

        if self.debug:
            print('Start: ' + str(self.startdate))
            print('End: ' + str(self.enddate))
            print('Duration: ' + str(self.enddate - self.startdate))

        # Build list of commit dates from oldest to newest
        command = ['git', 'rev-list', '--pretty=%ai', '--reverse', 'HEAD']
        res = subprocess.run(command, cwd=path, stdout=subprocess.PIPE)
        gitrevlist = res.stdout.decode('utf-8').split('\n')
        self.dates = [datetime.strptime(x, '%Y-%m-%d %H:%M:%S %z') for x in gitrevlist[1::2]]

    def split(self, X, y=None, group=None):
        """ Split method used by scikit-learn's cross_validate and cross_val_score
        methods """

        # Initiate loop variables
        trainset = []
        testset = []
        train_index = 0
        test_index = 0
        tsplit = self.startdate + self.traindur

        # Adjust start index to correspond to start date
        while self.dates[train_index] < self.startdate:
            train_index += 1

        n_pos = 0
        while tsplit + self.gap + self.testdur < self.enddate:
            # Set test index to correspond to appropriate date
            test_index = train_index
            while self.dates[test_index] < tsplit + self.gap:
                test_index += 1

            # Build training set
            while self.dates[train_index] < tsplit:
                trainset.append(train_index)
                train_index += 1

            # Build test set
            testset = []
            while self.dates[test_index] < tsplit + self.gap + self.testdur:
                testset.append(test_index)
                test_index += 1
                if y[test_index] == 1:
                    n_pos += 1

            if self.debug:
                print(str(len(trainset)) + ' ' + str(len(testset)) + ' ' \
                      + str(n_pos) + ' ' + str(self.dates[test_index]))
            n_pos = 0

            # Loop update
            tsplit += self.update

            yield trainset, testset
