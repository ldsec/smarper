We provide a dummy data set in this repository to test the Machine Learning framework.
dummy_data.mat is a cell array with the data from each user in each cell, in the format of Ndecisions x Nfeatures.
dummy_static_policies.mat is a cell array with the dummy static policies (-1, 0, or 1) per user (cell), per app (line), and per data type (column).
In this example, we assume 41 users, 10 apps, and 3 data types (contacts, location, and storage).

If you are interested in reproducing our results, please e-mail smarper@epfl.ch to obtain the data set with runtime permission decisions.