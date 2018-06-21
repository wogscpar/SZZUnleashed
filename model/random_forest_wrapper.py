""" A wrapper to enable RandomForestClassifier to be used in conjunction
with imblearn sampling methods when using cross_validate or cross_val_score
methods from scikit-learn """
__author__ = "Kristian Berg"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

from sklearn.ensemble import RandomForestClassifier

class RandomForestWrapper(RandomForestClassifier):
    """ A wrapper to enable RandomForestClassifier to be used in conjunction
    with imblearn sampling methods when using cross_validate or cross_val_score
    methods from scikit-learn """
    # pylint: disable = too-many-ancestors

    def __init__(self,
                 sampler=None,
                 n_estimators=10,
                 criterion="gini",
                 max_depth=None,
                 min_samples_split=2,
                 min_samples_leaf=1,
                 min_weight_fraction_leaf=0.,
                 max_features="auto",
                 max_leaf_nodes=None,
                 min_impurity_decrease=0.,
                 min_impurity_split=None,
                 bootstrap=True,
                 oob_score=False,
                 n_jobs=1,
                 random_state=None,
                 verbose=0,
                 warm_start=False,
                 class_weight=None):
        # pylint: disable = too-many-arguments, too-many-locals
        super().__init__(n_estimators,
                         criterion,
                         max_depth,
                         min_samples_split,
                         min_samples_leaf,
                         min_weight_fraction_leaf,
                         max_features,
                         max_leaf_nodes,
                         min_impurity_decrease,
                         min_impurity_split,
                         bootstrap,
                         oob_score,
                         n_jobs,
                         random_state,
                         verbose,
                         warm_start,
                         class_weight)
        self.sampler = sampler

    def fit(self, X, y, sample_weight=None):
        if self.sampler:
            X, y = self.sampler.fit_sample(X, y)
        return super().fit(X, y, sample_weight=None)
