# Machine Learning framework
We provide a Machine Learning framework for carefully training and comparing different context-aware models that predict permission decisions.

# Structure of the code
- `data/`: data set of runtime permission decisions and data set of static policies per user.
- `gpml/`: implementation of Gaussian Processes ([GPML toolbox](http://www.gaussianprocess.org/gpml/code/matlab/doc/)).
- `prettyPlot/`: wrapper to plot figures.
- `run_exp.m/`: experiment to plot Mean Absolute Error (MAE) and Incorrect Classification Rate (ICR) vs Percentage of decisions used (Fig. 6(a) and 6(b) of the paper [1]). We consider the following models: ZeroR_t, Bayesian Linear Regression, Gaussian Process with Squared Exponential Kernel (GP-SE), decision tree (D. Tree), and support vector machines (SVM).
- `mae_for_users.m/`: experiment to show that "context reduces the # of mistakes", in which we plot the Error histograms for BLR, Static Policies, and ZeroR_t (Fig. 6(c) of the paper). To show that "context improve accuracy across users", we plot the MAE obtained by the baselines and SVM versus those obtained by our BLR model (Fig. 7 of the paper [1]). Each point corresponds to the MAE of a participant.
- `model_comparison.m/`: experiment to compare models with different input features (Fig. 8 of the paper [1]). 

Note: We provide a dummy data set in this repository to test the Machine Learning framework. If you are interested in reproducing our results, please e-mail smarper@epfl.ch to obtain the data set with runtime permission decisions and the data set with static policies.
# Getting started
To use the code of this repository, you need to install Matlab with the [Statistics and Machine Learning toolbox](https://www.mathworks.com/help/stats/?requestedDomain=www.mathworks.com&nocookie=true). 
