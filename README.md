# genePAM: a clustering tools of cells

## About
This is a package for clustering cells, using Pachinko Allocation Model[1].

## Requirements
**[We have a docker image](https://github.com/tarohi24/genePAM-docker)!** We recommend using this docker image.
*We will distribute a docker image soon.*

To install manually, the followings are needed.

* JRE 1.9
* Python above 3

In addition to install them, you have to create a config file.
```
cp config_example.yaml config.yaml
```

## Run
After you modify the config file, you have only to execute `python run.py`. Results will be outputted to the directory you specify in `config.yaml`.

## References
[1] Li, W., & McCallum, A. (2006, June). Pachinko allocation: DAG-structured mixture models of topic correlations. In Proceedings of the 23rd international conference on Machine learning (pp. 577-584). ACM.  
[2] McCallum, Andrew Kachites.  "MALLET: A Machine Learning for Language Toolkit." 2002. [Website](http://mallet.cs.umass.edu) 
