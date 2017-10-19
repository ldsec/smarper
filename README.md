# SmarPer
SmarPer: Context-Aware and Automatic Runtime-Permissions for Mobile Devices

Smart Permissions ([SmarPer](https://spism.epfl.ch/smarper/)) is an advanced-permission mechanism for Android, with support for finer-grained permissions, context-awareness and multiple decision-levels. In addition, to help users manage permissions more efficiently and reduce permission fatigue, SmarPer provides (semi-) automatic decisions. In doing so, we will provide users with smarter controls for protecting their private information, with a lower overhead for users. We provide the source code for the SmarPer prototype in Android and the Machine Learning framework used in the SmarPer project.

# Structure of the code
This project is organized as follows:
- `SmarPerApp/`: SmarPer prototype in Android, based on [XPrivacy](https://github.com/M66B/XPrivacy).
- `ML/`: Machine Learning framework for carefully training and comparing different context-aware models that predict permission decisions. 
- `ML/data/`: data set of runtime permission decisions per user.
- `ML/gpml/`: implementation of Gaussian Processes ([GPML toolbox](http://www.gaussianprocess.org/gpml/code/matlab/doc/)).
- `ML/prettyPlot/`: wrapper to plot figures.
- `ML/run_exp.m/`: experiment to plot Mean Absolute Error (MAE) and Incorrect Classification Rate (ICR) vs Percentage of decisions used (Fig. 6(a) and 6(b) of the paper [1]). We consider the following models: ZeroR_t, Bayesian Linear Regression, Gaussian Process with Squared Exponential Kernel (GP-SE), decision tree (D. Tree), and support vector machines (SVM).
- `ML/mae_for_users.m/`: experiment to show that "context reduces the # of mistakes", in which we plot the Error histograms for BLR, Static Policies, and ZeroR_t (Fig. 6(c) of the paper). To show that "context improve accuracy across users", we plot the MAE obtained by the baselines and SVM versus those obtained by our BLR model (Fig. 7 of the paper [1]). Each point corresponds to the MAE of a participant.
- `ML/model_comparison.m/`: experiment to compare models with different input features (Fig. 8 of the paper [1]). 


# Getting started
To use the code of this repository, you need to install Matlab with the [Statistics and Machine Learning toolbox](https://www.mathworks.com/help/stats/?requestedDomain=www.mathworks.com&nocookie=true). 

Note: We provide a dummy data set in this repository to test the Machine Learning framework. If you are interested in reproducing our results, please e-mail smarper@epfl.ch to obtain the data set with runtime permission decisions.

# Documentation
For more details about the SmarPer project, refer to the [research paper](https://hal.archives-ouvertes.fr/hal-01489684) and the [SmarPer website](https://spism.epfl.ch/smarper/).

# Research
The research behind the SmarPer Project was published in the following paper:
- [1] Katarzyna Olejnik, Italo Dacosta, Joana Soares Machado, Kévin Huguenin, Mohammad Emtiyaz Khan, Jean-Pierre Hubaux. SmarPer: Context-Aware and Automatic Runtime-Permissions for Mobile Devices. In Proceedings of the 38th IEEE Symposium on Security and Privacy (S&P), San Jose, CA, United States, May 2017.

# License
SmarPer and [XPrivacy](https://github.com/M66B/XPrivacy) are released under the GPLv3 License.

The GPML toolbox code is released under the FreeBSD License.

# Contact
The SmarPer project is part of the [research effort](http://lca.epfl.ch/projects/privacy-mobile-pervasive/) of the LCA1 lab, EPFL. Feel free to contact us (smarper@epfl.ch).

Project members:
- Kasia Olejnik
- Italo Dacosta
- Joana Soares Machado
- Kevin Huguenin
- Mohammad Emtiyaz Khan
- Jean-Pierre Hubaux
