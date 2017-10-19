% experiment to plot MAE and ICR vs Percentage of decisions used
% Written by Emtiyaz Khan and Joana Soares Machado

clear all; close all;
% to run and save output
run = 1; save_out = 1;
% aftering running, plot the output using the save output
plot_out = 1; print_out = 0;

% name of the output file
filename = 'ML/output/maeVsTime.mat';

% run the experiment
if run
    % parameters of the experiment
    nSeeds = 50; % number of seeds
    split_ratio = 0.5; % percentage of users in the learning set
    nTestPoints = 20; % number of test decisions for users in test set
    start_time = 0.4; % where we start sampling test points (percentage of data)
    ratios = [0.1:0.05:1]; % evaluate model after this percentage of data
    featureIdx = 1:5; % indexes of the features to be considered
    nValuesFeatures = [10,3,2,6,2]; % number of possible values that each feature can take, e.g. in_foreground can take 2 values (0 or 1)
    appIdx = [1 3 4 7 9]; % indexes of the apps to be considered (e.g., the most popular apps)

    for s = 1:nSeeds
        fprintf('Seed %d\n',s);
        setSeed(s);
        % get data and static policy baseline
        [X, y, XTr, yTr, XTe, yTe, maeStatic(s), icrStatic(s)] = get_data(featureIdx, nValuesFeatures, appIdx, split_ratio, nTestPoints, start_time);
        
        hyp{2} = []; % hyperparameters for SVM-all
        hyp{3} = []; % hyperparameters for zeroR
        fprintf('Learning hyperparameter for the GP-SE model\n')
        hyp{4} = minimize([0,0,0], @fit, -100, 'GP-SEiso', y, X, 1, [], []); % hyperparameters for GP-SE
        hyp{5} = []; % hyperparameters for DTree
        hyp{6} = []; % hyperparameters for SVM
        % learn hyperparameters of GP-LIN (Bayesian Linear Regression)
        fprintf('Learning hyperparameter for BLR\n')
        hyp{7} = minimize(0, @fit, -100, 'GP-Lin', y, X, 1, [], []);
        
        if s == 1 % do it only once because it takes some time. Estimate it each seed for more accurate results
            fprintf('Learning hyperparameter for BLR-all\n')
            hyp{1} = minimize(0, @fit, -100, 'GP-Lin-all', y, X, 1, [], []); % hyperparameters for BLR-all
        end
        
        % Evaluate models
        models = {'GP-Lin-all','SVM-all','ZeroR','GP-SEiso','DTree','SVM','GP-Lin'};%'GP-Lin','GP-Lin-ARD','GP-SEiso', 'SVM'} 'GP-Lin-general'};%'Lin-Reg-general'};
        for i = 1:length(ratios)
            for m = 1:length(models)
                hyp_m = hyp{m};
                yHat = fit(hyp_m, models{m}, yTr, XTr, ratios(i), yTe, XTe);
                [mae(i,s,m), icr(i,s,m)] = loss(yHat, yTe);
            end
        end
        
        fprintf('MAE obtained by the models:\n')
        models
        [squeeze(mae(:,s,:))]
    end
    
    % save output
    if save_out
        save(filename, 'nSeeds', 'split_ratio', 'nTestPoints', 'start_time', 'ratios', 'featureIdx',...
            'models', 'hyp', 'mae', 'icr', 'maeStatic', 'icrStatic');
    end
end

if plot_out
    % load the saved output
    load(filename);
    
    for m = 1:2 % for the one-size-fits-all models
      
      xData{m} = ratios*100;
      % mae
      in = squeeze(mae(:,:,m));
      out = quantile(in', [0.25 0.50 0.75]);
      yData1{m} = out(2,:);
      errors1{m,1} = out(1,:);
      errors1{m,2} = out(3,:);
      % icr
      in = squeeze(icr(:,:,m));
      out = quantile(in', [0.25 0.50 0.75]);
      yData2{m} = out(2,:);
      errors2{m,1} = out(1,:);
      errors2{m,2} = out(3,:);
    end
    
    % for static policies
    xData{3} = ratios*100;
    out = quantile(maeStatic, [0.25 0.5 0.75]);
    out = repmat(out', [1 length(ratios)]);
    yData1{3} = out(2,:);
    errors1{3,1} = out(1,:);
    errors1{3,2} = out(3,:);
    out = quantile(icrStatic, [0.25 0.5 0.75]);
    out = repmat(out', [1 length(ratios)]);
    yData2{3} = out(2,:);
    errors2{3,1} = out(1,:);
    errors2{3,2} = out(3,:);
    
    for m = 3:length(models)
        xData{m+1} = ratios*100;
        % mae
        in = squeeze(mae(:,:,m));
        out = quantile(in', [0.25 0.50 0.75]);
        yData1{m+1} = out(2,:);
        errors1{m+1,1} = out(1,:);
        errors1{m+1,2} = out(3,:);
        % icr
        in = squeeze(icr(:,:,m));
        out = quantile(in', [0.25 0.50 0.75]);
        yData2{m+1} = out(2,:);
        errors2{m+1,1} = out(1,:);
        errors2{m+1,2} = out(3,:);
    end
    
    options.linewidth = 3;
    options.xlabel = 'Percentage of Decisions Used (Ordered by Time)';
    %options.title = ;
    options.legend = {'BLR-all','SVM-all','Static Policy','ZeroR_t','GP-SE','D. Tree','SVM','BLR'};
    options.legendLoc = 'SouthEast';
    %options.labelLines = 1;
    options.errorStyle = {'--'};
    options.errorColors = [
        0.8039    0.8784    0.9686 % BLR-all
        0.7569    0.8667    0.7765 % SVM-all
        .75 .75 1 % static policies
        .75 1 .75 % ZeroR
        0.8196    0.6118    0.8627 % GP-SE
        0.9529    0.8706    0.7333 % DTree
        0.8000    0.8000    0.8000 % SVM
        1 .75 .75]; % BLR
    options.colors = [0    0.4471    0.7412 % BLR-all
        0.1059    0.3098    0.2078 % SVM-all
        0 0 .5 % static policies
        0 .5 0 % ZeroR
        0.4941    0.1843    0.5569 % GP-SE
        0.8706    0.4902         0 % DTree
        0.3804    0.3804    0.3804 % SVM
        .5 0 0]; % BLR
    options.errorFill = 1;
    options.markers = {'x','+','','s','v','*','d','o'};
    
    figure(1);
    options.errors = errors1;
    options.ylabel = 'Mean Absolute Error (MAE)';
    prettyPlot(xData,yData1,options);
    grid on;
    xlim([0.1-0.01 1+0.01]*100);
    ylim([0 1]);
    set(gca, 'ytick', [0.1:0.1:1]);
    if print_out
        print('-dpdf','ML/output/maeVsTime.pdf');
    end
    
    figure(2);
    options.errors = errors2;
    options.ylabel = 'Incorrect Classification Rate (ICR)';
    prettyPlot(xData,yData2,options);
    grid on;
    xlim([0.1-0.01 1+0.01]*100);
    ylim([0 1]);
    set(gca, 'ytick', [0.1:0.1:1]);
    if print_out
        print('-dpdf','ML/output/icrVsTime.pdf');
    end
end
