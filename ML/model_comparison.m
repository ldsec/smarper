function model_comparison(task)
% compare models with different input features
% Written by Emtiyaz Khan and Joana Soares Machado

% parameters of the experiment
nSeeds = 50; % number of seeds
appIdx = [1 3 4 7 9]; % indexes of the apps to be considered (e.g., the most popular apps)

switch task
    case 'run'
        saveOut = 1; % save output
        filename = 'ML/output/model_comparison.mat';
        
        % define different subsets of features for each model
        models{1} = [1]; % select only the first feature
        models{end+1} = [4]; % select only the fourth feature
        models{end+1} = [2]; % select only the second feature
        models{end+1} = [3]; % select only the third feature
        models{end+1} = [1,2,3,4]; % select some features
        models{end+1} = [1:5]; % select all the features
        nValuesFeatures = [10,3,2,6,2]; % number of possible values that each feature can take, e.g. in_foreground can take 2 values (0 or 1)
        
        % fit all models
        for s = 1:nSeeds
            for i = 1:length(models)
                fprintf('Seed %d model %d\n',s, i);
                [nlZ(i,s), mae(i,s), icr(i,s)] = evalFeatures(models{i},s,nValuesFeatures, appIdx);
            end
            [nlZ(:,s), mae(:,s), icr(:,s)]
            
            if saveOut
                save(filename, 'models', 'nlZ', 'mae', 'icr');
            end
        end
        
    case 'plot'
        printOut = 1;
        % load maeVsTime to get results from Static and ZeroR
        load('ML/output/maeVsTime.mat');
        maeZeroR = mae(end,:,1);
        
        % load model comparison and append
        load('ML/output/model_comparison.mat');
        mae = [maeStatic(:)'; maeZeroR(:)'; mae];

        % which models to plot
        idx = [1 2 3 4 5 6]; % only plot these
        idx = [1 2 idx+2]; % because maeZeroR and maeStatic are added
        idx = fliplr(idx); % because of horizontal boxplot
        
        % categorize the models within 4 groups
        y = [0 0 2.5 2.5];
        x = [0 1 1 0];
        h = fill(x,y, [.5 .5 .5]);
        set(h, 'facealpha', 0.3);
        hold on
        y = [2.5 2.5 6.5 6.5];
        x = [0 1 1 0];
        h = fill(x,y, [0 .5 0]);
        set(h, 'facealpha', 0.3);
        y = [6.5 6.5 12.5 12.5];
        x = [0 1 1 0];
        h = fill(x,y, [0 0 .5]);
        set(h, 'facealpha', 0.3);
        y = [12.5 12.5 16.5 16.5];
        x = [0 1 1 0];
        h = fill(x,y, [.5 .0 .0]);
        set(h, 'facealpha', 0.3);
        hold on
        
        % boxplot
        bh = boxplot(mae(idx,:)', 'orientation', 'horizontal', 'notch', 'on');
        set(bh,'linewidth', 2);
        label = {'All Features', 'A+B+C+D', ...
            'Foreground (D)', 'Category (C)', 'Method (B)', 'App-Name (A)',...
            'ZeroR', 'Static-Policy'};
        set(gca,'ytick', [1:18], 'yticklabel', label);
        hx = xlabel('Mean Absolute Error (MAE)');
        ht = title('Which context helps to predict?');
        xlim([0.1 1])
        box on;
        grid on;
        
        set(gca,'FontName','AvantGarde','FontWeight','normal','FontSize',15);
        set(ht,'FontName','AvantGarde','FontSize',15,'FontWeight','bold');
        set([hx],'FontName','AvantGarde','FontSize',15,'FontWeight','normal');
        set(gca, ...
            'Box'         , 'on'     , ...
            'TickDir'     , 'out'     , ...
            'TickLength'  , [.02 .02] , ...
            'XMinorTick'  , 'off'      , ...
            'YMinorTick'  , 'off'      , ...
            'LineWidth'   , 1         );
        
        if printOut
            print -dpdf 'ML/output/model_comparison.pdf';
        end
        
        
    otherwise
        error('no such task');
end

function [nlZ, mae, icr] = evalFeatures(featureIdx,seed,nValuesFeatures,appIdx)

split_ratio = 0.5; % percentage of users in the learning set
nTestPoints = 20; % number of test decisions for users in test set
start_time = 0.4; % where we start sampling test points

setSeed(seed);
[X, y, XTr, yTr, XTe, yTe, maeStatic, icrStatic] = get_data(featureIdx, nValuesFeatures, appIdx, split_ratio, nTestPoints, start_time);
[hyp, nlZ_all] = minimize(0, @fit, -100, 'GP-Lin', y, X, 1, [], []);
nlZ = nlZ_all(end);
yHat = fit(hyp, 'GP-Lin', yTr, XTr, 1, yTe, XTe);
[mae, icr] = loss(yHat, yTe);


