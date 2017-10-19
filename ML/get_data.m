function [X_learn, y_learn, XTr, yTr, XTe, yTe, maeStatic, icrStatic, yHat] = get_data(featureIdx, nValuesFeatures, appIdx, split_ratio, nTestPoints, start_time)
% get_data stored in the file data/data_without_start_end.mat
% Description of inputs:
% seed is a random seed,
% featureIdx contains some feature index
% nValuesFeatures contains the number of possible values that each feature
% can take, e.g. in_foreground can take 2 values (0 or 1)
% appIdx: the index of the popular apps
% split_ratio=0.7 will assign 70% of the users for learning and rest for testing
% nTestPoints = 20 will randomly leave-out 20 decisions from the test users. We can specify a start_time after which the decisions are left-out for testing from test users, e.g. start_time = 0.4 will only take decisions that occur after 40% of decisions.
% Description of outputs:
% (X_learn,y_learn) contains decisions for users in the learning set
% (XTr,yTr, XTe,yTe) are users chosen for testing with (XTe, yTe) containg the test decisions left-out, while XTr,yTr contains the rest.
% maeStatic and icrStatic contains MAE and ICR for static policies provided by the users.
% Written by Emtiyaz Khan and Joana Soares Machado

% Load the data with all the features,
% without first and last 5 decisions, cell with 1xNusers,
load('data/data.mat');

% indexes of the app and the data type for the dummy data
app_index = 1;
dataType_index = 2;

% build X, Y matrix from popular apps
D_pop_apps = get_pop_apps(data, appIdx);
[Xall, Yall] = build_X_Y(D_pop_apps);
X_not_dummy = Xall; % required for static policies to get the app and the data type
Xall = select_features(Xall, featureIdx);
Xall = dummify(Xall, featureIdx, nValuesFeatures);

% split users into two sets for learning (idxTr) and testing (idxTe)
nUsers=size(Yall,2);
idxLearn = datasample([1:nUsers], round(split_ratio*nUsers), 'Replace', false);
y_learn = Yall(:, idxLearn);
X_learn = Xall(:,:,idxLearn);
idxTe = setdiff([1:nUsers], idxLearn);
y_test = Yall(:, idxTe);
X_test = Xall(:,:,idxTe);

% punch holes in the test users
[XTr, XTe, yTr, yTe] = punch_holes(X_test, y_test, nTestPoints, start_time);

% evaluate the baseline with static policies
nUsers = size(yTe,2);
yHat = NaN*zeros(size(yTe));
load('data/static_policies.mat');
for n = 1:nUsers
    idx = find(~isnan(yTe(:,n)));
    uid = idxTe(n); % index of n'th user
    for j = 1:length(idx) % for each test point
        % predict the static decision for the given app (column 1 of X_not_dummy) and data type (column 2)
        yHat(idx(j),n) = static_policies{uid}(X_not_dummy(idx(j),app_index,uid), X_not_dummy(idx(j),dataType_index,uid));
    end
end

[maeStatic, icrStatic] = loss(yHat, yTe);

function [ D ] = get_pop_apps( data, appIdx )
% builds the matrix D with the data from popular apps
% select the popular apps (e.g., apps with > 200 decisions) to reduce data
% sparsity

nApps = length(appIdx);
nUsers = length(data);

for n = 1:nUsers
    user_temp = [];
    
    for d = 1:size(data{n},1) % for each decision of the user
        for j = 1:nApps
            % for each decision, check the app that generated the request
            % (in column 1) and keep it if it is a popular app
            if data{n}(d,1) == appIdx(j)
                user_temp = [user_temp; data{n}(d, 1:end)];
            end
        end
    end
    % save the data of the current user
    D{n} = user_temp;
end

function [ X_feat ] = select_features( X, features )
% builds the matrix X only with the selected features
% features is a vector with the indexes of the features to select

nUsers = size(X,3);
nFeatures = size(features,2);
X_feat = NaN*zeros(size(X,1),nFeatures,nUsers);
for n = 1:nUsers
    X_feat(:,:,n)=X(:,features,n);
end

function [ X_dummy ] = dummify( X, selected_features, nValuesFeatures)
% converts the dataset with categorical features to dummy variables

% drop one for each feature, because we need n-1 columns to encode n values
dummy_possible_feat = nValuesFeatures - ones(size(nValuesFeatures));

% get the number of possible values only for the selected features
size_features = dummy_possible_feat(1,selected_features);
% calculate the total number of dummy columns for the features
n_features_dummy = sum(dummy_possible_feat(1,selected_features));
nUsers = size(X,3);

X_dummy = zeros(size(X(:,:,1),1),n_features_dummy,nUsers);
for n = 1:nUsers % for each user
    
    for t = 1:size(X(:,:,n),1)
        if isnan(X(t,1,n))
            X_dummy(t,:,n) = NaN*zeros(size(X_dummy(t,:,n)));
        else
            indexes = zeros(size(size_features));
            for f = 1:size(selected_features,2)
                if f>1
                    indexes(1,f-1)=1;
                end
                ind = X(t,f,n);
                
                
                if ind ~= nValuesFeatures(selected_features(f)) && ind ~= 0
                    % calculate the position of the feature, taking into account the previous features in the matrix
                    pos = sum(indexes*size_features')+ind;
                    
                    if isfinite(pos) % ignore the features that are NaN
                        X_dummy(t,pos,n) = 1;
                    end
                end
            end
        end
    end
    
end

function [ Xtrain, Xtest, Ytrain, Ytest ] = punch_holes(X, Y, nTestPoints, start)
% Borrowed from the following original function:
% [ Xtrain, Xtest, Ytrain, Ytest ] = build_X_Y_train_test(X, Y, nDecPerUser)
% Build train and test sets
% nTestPoints is the number of test points to randomly sample from the data
% start is the percentage of the total data to start sampling the test points

%setSeed(seed); % shouldn't set seed twice
nUsers = size(Y,2);

% initialize variables
Xtest = NaN*zeros(size(X));
Xtrain = X;
Ytest = NaN*zeros(size(Y));
Ytrain = Y;

for n=1:nUsers % for each user
    nDec = sum(isfinite(Y(:,n))); % number of decisions
    % sample the same number of decisions (nTestPoints) for each user for test
    idx_test = sort(datasample(round(start*nDec):nDec, nTestPoints, 'Replace', false));
    % create test-train
    Ytest(idx_test,n) = Y(idx_test,n);
    Xtest(idx_test,:,n)= X(idx_test,:,n);
    Ytrain(idx_test,n) = NaN;
    Xtrain(idx_test,:,n) = NaN;
end

function [ X, Y ] = build_X_Y( data )
% builds the matrix X and Y

nUsers = length(data);
n_decisions = [];
for n = 1:nUsers
    n_decisions = [n_decisions; size(data{n},1)];
end

max_decisions = max(n_decisions);
% Form the matrix of decision outputs
Y = NaN*zeros(nUsers, max_decisions);
for n = 1:nUsers
    yn = data{n}(:,end);
    Y(n,1:length(yn)) = yn(:)';
    nDecisions(n) = length(yn);
end

% Form the matrix with the features
nFeatures = size(data{1},2);
X = NaN*zeros(max_decisions,nFeatures,nUsers);
for n = 1:nUsers
    xn = data{n}(:,1:end-1);
    X(1:size(xn,1), 1:size(xn,2), n) = xn;
end

% make the decisions matrix Decisions x nUsers
Y = Y';


