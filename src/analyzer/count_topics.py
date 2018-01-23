import pandas as pd
import numpy as np
import re
from sklearn import preprocessing
# get_ipython().magic('matplotlib inline')


def get_dfs(datafile='/Users/wataru/Desktop/b.txt',
            cells_file='/Users/wataru/IdeaProjects/genePAM/data/cells.txt',
            catergoires=['EGFP', 'Double', 'Tdtomato']):
    with open(datafile) as f:
        arrays = [np.fromstring(x, dtype=np.int8, sep=',')
                  for x in f.readlines()]

    topic_and_counts = [np.unique(ary, return_counts=True) for ary in arrays]
    counts = np.array([[c for _, c in sorted(zip(ts, cs), key=lambda x: x[0])]
                       for ts, cs in topic_and_counts])
    dists = preprocessing.normalize(counts, axis=1, norm='l1')
    df = pd.DataFrame(dists)

    with open(cells_file) as f:
        cells = f.readlines()[0].rstrip().split(',')

    # (cells) is invalid: set_index()'s argument should be a list of list
    df = df.set_index([cells])   
    patterns = [re.compile(r) for r in catergoires]
    _dfs = [df.loc[[bool(pat.search(i)) for i in df.index.tolist()]]
            for pat in patterns]
    categorized_dfs = {name: _df for name, _df
                       in zip(catergoires, _dfs)}
    return categorized_dfs
