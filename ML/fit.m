function [out1, out2] = fit(hyp, model, y, X, ratio, yTe, XTe)
% Fit a model with some HYPerparameterx on data (y,X)
% and may be also test on (yTe,XTe)
% For a complete list of model and details of their hyperparameters,
% see the code.
%
% There are two modes of working: First test mode
% e.g. yHat = fit([], 'ZeroR', yTr, XTr, ratio, yTe, XTe);
% or   yHat = fit(0, 'GP-Lin', yTr, XTr, ratio, yTe, XTe);
% where yHat is the predicition for yTe (same size as yTe),
% second output argument out2 is not used.
% Ratio is simply how many decisions of (y,X) are used to compute predictions.
%
% The second model is train mode, e.g.
% [nlZ, dnlZ] = fit(hyp, 'GP-Lin', y, X, ratio, [], [])
% where nlZ is the negative log-marginal likelihood
% and dnlZ is its derivative wrt hyp.
% we can use this within a minimizer to optimize hyp:
% hyp = minimize(hyp0, @fit, -100, 'GP-Lin', y, X, ratio, [], []);
% Here, 100 indicates the number of function evaluations,
% ratio is the number of decisions (usually set to 1)
%
% when the last two arguments are empty, we train and return [nlZ and dnlZ]
% else we return predictions yHat
%
% Written by Emtiyaz Khan and Joana Soares Machado

test = 1;
if isempty(yTe) & isempty(XTe)
    test = 0;
end

% initialize
yHat = NaN*zeros(size(yTe));
nlZ = 0;
dnlZ = 0;

% for all users train/test
nUsers = size(y,2);
Xn_all = []; 
Xn_all_no_bias = [];
yn_all = [];

for n = 1:nUsers
    % remove missing values
    idx = find(~isnan(y(:,n)));
    y_temp = y(idx,n);
    x_temp = X(idx,:,n);
    
    % choose data only till time t
    nDec = sum(isfinite(y(:,n))); % number of decisions
    t = floor(ratio*nDec); % i.e. the percentage of total decisions
    Xn_no_bias = x_temp(1:t, :);
    yn = y_temp(1:t);
    N = size(Xn_no_bias,1);
    Xn = [ones(N,1) Xn_no_bias]; % Append the bias term
    
    % concatenate data from al users for the one-size-fits-all models
    Xn_all = [Xn_all; Xn];
    Xn_all_no_bias = [Xn_all_no_bias; Xn_no_bias];
    yn_all = [yn_all; yn];
    
    % format test data
    if test
        idx = find(~isnan(yTe(:,n)));
        ynTe = yTe(idx,n);
        XnTe_no_bias = XTe(idx,:,n);
        N = size(XnTe_no_bias,1);
        XnTe = [ones(N,1) XnTe_no_bias]; % Append the bias term
    end
    
    % models
    switch model
        % Baseline ZeroR
        case 'ZeroR'
            if test; yHat(idx,n) = mode(yn); 
        end
            
        % Bayesian Linear Regression per user (BLR)
        case 'GP-Lin'
            % specify the model
            cov_func = {@covLIN}; hyp_gp.cov = [];
            mean_func = {@meanZero}; hyp_gp.mean = [];
            lik_func = {@likGauss}; hyp_gp.lik = [hyp(1)];
            % test or train
            if test
                [yhat_n] = gp(hyp_gp, 'infExact', mean_func, cov_func, lik_func, Xn, yn, XnTe);
                yHat(idx,n) = round(yhat_n);
            else
                [nlZ_n, dnlZ_n] = gp(hyp_gp, 'infExact', mean_func, cov_func, lik_func, Xn, yn);
                nlZ = nlZ + nlZ_n;
                dnlZ = dnlZ + dnlZ_n.lik;
            end
            
        % Bayesian Linear Regression one-size-fits-all (BLR-all)
        case 'GP-Lin-all'
            % specify the model
            cov_func = {@covLIN}; hyp_gp.cov = [];
            mean_func = {@meanZero}; hyp_gp.mean = [];
            lik_func = {@likGauss}; hyp_gp.lik = [hyp(1)];
            
            % merge the data from all the users
            
            % test or train
            if test
                [yhat_n] = gp(hyp_gp, 'infExact', mean_func, cov_func, lik_func, Xn_all, yn_all, XnTe);
                yHat(idx,n) = round(yhat_n);
            else
                [nlZ_n, dnlZ_n] = gp(hyp_gp, 'infExact', mean_func, cov_func, lik_func, Xn_all, yn_all);
                nlZ = nlZ + nlZ_n;
                dnlZ = dnlZ + dnlZ_n.lik;
            end
            
        % Gaussian Process with Squared Exponential Kernel (GP-SE)
        case 'GP-SEiso'
            % specify the model
            cov_func = {@covSEiso}; hyp_gp.cov = [hyp(1) hyp(2)];
            mean_func = {@meanZero}; hyp_gp.mean = [];
            lik_func = {@likGauss}; hyp_gp.lik = [hyp(3)];
            % test or train
            if isempty(yTe)
                [nlZ_n, dnlZ_n] = gp(hyp_gp, 'infExact', mean_func, cov_func, lik_func, Xn, yn);
                nlZ = nlZ + nlZ_n;
                dnlZ = dnlZ + [dnlZ_n.cov(:); dnlZ_n.lik];
            else
                [yhat_n] = gp(hyp_gp, 'infExact', mean_func, cov_func, lik_func, Xn, yn, XnTe);
                yHat(idx,n) = round(yhat_n);
            end
            
        % Decision Tree
        case 'DTree'
            if test
                Tree = fitctree(Xn_no_bias, yn);
                % predict on the test decisions
                yHat(idx,n) = predict(Tree, XnTe_no_bias);
            end
            
        % 3-binary support vector machines (SVM), with linear kernel
        case 'SVM'
            if test
                % Estimate beta
                t = templateSVM('KernelFunction','linear');
                SVMModel = fitcecoc(Xn_no_bias, yn,'Learners',t);
                % predict on the test decisions
                yHat(idx,n) = predict(SVMModel, XnTe_no_bias);
            end
        
        % One-size-fits-all SVM    
        case 'SVM-all'
            if test
                % Estimate beta
                t = templateSVM('KernelFunction','linear');
                SVMModel = fitcecoc(Xn_all_no_bias, yn_all,'Learners',t);
                % predict on the test decisions
                yHat(idx,n) = predict(SVMModel, XnTe_no_bias);
            end
            
    end %end of model
end % end of for all users

if test
    % return predictions
    out1 = yHat;
    out2 = NaN;
else
    % return nlZ and its derivative
    out1 = nlZ/nUsers;
    out2 = dnlZ/nUsers;
end
